package com.majesnix.sus

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.majesnix.sus.models.UrlDTO
import com.majesnix.sus.persistance.UrlRepository
import io.circe.Json
import org.http4s._
import org.http4s.circe._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.server.middleware.ErrorHandling
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.OffsetDateTime
import scala.collection.mutable

class UrlRoutesSpec extends AnyFlatSpec with Matchers {

  private def inMemoryRepo: UrlRepository = new UrlRepository {
    private val store = mutable.Map.empty[String, (String, Option[OffsetDateTime])]
    def insertShortUrl(short: String, url: String, expiresAt: Option[OffsetDateTime]): IO[Boolean] = IO {
      if (store.contains(short)) false else { store(short) = (url, expiresAt); true }
    }
    def resolveShortUrl(short: String): IO[Option[UrlDTO]] = IO {
      store.get(short).collect {
        case (url, None)            => UrlDTO(url)
        case (url, Some(expiresAt)) if expiresAt.isAfter(OffsetDateTime.now()) => UrlDTO(url)
      }
    }
    def deleteExpiredUrls(): IO[Int] = IO {
      val expired = store.filter { case (_, (_, exp)) => exp.exists(!_.isAfter(OffsetDateTime.now())) }
      expired.keys.foreach(store.remove)
      expired.size
    }
  }

  private def withErrorRecovery(app: HttpApp[IO]): HttpApp[IO] =
    ErrorHandling.Recover.messageFailure(app)

  private def exec(app: HttpApp[IO], req: Request[IO]): Response[IO] =
    withErrorRecovery(app).run(req).unsafeRunSync()

  private def asJson(resp: Response[IO]): Json =
    resp.as[Json].unsafeRunSync()

  private def isClientError(resp: Response[IO]): Boolean =
    resp.status.code / 100 == 4

  private def jsonPost(app: HttpApp[IO], json: Json): Response[IO] =
    exec(app, Request[IO](Method.POST, uri"/").withEntity(json))

  private def rawPost(app: HttpApp[IO], raw: String): Response[IO] =
    exec(
      app,
      Request[IO](Method.POST, uri"/")
        .withEntity(raw)
        .putHeaders(`Content-Type`(MediaType.application.json))
    )

  "GET /health" should "return 200 OK" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = exec(app, Request(Method.GET, uri"/health"))
    resp.status shouldBe Status.Ok
    resp.as[String].unsafeRunSync() shouldBe "OK"
  }

  "POST /" should "create a short URL for a valid URL and return its key" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = jsonPost(app, Json.obj("url" -> Json.fromString("https://example.com/path")))
    resp.status shouldBe Status.Ok
    asJson(resp).hcursor.get[String]("short").isRight shouldBe true
  }

  it should "create a short URL with a 1d expiry" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = jsonPost(app, Json.obj("url" -> Json.fromString("https://example.com"), "expiry" -> Json.fromString("1d")))
    resp.status shouldBe Status.Ok
  }

  it should "create a short URL with a 1w expiry" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = jsonPost(app, Json.obj("url" -> Json.fromString("https://example.com"), "expiry" -> Json.fromString("1w")))
    resp.status shouldBe Status.Ok
  }

  it should "create a short URL with a 1m expiry" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = jsonPost(app, Json.obj("url" -> Json.fromString("https://example.com"), "expiry" -> Json.fromString("1m")))
    resp.status shouldBe Status.Ok
  }

  it should "create a short URL with a 1y expiry" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = jsonPost(app, Json.obj("url" -> Json.fromString("https://example.com"), "expiry" -> Json.fromString("1y")))
    resp.status shouldBe Status.Ok
  }

  it should "create a short URL with an explicit unlimited expiry" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = jsonPost(app, Json.obj("url" -> Json.fromString("https://example.com"), "expiry" -> Json.fromString("unlimited")))
    resp.status shouldBe Status.Ok
  }

  it should "return 400 for an invalid expiry value" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = jsonPost(app, Json.obj("url" -> Json.fromString("https://example.com"), "expiry" -> Json.fromString("2d")))
    resp.status shouldBe Status.BadRequest
  }

  it should "return 400 for a URL pointing at the server host" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = jsonPost(app, Json.obj("url" -> Json.fromString("https://localhost/anything")))
    resp.status shouldBe Status.BadRequest
  }

  it should "return 400 for a disallowed scheme" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = jsonPost(app, Json.obj("url" -> Json.fromString("javascript:alert(1)")))
    resp.status shouldBe Status.BadRequest
  }

  it should "return 4xx for malformed JSON" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = rawPost(app, "not json at all")
    isClientError(resp) shouldBe true
  }

  it should "return 4xx when the url field is absent" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = jsonPost(app, Json.obj("other" -> Json.fromString("value")))
    isClientError(resp) shouldBe true
  }

  "GET /:short" should "return the original URL for a known short" in {
    val repo = inMemoryRepo
    val app  = UrlRoutes.routes(repo)

    val createResp = jsonPost(app, Json.obj("url" -> Json.fromString("https://example.com")))
    val short      = asJson(createResp).hcursor.get[String]("short").toOption.get

    val getResp = exec(app, Request(Method.GET, Uri.unsafeFromString(s"/$short")))
    getResp.status shouldBe Status.Ok
    asJson(getResp).hcursor.get[String]("url").toOption shouldBe Some("https://example.com")
  }

  it should "return 404 for an unknown short" in {
    val app  = UrlRoutes.routes(inMemoryRepo)
    val resp = exec(app, Request(Method.GET, uri"/doesnotexist"))
    resp.status shouldBe Status.NotFound
  }
}
