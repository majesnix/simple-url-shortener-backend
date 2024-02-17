package com.majesnix.sus

import cats.data.Kleisli
import cats.effect.IO
import cats.implicits.catsSyntaxApplicativeId
import com.majesnix.sus.NanoId.generateId
import com.majesnix.sus.models.Url.valid
import com.majesnix.sus.models.{CreateShortUrlResponse, Url}
import com.majesnix.sus.persistance.UrlDAO.{createShortUrl, resolveShortUrl}
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe._
import org.http4s.dsl.io._
import io.circe.generic.auto._
import io.circe.syntax._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import scala.language.postfixOps
import scala.util.Random

object UrlRoutes {
  implicit val decoder: EntityDecoder[IO, Url] = jsonOf[IO, Url]

  implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  val urlRoutes: Kleisli[IO, Request[IO], Response[IO]] =
    HttpRoutes
      .of[IO] {
        case req @ POST -> Root =>
          for {
            url <- req.as[Url]
            resp <-
              if (valid(url)) {
                for {
                  short <- generateId(8)
                    .pure[IO]
                  _ <- createShortUrl(short = short, url = url.url)
                  resp <- Ok(CreateShortUrlResponse(short = short).asJson)
                } yield resp
              } else {
                BadRequest("Invalid URL")
              }
          } yield resp
        case GET -> Root / short =>
          for {
            maybeUrl <- resolveShortUrl(short = short)
            response <- maybeUrl match {
              case Some(url) => Ok(url.asJson)
              case _         => NotFound()
            }
          } yield response
      }
      .orNotFound
}
