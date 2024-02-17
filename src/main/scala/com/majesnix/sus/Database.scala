package com.majesnix.sus

import cats.effect._
import com.typesafe.config.{Config, ConfigFactory}
import natchez.Trace.Implicits.noop
import org.flywaydb.core.Flyway
import skunk._

object Database {
  private val config = ConfigFactory.load().getConfig("database")

  val pool: Resource[IO, Resource[IO, Session[IO]]] =
    Session.pooled[IO](
      host = config.getString("host"),
      port = config.getInt("port"),
      database = config.getString("database"),
      user = config.getString("user"),
      password = Some(config.getString("password")),
      max = config.getInt("thread-pool-size")
    )

  def migrate(): IO[Unit] = for {
    _ <- IO {
      Flyway
        .configure()
        .dataSource(
          s"jdbc:postgresql://${config.getString("host")}:${config
            .getString("port")}/${config.getString("database")}",
          config.getString("user"),
          config.getString("password")
        )
        .load()
        .migrate()
    }
  } yield ()

}
