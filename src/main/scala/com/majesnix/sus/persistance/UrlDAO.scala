package com.majesnix.sus.persistance

import cats.effect._
import com.majesnix.sus.Database.pool
import com.majesnix.sus.models.{ShortUrl, Url}
import skunk._
import skunk.codec.all._
import skunk.implicits._

object UrlDAO {

  private val shortUrl = (varchar *: text).values.to[ShortUrl]

  private def insertShortUrlCommand(): Command[ShortUrl] =
    sql"INSERT INTO t_url (short, long) VALUES $shortUrl".command

  def createShortUrl(short: String, url: String): IO[Unit] = {
    pool.use { session =>
      session.use { s =>
        for {
          _ <- s.execute(insertShortUrlCommand())(
            args = ShortUrl(short = short, url = url)
          )
        } yield ()
      }
    }
  }

  private def resolveShortUrlCommand: Query[String, Url] =
    sql"SELECT long FROM t_url WHERE short = $varchar"
      .query(text)
      .to[Url]

  def resolveShortUrl(short: String): IO[Option[Url]] = {
    pool.use { session =>
      session.use { s =>
        for {
          url <- s.prepare(resolveShortUrlCommand).flatMap { ps =>
            ps.option(args = short)
          }
        } yield url
      }
    }
  }
}
