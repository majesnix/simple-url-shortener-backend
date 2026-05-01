package com.majesnix.sus

import cats.effect.IO
import com.majesnix.sus.NanoId.generateId
import com.majesnix.sus.models.CreateUrlRequest
import com.majesnix.sus.models.UrlDTO.valid
import com.majesnix.sus.models.ShortenResponse
import com.majesnix.sus.persistance.UrlRepository
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._

import java.time.OffsetDateTime

object UrlRoutes {
  private val ShortLength      = 8
  private val MaxInsertAttempts = 5

  implicit val decoder: EntityDecoder[IO, CreateUrlRequest] = jsonOf[IO, CreateUrlRequest]

  private def createWithRetry(
      dao: UrlRepository,
      url: String,
      expiresAt: Option[OffsetDateTime],
      attemptsLeft: Int
  ): IO[Option[String]] =
    if (attemptsLeft <= 0) IO.pure(None)
    else
      for {
        short    <- generateId(ShortLength)
        inserted <- dao.insertShortUrl(short, url, expiresAt)
        result   <- if (inserted) IO.pure(Some(short))
                    else createWithRetry(dao, url, expiresAt, attemptsLeft - 1)
      } yield result

  def routes(dao: UrlRepository): HttpApp[IO] =
    HttpRoutes
      .of[IO] {
        case GET -> Root / "health" =>
          Ok("OK")

        case req @ POST -> Root =>
          for {
            CreateUrlRequest(url, expiry) <- req.as[CreateUrlRequest]
            response <-
              if (!valid(url)) BadRequest("Invalid URL")
              else if (!CreateUrlRequest.validExpiry(expiry)) BadRequest("Invalid expiry")
              else {
                val expiresAt = CreateUrlRequest.toExpiresAt(expiry)
                createWithRetry(dao, url, expiresAt, MaxInsertAttempts).flatMap {
                  case Some(short) => Ok(ShortenResponse(short = short).asJson)
                  case None        => InternalServerError("Could not generate a unique short URL")
                }
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
