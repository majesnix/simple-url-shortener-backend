package com.majesnix.sus

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    Database.migrate() >>
      Database.pool.use(HttpServer.run).as(ExitCode.Success)
}
