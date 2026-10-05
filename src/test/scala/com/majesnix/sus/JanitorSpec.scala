package com.majesnix.sus

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.majesnix.sus.models.UrlDTO
import com.majesnix.sus.persistance.UrlRepository
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.OffsetDateTime
import scala.collection.mutable

class JanitorSpec extends AnyFlatSpec with Matchers {

  private def makeRepo(initial: Map[String, String]): UrlRepository =
    new UrlRepository {
      private val store = mutable.Map.from(
        initial.view.mapValues(url =>
          (url, Option.empty[OffsetDateTime], false)
        )
      )

      def insertShortUrl(
          short: String,
          url: String,
          expiresAt: Option[OffsetDateTime],
          oneTime: Boolean
      ): IO[Boolean] = IO {
        if (store.contains(short)) false
        else { store(short) = (url, expiresAt, oneTime); true }
      }
      def resolveShortUrl(
          short: String,
          claimOneTime: Boolean
      ): IO[Option[UrlDTO]] = IO {
        store.get(short).map { case (url, _, _) => UrlDTO(url) }
      }
      def deleteExpiredUrls(): IO[Int] = IO.pure(0)
      def listAllUrls(): IO[List[(String, String)]] = IO {
        store.toList.map { case (short, (url, _, _)) => (short, url) }
      }
      def deleteByShort(short: String): IO[Unit] = IO {
        store.remove(short); ()
      }
    }

  "removeInvalidUrls" should "remove URLs with disallowed schemes" in {
    val repo = makeRepo(
      Map("a" -> "https://example.com", "b" -> "ftp://files.example.com")
    )
    Janitor.removeInvalidUrls(repo).unsafeRunSync()
    repo.listAllUrls().unsafeRunSync().map(_._2).toSet shouldBe Set(
      "https://example.com"
    )
  }

  it should "remove URLs pointing at private IPs" in {
    val repo = makeRepo(
      Map("a" -> "https://example.com", "b" -> "http://192.168.1.1/admin")
    )
    Janitor.removeInvalidUrls(repo).unsafeRunSync()
    repo.listAllUrls().unsafeRunSync().map(_._2).toSet shouldBe Set(
      "https://example.com"
    )
  }

  it should "remove URLs with dangerous schemes" in {
    val repo =
      makeRepo(Map("a" -> "https://example.com", "b" -> "javascript:alert(1)"))
    Janitor.removeInvalidUrls(repo).unsafeRunSync()
    repo.listAllUrls().unsafeRunSync().map(_._2).toSet shouldBe Set(
      "https://example.com"
    )
  }

  it should "remove URLs pointing at loopback addresses" in {
    val repo = makeRepo(
      Map("a" -> "https://example.com", "b" -> "http://127.0.0.1:8080/path")
    )
    Janitor.removeInvalidUrls(repo).unsafeRunSync()
    repo.listAllUrls().unsafeRunSync().map(_._2).toSet shouldBe Set(
      "https://example.com"
    )
  }

  it should "not remove valid URLs" in {
    val repo = makeRepo(
      Map("a" -> "https://example.com", "b" -> "http://another.org/path?q=1")
    )
    Janitor.removeInvalidUrls(repo).unsafeRunSync()
    repo.listAllUrls().unsafeRunSync().map(_._2).toSet shouldBe Set(
      "https://example.com",
      "http://another.org/path?q=1"
    )
  }

  it should "keep URLs that only fail newer, stricter validation rules (e.g. the length cap)" in {
    val longUrl = "https://example.com/" + "a" * 3000
    val repo = makeRepo(Map("a" -> longUrl))
    Janitor.removeInvalidUrls(repo).unsafeRunSync()
    repo.listAllUrls().unsafeRunSync().map(_._2) shouldBe List(longUrl)
  }

  it should "handle an empty repository without error" in {
    val repo = makeRepo(Map.empty)
    noException should be thrownBy Janitor
      .removeInvalidUrls(repo)
      .unsafeRunSync()
  }
}
