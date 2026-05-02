import scala.sys.process.Process

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.18"

enablePlugins(JavaServerAppPackaging)

Docker / packageName := "sus-backend"
Docker / version := "2.1.2"
dockerUpdateLatest := true
dockerBuildxPlatforms := Seq("linux/arm64/v8", "linux/amd64")

lazy val root = (project in file("."))
  .settings(
    name := "url-shortener",
    dockerExposedPorts := Seq(8080),
    dockerUsername := Some("codingbros"),
    dockerBaseImage := "eclipse-temurin:25",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Ywarn-unused:imports"
  )

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

lazy val http4sVersion = "1.0.0-M46"
// Needed for flyway migrations
lazy val jdbcPostgresVersion = "42.7.10"
lazy val circeVersion = "0.14.15"
lazy val flywayVersion = "11.20.3"

libraryDependencies ++= Seq(
  "org.http4s"                  %% "http4s-ember-client"        % http4sVersion,
  "org.http4s"                  %% "http4s-ember-server"        % http4sVersion,
  "org.http4s"                  %% "http4s-circe"               % http4sVersion,
  "org.http4s"                  %% "http4s-dsl"                 % http4sVersion,
  "org.typelevel"               %% "cats-effect"                % "3.7.0",
  "io.circe"                    %% "circe-generic"              % circeVersion,
  "io.circe"                    %% "circe-literal"              % circeVersion,
  "org.tpolecat"                %% "skunk-core"                 % "1.0.0",
  "org.postgresql"              % "postgresql"                  % jdbcPostgresVersion,
  "org.flywaydb"                % "flyway-core"                 % flywayVersion,
  "org.flywaydb"                % "flyway-database-postgresql"  % flywayVersion,
  "com.typesafe"                % "config"                      % "1.4.6",
  "com.typesafe.scala-logging"  %% "scala-logging"              % "3.9.6",
  "ch.qos.logback"              % "logback-classic"             % "1.5.32" % Runtime,
  "org.typelevel"               %% "log4cats-slf4j"             % "2.8.0",  // Direct Slf4j Support - Recommended
  "org.scalatest"               %% "scalatest"                  % "3.2.20"  % Test,
)

lazy val it = (project in file("it"))
  .dependsOn(root)
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.20",
      "org.scala-lang" %% "toolkit" % "0.9.2"
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