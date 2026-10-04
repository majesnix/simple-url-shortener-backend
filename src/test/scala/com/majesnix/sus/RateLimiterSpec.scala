package com.majesnix.sus

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.headers.`Retry-After`
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci._

import scala.concurrent.duration._

class RateLimiterSpec extends AnyFlatSpec with Matchers {

  private val okApp: HttpApp[IO] = HttpApp[IO](_ => Ok("ok"))

  private def limited(max: Int, window: FiniteDuration, hops: Int) =
    RateLimiter.create(max, window, hops).unsafeRunSync().apply(okApp)

  private def post(from: String, forwardedFor: Option[String] = None) = {
    val req = Request[IO](Method.POST, uri"/")
      .withAttribute(
        Request.Keys.ConnectionInfo,
        Request.Connection(
          local = SocketAddress(ipv4"127.0.0.1", port"8080"),
          remote = SocketAddress(IpAddress.fromString(from).get, port"50000"),
          secure = false
        )
      )
    forwardedFor.fold(req)(v =>
      req.putHeaders(Header.Raw(ci"X-Forwarded-For", v))
    )
  }

  private def statuses(app: HttpApp[IO], reqs: Seq[Request[IO]]): Seq[Status] =
    reqs.map(r => app.run(r).unsafeRunSync().status)

  "RateLimiter" should "reject POSTs above the limit with 429 and Retry-After" in {
    val app = limited(2, 1.minute, 0)
    val results = statuses(app, Seq.fill(3)(post("1.2.3.4")))
    results shouldBe Seq(Status.Ok, Status.Ok, Status.TooManyRequests)
    app
      .run(post("1.2.3.4"))
      .unsafeRunSync()
      .headers
      .get[`Retry-After`] shouldBe defined
  }

  it should "count clients separately" in {
    val app = limited(1, 1.minute, 0)
    statuses(app, Seq(post("1.2.3.4"), post("5.6.7.8"))) shouldBe Seq(
      Status.Ok,
      Status.Ok
    )
  }

  it should "never limit GET requests" in {
    val app = limited(1, 1.minute, 0)
    val get = post("1.2.3.4").withMethod(Method.GET)
    statuses(app, Seq.fill(5)(get)).distinct shouldBe Seq(Status.Ok)
  }

  it should "allow requests again once the window has passed" in {
    val app = limited(1, 50.millis, 0)
    app.run(post("1.2.3.4")).unsafeRunSync().status shouldBe Status.Ok
    app
      .run(post("1.2.3.4"))
      .unsafeRunSync()
      .status shouldBe Status.TooManyRequests
    Thread.sleep(100)
    app.run(post("1.2.3.4")).unsafeRunSync().status shouldBe Status.Ok
  }

  it should "be disabled when max-requests is 0" in {
    val app = limited(0, 1.minute, 0)
    statuses(app, Seq.fill(5)(post("1.2.3.4"))).distinct shouldBe Seq(Status.Ok)
  }

  "clientKey" should "ignore X-Forwarded-For when no proxy is trusted" in {
    val limiter = RateLimiter.create(1, 1.minute, 0).unsafeRunSync()
    limiter.clientKey(post("10.0.0.1", Some("9.9.9.9"))) shouldBe "10.0.0.1"
  }

  it should "use the entry appended by the trusted proxy, not spoofable ones" in {
    val limiter = RateLimiter.create(1, 1.minute, 1).unsafeRunSync()
    limiter.clientKey(
      post("10.0.0.1", Some("6.6.6.6, 1.2.3.4"))
    ) shouldBe "1.2.3.4"
  }

  it should "fall back to the peer address when the header is missing" in {
    val limiter = RateLimiter.create(1, 1.minute, 1).unsafeRunSync()
    limiter.clientKey(post("10.0.0.1")) shouldBe "10.0.0.1"
  }
}
