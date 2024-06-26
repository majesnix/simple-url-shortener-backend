ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.12"

enablePlugins(JavaServerAppPackaging)

Docker / packageName := "sus-backend"
Docker / version := "2.0.0"

dockerBuildCommand := {
  if (sys.props("os.arch") != "amd64") {
    // use buildx with platform to build supported amd64 images on other CPU architectures
    // this may require that you have first run 'docker buildx create' to set docker buildx up
    dockerExecCommand.value ++ Seq("buildx", "build", "--platform=linux/amd64", "--load") ++ dockerBuildOptions.value :+ "."
  } else dockerExecCommand.value ++ Seq("buildx", "build", "--platform=linux/arm64", "--load") ++ dockerBuildOptions.value :+ "."
}

lazy val root = (project in file("."))
  .settings(
    name := "url-shortener",
    dockerExposedPorts := Seq(8080),
    dockerUsername := Some("codingbros"),
    dockerBaseImage := "openjdk:17",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Ywarn-unused:imports"
  )

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

lazy val http4sVersion = "1.0.0-M41"
// Needed for flyway migrations
lazy val jdbcPostgresVersion = "42.7.3"
lazy val circeVersion = "0.14.7"
lazy val flywayVersion = "10.15.0"

libraryDependencies ++= Seq(
  "org.http4s"                  %% "http4s-ember-client"        % http4sVersion,
  "org.http4s"                  %% "http4s-ember-server"        % http4sVersion,
  "org.http4s"                  %% "http4s-circe"               % http4sVersion,
  "org.http4s"                  %% "http4s-dsl"                 % http4sVersion,
  "org.typelevel"               %% "cats-effect"                % "3.5.3",
  "io.circe"                    %% "circe-generic"              % circeVersion,
  "io.circe"                    %% "circe-literal"              % circeVersion,
  "org.tpolecat"                %% "skunk-core"                 % "0.6.3",
  "org.postgresql"              % "postgresql"                  % jdbcPostgresVersion,
  "org.flywaydb"                % "flyway-core"                 % flywayVersion,
  "org.flywaydb"                % "flyway-database-postgresql"  % flywayVersion,
  "com.typesafe"                % "config"                      % "1.4.3",
  "com.typesafe.scala-logging"  %% "scala-logging"              % "3.9.5",
  "ch.qos.logback"              % "logback-classic"             % "1.5.6" % Runtime,
  "org.typelevel" %% "log4cats-slf4j"   % "2.7.0",  // Direct Slf4j Support - Recommended
)
