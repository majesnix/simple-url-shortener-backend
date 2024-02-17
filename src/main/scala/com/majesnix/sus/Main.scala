package com.majesnix.sus

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = for {
    _ <- Database.migrate()
    server <- HttpServer.run
  } yield server
}
