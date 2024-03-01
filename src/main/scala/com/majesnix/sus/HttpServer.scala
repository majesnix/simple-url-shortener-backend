package com.majesnix.sus

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s._
import org.http4s.Method
import org.http4s.ember.server._
import org.http4s.server.middleware.{CORS, ErrorAction, ErrorHandling}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.concurrent.duration.DurationInt

object HttpServer {
  implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  private val withCors = CORS.policy
    .withAllowOriginAll
    .withAllowMethodsIn(Set(Method.GET, Method.POST))
    .withAllowCredentials(false)
    .withMaxAge(1.day)
    .apply(UrlRoutes.urlRoutes).unsafeRunSync()
  private val withErrorLogging = ErrorHandling.Recover.total(
    ErrorAction.log(
      withCors,
      messageFailureLogAction = (t, msg) =>
        IO.println(msg) >>
          IO.println(t),
      serviceErrorLogAction = (t, msg) =>
        IO.println(msg) >>
          IO.println(t)
    )
  )

  def run: IO[Nothing] = {
    for {
      _ <-
        EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(withErrorLogging).build
    } yield ()
  }.useForever
}
