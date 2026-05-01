package com.majesnix.sus.persistance

import cats.effect._
import com.majesnix.sus.models.UrlDTO
import skunk._
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._

import java.time.OffsetDateTime

trait UrlRepository {
  def insertShortUrl(short: String, url: String, expiresAt: Option[OffsetDateTime], oneTime: Boolean): IO[Boolean]
  def resolveShortUrl(short: String): IO[Option[UrlDTO]]
  def deleteExpiredUrls(): IO[Int]
  def listAllUrls(): IO[List[(String, String)]]
  def deleteByShort(short: String): IO[Unit]
}

class UrlDAO(sessions: Resource[IO, Session[IO]]) extends UrlRepository {

  private case class UrlRecord(short: String, url: String, expiresAt: Option[OffsetDateTime], oneTime: Boolean)
  private val urlRecord = (varchar *: text *: timestamptz.opt *: bool).values.to[UrlRecord]

  private case class ShortAndLong(short: String, long: String)

  private val insertShortUrlCommand: Command[UrlRecord] =
    sql"INSERT INTO t_url (short, long, expires_at, one_time) VALUES $urlRecord".command

  private val claimOneTimeQuery: Query[String, UrlDTO] =
    sql"DELETE FROM t_url WHERE short = $varchar AND one_time = TRUE RETURNING long"
      .query(text)
      .to[UrlDTO]

  private val resolveRegularQuery: Query[String, UrlDTO] =
    sql"SELECT long FROM t_url WHERE short = $varchar AND one_time = FALSE AND (expires_at IS NULL OR expires_at > NOW())"
      .query(text)
      .to[UrlDTO]

  private val deleteExpiredCommand: Command[Void] =
    sql"DELETE FROM t_url WHERE expires_at IS NOT NULL AND expires_at <= NOW()".command

  private val listAllUrlsQuery: Query[Void, ShortAndLong] =
    sql"SELECT short, long FROM t_url WHERE (expires_at IS NULL OR expires_at > NOW())"
      .query(varchar *: text)
      .to[ShortAndLong]

  private val deleteByShortCommand: Command[String] =
    sql"DELETE FROM t_url WHERE short = $varchar".command

  def insertShortUrl(short: String, url: String, expiresAt: Option[OffsetDateTime], oneTime: Boolean): IO[Boolean] =
    sessions.use { s =>
      s.execute(insertShortUrlCommand)(UrlRecord(short, url, expiresAt, oneTime)).as(true)
    }.recover { case SqlState.UniqueViolation(_) => false }

  def resolveShortUrl(short: String): IO[Option[UrlDTO]] =
    sessions.use { s =>
      s.prepare(claimOneTimeQuery).flatMap(_.option(short)).flatMap {
        case some @ Some(_) => IO.pure(some)
        case None           => s.prepare(resolveRegularQuery).flatMap(_.option(short))
      }
    }

  def deleteExpiredUrls(): IO[Int] =
    sessions.use { s =>
      s.execute(deleteExpiredCommand).map {
        case Completion.Delete(n) => n
        case _                   => 0
      }
    }

  def listAllUrls(): IO[List[(String, String)]] =
    sessions.use { s =>
      s.execute(listAllUrlsQuery).map(_.map(r => (r.short, r.long)))
    }

  def deleteByShort(short: String): IO[Unit] =
    sessions.use { s =>
      s.execute(deleteByShortCommand)(short).void
    }
}
