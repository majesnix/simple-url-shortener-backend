package com.majesnix.sus

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.Origin
import org.http4s.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

class HttpServerSpec extends AnyFlatSpec with Matchers {

  private implicit val loggerFactory: LoggerFactory[IO] =
    Slf4jFactory.create[IO]

  private val echoApp: HttpApp[IO] =
    HttpApp[IO](req => req.as[String].flatMap(Ok(_)))

  "limitBody" should "reject bodies above the limit with 413" in {
    val app = HttpServer.limitBody(echoApp, 10)
    val resp = app
      .run(Request[IO](Method.POST, uri"/").withEntity("x" * 11))
      .unsafeRunSync()
    resp.status shouldBe Status.PayloadTooLarge
  }

  it should "pass bodies within the limit" in {
    val app = HttpServer.limitBody(echoApp, 10)
    val resp = app
      .run(Request[IO](Method.POST, uri"/").withEntity("x" * 9))
      .unsafeRunSync()
    resp.status shouldBe Status.Ok
  }

  private def allowOrigin(
      origins: Set[String],
      origin: String
  ): Option[String] = {
    val app = HttpServer
      .corsPolicy(origins)
      .apply(HttpApp[IO](_ => Ok()))
      .unsafeRunSync()
    app
      .run(
        Request[IO](Method.GET, uri"/x").putHeaders(
          Header.Raw(ci"Origin", origin)
        )
      )
      .unsafeRunSync()
      .headers
      .get(ci"Access-Control-Allow-Origin")
      .map(_.head.value)
  }

  "corsPolicy" should "allow every origin when none are configured" in {
    allowOrigin(Set.empty, "https://evil.example") shouldBe Some("*")
  }

  it should "only allow configured origins" in {
    val origins = Set("https://dcl.re")
    allowOrigin(origins, "https://dcl.re") shouldBe Some("https://dcl.re")
    allowOrigin(origins, "https://evil.example") shouldBe None
  }

  it should "not confuse Origin.Host rendering" in {
    Origin
      .Host(Uri.Scheme.https, Uri.RegName("dcl.re"), None)
      .renderString shouldBe "https://dcl.re"
  }
}
