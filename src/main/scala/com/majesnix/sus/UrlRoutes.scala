package com.majesnix.sus

import cats.effect.IO
import com.majesnix.sus.NanoId.generateId
import com.majesnix.sus.models.UrlDTO.valid
import com.majesnix.sus.models.{ShortenResponse, UrlDTO}
import com.majesnix.sus.persistance.UrlRepository
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._

object UrlRoutes {
  private val ShortLength = 8
  private val MaxInsertAttempts = 5

  implicit val decoder: EntityDecoder[IO, UrlDTO] = jsonOf[IO, UrlDTO]

  private def createWithRetry(dao: UrlRepository, url: String, attemptsLeft: Int): IO[Option[String]] =
    if (attemptsLeft <= 0) IO.pure(None)
    else
      for {
        short    <- generateId(ShortLength)
        inserted <- dao.insertShortUrl(short, url)
        result   <- if (inserted) IO.pure(Some(short))
                    else createWithRetry(dao, url, attemptsLeft - 1)
      } yield result

  def routes(dao: UrlRepository): HttpApp[IO] =
    HttpRoutes
      .of[IO] {
        case GET -> Root / "health" =>
          Ok("OK")

        case req @ POST -> Root =>
          for {
            UrlDTO(url) <- req.as[UrlDTO]
            response <-
              if (!valid(url)) BadRequest("Invalid URL")
              else
                createWithRetry(dao, url, MaxInsertAttempts).flatMap {
                  case Some(short) => Ok(ShortenResponse(short = short).asJson)
                  case None        => InternalServerError("Could not generate a unique short URL")
                }
          } yield response

        case GET -> Root / short =>
          dao.resolveShortUrl(short).flatMap {
            case Some(url) => Ok(url.asJson)
            case None      => NotFound()
          }
      }
      .orNotFound
}
