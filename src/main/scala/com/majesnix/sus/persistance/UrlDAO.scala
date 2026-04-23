package com.majesnix.sus.persistance

import cats.effect._
import com.majesnix.sus.models.{ShortUrl, UrlDTO}
import skunk._
import skunk.codec.all._
import skunk.implicits._

class UrlDAO(sessions: Resource[IO, Session[IO]]) {

  private val shortUrl = (varchar *: text).values.to[ShortUrl]

  private val insertShortUrlCommand: Command[ShortUrl] =
    sql"INSERT INTO t_url (short, long) VALUES $shortUrl".command

  private val resolveShortUrlCommand: Query[String, UrlDTO] =
    sql"SELECT long FROM t_url WHERE short = $varchar"
      .query(text)
      .to[UrlDTO]

  /** Inserts a short→url mapping. Returns `false` if the short collided with an
    * existing row (UNIQUE violation), `true` on success.
    */
  def insertShortUrl(short: String, url: String): IO[Boolean] =
    sessions.use { s =>
      s.execute(insertShortUrlCommand)(ShortUrl(short = short, url = url)).as(true)
    }.recover { case SqlState.UniqueViolation(_) => false }

  def resolveShortUrl(short: String): IO[Option[UrlDTO]] =
    sessions.use { s =>
      s.prepare(resolveShortUrlCommand).flatMap(_.option(short))
    }
}
