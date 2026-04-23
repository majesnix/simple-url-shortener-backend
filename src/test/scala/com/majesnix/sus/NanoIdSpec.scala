package com.majesnix.sus

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NanoIdSpec extends AnyFlatSpec with Matchers {

  private val alphabet =
    "_~0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toSet

  "generateId" should "produce an id of the requested length" in {
    val id = NanoId.generateId(8).unsafeRunSync()
    id.length shouldBe 8
  }

  it should "produce an id using only the default alphabet" in {
    val id = NanoId.generateId(32).unsafeRunSync()
    id.forall(alphabet.contains) shouldBe true
  }

  it should "produce distinct ids across many invocations" in {
    val ids = (1 to 1000).map(_ => NanoId.generateId(8).unsafeRunSync()).toSet
    ids.size shouldBe 1000
  }

  it should "honour the default size when called with no arguments" in {
    val id = NanoId.generateId.unsafeRunSync()
    id.length shouldBe 21
  }
}
