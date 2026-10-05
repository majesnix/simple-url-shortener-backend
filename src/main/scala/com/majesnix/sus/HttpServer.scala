package com.majesnix.sus

import cats.effect.{IO, Resource}
import com.comcast.ip4s._
import com.majesnix.sus.persistance.UrlDAO
import com.typesafe.config.ConfigFactory
import org.http4s.{HttpApp, Method, Response, Status}
import org.http4s.ember.server._
import org.http4s.headers.Origin
import org.http4s.server.middleware.{
  CORS,
  EntityLimiter,
  ErrorAction,
  ErrorHandling
}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import skunk.Session

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.DurationConverters._

object HttpServer {
  private implicit val loggerFactory: LoggerFactory[IO] =
    Slf4jFactory.create[IO]
  private val logger = loggerFactory.getLogger

  private def withErrorLogging(app: HttpApp[IO]): HttpApp[IO] =
    ErrorHandling.Recover.total(
      ErrorAction.log(
        app,
        messageFailureLogAction = (t, msg) => logger.warn(t)(msg),
        serviceErrorLogAction = (t, msg) => logger.error(t)(msg)
      )
    )

  private[sus] def limitBody(app: HttpApp[IO], maxBytes: Long): HttpApp[IO] =
    EntityLimiter(app, maxBytes).mapF(_.recover {
      case EntityLimiter.EntityTooLarge(_) =>
        Response[IO](Status.PayloadTooLarge).withEntity(
          "Request body too large"
        )
    })

  private[sus] def corsPolicy(origins: Set[String]) = {
    val base = CORS.policy
      .withAllowMethodsIn(Set(Method.GET, Method.POST))
      .withAllowCredentials(false)
      .withMaxAge(1.day)
    if (origins.isEmpty) base.withAllowOriginAll
    else
      base.withAllowOriginHost((host: Origin.Host) =>
        origins(host.renderString)
      )
  }

  def run(sessions: Resource[IO, Session[IO]]): IO[Nothing] = {
    val dao = new UrlDAO(sessions)
    val rootConfig = ConfigFactory.load()
    val config = rootConfig.getConfig("server")
    val rateConfig = rootConfig.getConfig("rate-limit")
    val corsOrigins = config
      .getString("cors-origins")
      .split(',')
      .map(_.trim.stripSuffix("/"))
      .filter(_.nonEmpty)
      .toSet

    for {
      host <- IO.fromOption(Host.fromString(config.getString("host")))(
        new IllegalArgumentException(
          s"Invalid server.host: ${config.getString("host")}"
        )
      )
      port <- IO.fromOption(Port.fromInt(config.getInt("port")))(
        new IllegalArgumentException(
          s"Invalid server.port: ${config.getInt("port")}"
        )
      )
      rateLimiter <- RateLimiter.create(
        rateConfig.getInt("max-requests"),
        rateConfig.getDuration("window").toScala: FiniteDuration,
        rateConfig.getInt("forwarded-for-hops")
      )
      corsApp <- corsPolicy(corsOrigins).apply(
        rateLimiter(
          limitBody(UrlRoutes.routes(dao), config.getLong("max-body-bytes"))
        )
      )
      nothing <- Janitor.run(dao).background.use { _ =>
        EmberServerBuilder
          .default[IO]
          .withHost(host)
          .withPort(port)
          .withHttpApp(withErrorLogging(corsApp))
          .build
          .useForever
      }
    } yield nothing
  }
}
