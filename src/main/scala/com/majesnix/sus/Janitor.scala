package com.majesnix.sus

import cats.effect.IO
import com.majesnix.sus.models.UrlDTO
import com.majesnix.sus.persistance.UrlRepository
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.time.{LocalDate, ZoneOffset}
import scala.concurrent.duration._

object Janitor {
  private implicit val loggerFactory: LoggerFactory[IO] =
    Slf4jFactory.create[IO]
  private val logger = loggerFactory.getLogger

  private def timeUntilMidnightUTC: FiniteDuration = {
    val now = java.time.Instant.now()
    val midnight = LocalDate
      .now(ZoneOffset.UTC)
      .plusDays(1)
      .atStartOfDay(ZoneOffset.UTC)
      .toInstant
    java.time.Duration.between(now, midnight).toMillis.milliseconds
  }

  private[sus] def removeInvalidUrls(dao: UrlRepository): IO[Unit] =
    dao
      .listAllUrls()
      .flatMap { urls =>
        val invalid = urls.filter { case (_, long) => UrlDTO.dangerous(long) }
        invalid
          .foldLeft(IO.unit) { (acc, entry) =>
            acc.flatMap(_ => dao.deleteByShort(entry._1))
          }
          .flatMap(_ =>
            logger.info(s"Janitor removed ${invalid.size} invalid URL(s)")
          )
      }
      .handleErrorWith(e => logger.warn(e)("Janitor invalid-URL sweep failed"))

  def run(dao: UrlRepository): IO[Nothing] = {
    val step: IO[Unit] =
      IO(timeUntilMidnightUTC)
        .flatMap(IO.sleep)
        .flatMap { _ =>
          dao
            .deleteExpiredUrls()
            .flatMap(n => logger.info(s"Janitor deleted $n expired URL(s)"))
            .handleErrorWith(e =>
              logger.warn(e)("Janitor expired-URL sweep failed")
            )
            .flatMap(_ => removeInvalidUrls(dao))
        }
    step.foreverM
  }
}
