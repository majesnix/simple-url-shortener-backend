package com.majesnix.sus

import cats.effect.{IO, Resource}
import com.comcast.ip4s._
import com.majesnix.sus.persistance.UrlDAO
import com.typesafe.config.ConfigFactory
import org.http4s.{HttpApp, Method}
import org.http4s.ember.server._
import org.http4s.server.middleware.{CORS, ErrorAction, ErrorHandling}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import skunk.Session

import scala.concurrent.duration.DurationInt

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

  def run(sessions: Resource[IO, Session[IO]]): IO[Nothing] = {
    val dao = new UrlDAO(sessions)
    val config = ConfigFactory.load().getConfig("server")

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
      corsApp <- CORS.policy.withAllowOriginAll
        .withAllowMethodsIn(Set(Method.GET, Method.POST))
        .withAllowCredentials(false)
        .withMaxAge(1.day)
        .apply(UrlRoutes.routes(dao))
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
