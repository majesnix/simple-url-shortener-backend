package com.majesnix.sus.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UrlDTOSpec extends AnyFlatSpec with Matchers {

  "valid" should "accept http and https URLs" in {
    UrlDTO.valid("http://example.com") shouldBe true
    UrlDTO.valid("https://example.com/path?q=1") shouldBe true
  }

  it should "reject ftp URLs" in {
    UrlDTO.valid("ftp://files.example.com/readme") shouldBe false
  }

  it should "reject URLs without a host" in {
    UrlDTO.valid("http:///nohost") shouldBe false
    UrlDTO.valid("https://") shouldBe false
  }

  it should "reject disallowed schemes" in {
    UrlDTO.valid("javascript:alert(1)") shouldBe false
    UrlDTO.valid("file:///etc/passwd") shouldBe false
    UrlDTO.valid("data:text/html,<h1>x</h1>") shouldBe false
  }

  it should "reject malformed or empty input" in {
    UrlDTO.valid("") shouldBe false
    UrlDTO.valid("not a url") shouldBe false
  }

  it should "reject URLs pointing at our own server host (case-insensitive)" in {
    UrlDTO.valid("https://localhost:8080/abc") shouldBe false
    UrlDTO.valid("http://LOCALHOST") shouldBe false
  }

  it should "not reject URLs that merely contain the server host as a substring" in {
    UrlDTO.valid("https://example.com/?ref=localhost") shouldBe true
    UrlDTO.valid("https://mylocalhostmirror.com") shouldBe true
  }

  it should "reject loopback and private IP addresses" in {
    UrlDTO.valid("http://127.0.0.1") shouldBe false
    UrlDTO.valid("http://127.0.0.1:8080/path") shouldBe false
    UrlDTO.valid("http://10.0.0.1") shouldBe false
    UrlDTO.valid("http://192.168.1.1") shouldBe false
    UrlDTO.valid("http://169.254.169.254") shouldBe false  // AWS metadata
    UrlDTO.valid("http://[::1]/") shouldBe false
  }

  it should "reject URLs exceeding 2048 characters" in {
    val longUrl = "https://example.com/" + "a" * 2030
    longUrl.length should be > 2048
    UrlDTO.valid(longUrl) shouldBe false
  }

  it should "accept URLs up to 2048 characters" in {
    val okUrl = "https://example.com/" + "a" * (2048 - "https://example.com/".length)
    okUrl.length shouldBe 2048
    UrlDTO.valid(okUrl) shouldBe true
  }
}
