ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.12"

enablePlugins(JavaServerAppPackaging)

packageName in Docker := "sus-backend"
version in Docker := "2.0.0"
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
    dockerUsername := Some("codingbros")
  )

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

val http4sVersion = "1.0.0-M40"
// Needed for flyway migrations
lazy val jdbcPostgresVersion = "42.7.1"
lazy val circeVersion = "0.14.6"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.typelevel" %% "cats-effect" % "3.5.3",
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-literal" % circeVersion,
  "org.tpolecat" %% "skunk-core" % "0.6.2",
  "org.postgresql" % "postgresql" % jdbcPostgresVersion,
  "org.flywaydb" % "flyway-core" % "9.22.3",
  "com.typesafe" % "config" % "1.4.3",
)
