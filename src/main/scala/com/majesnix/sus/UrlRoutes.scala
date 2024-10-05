package com.majesnix.sus

import cats.data.Kleisli
import cats.effect.IO
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxOptionId}
import com.majesnix.sus.NanoId.generateId
import com.majesnix.sus.models.UrlDTO.valid
import com.majesnix.sus.models.{ShortenResponse, UrlDTO}
import com.majesnix.sus.persistance.UrlDAO.{createShortUrl, deleteShortUrl, resolveShortUrl}
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe._
import org.http4s.dsl.io._
import io.circe.generic.auto._
import io.circe.syntax._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

case class User(id: Long, name: String)

object UrlRoutes {
  implicit val decoder: EntityDecoder[IO, UrlDTO] =
    jsonOf[IO, UrlDTO]

  implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  val urlRoutes: Kleisli[IO, Request[IO], Response[IO]] =
    HttpRoutes
      .of[IO] {
        case req @ POST -> Root =>
          for {
            UrlDTO(url) <- req.as[UrlDTO]
            response <-
              if (valid(url)) {
                for {
                  short <- IO { generateId(8) }
                  _ <- createShortUrl(short = short, url = url)
                  response <- Ok(ShortenResponse(short = short).asJson)
                } yield response
              } else {
                BadRequest("Invalid URL")
              }
          } yield response
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

  private val authedRoutes: AuthedRoutes[User, IO] =
    AuthedRoutes.of { case DELETE -> Root / short as user =>
      for {
        _ <- deleteShortUrl(short = short)
        response <- Ok("Deleted")
      } yield response
    }
}
