import scala.sys.process.Process

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

enablePlugins(JavaServerAppPackaging)

Docker / packageName := "sus-backend"
Docker / version := "2.0.1"
dockerUpdateLatest := true
dockerBuildxPlatforms := Seq("linux/arm64/v8", "linux/amd64")

lazy val root = (project in file("."))
  .settings(
    name := "url-shortener",
    dockerExposedPorts := Seq(8080),
    dockerUsername := Some("codingbros"),
    dockerBaseImage := "openjdk:22",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Ywarn-unused:imports"
  )

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

lazy val http4sVersion = "1.0.0-M44"
// Needed for flyway migrations
lazy val jdbcPostgresVersion = "42.7.5"
lazy val circeVersion = "0.14.12"
lazy val flywayVersion = "11.6.0"

libraryDependencies ++= Seq(
  "org.http4s"                  %% "http4s-ember-client"        % http4sVersion,
  "org.http4s"                  %% "http4s-ember-server"        % http4sVersion,
  "org.http4s"                  %% "http4s-circe"               % http4sVersion,
  "org.http4s"                  %% "http4s-dsl"                 % http4sVersion,
  "org.typelevel"               %% "cats-effect"                % "3.7-4972921",
  "io.circe"                    %% "circe-generic"              % circeVersion,
  "io.circe"                    %% "circe-literal"              % circeVersion,
  "org.tpolecat"                %% "skunk-core"                 % "0.6.4",
  "org.postgresql"              % "postgresql"                  % jdbcPostgresVersion,
  "org.flywaydb"                % "flyway-core"                 % flywayVersion,
  "org.flywaydb"                % "flyway-database-postgresql"  % flywayVersion,
  "com.typesafe"                % "config"                      % "1.4.3",
  "com.typesafe.scala-logging"  %% "scala-logging"              % "3.9.5",
  "ch.qos.logback"              % "logback-classic"             % "1.5.18" % Runtime,
  "org.typelevel"               %% "log4cats-slf4j"             % "2.7.0",  // Direct Slf4j Support - Recommended
)

lazy val it = (project in file("it"))
  .dependsOn(root)
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19",
      "org.scala-lang" %% "toolkit" % "0.6.0"
    )
  )

lazy val deployContainers = taskKey[Unit]("Deploy containers")
deployContainers := {
  Process(s"docker compose up -d").!
  println(s"Waiting for containers to be up and running (5 sec)")
  Thread.sleep(5000)
}

lazy val stopContainers = taskKey[Unit]("Stop containers")
stopContainers := Process(s"docker compose down").!

lazy val runItTest = taskKey[Unit]("Build image, deploy containers, run it tests and stop afterwards")
runItTest := (stopContainers dependsOn it/Test/test dependsOn deployContainers dependsOn Docker/publishLocal).value