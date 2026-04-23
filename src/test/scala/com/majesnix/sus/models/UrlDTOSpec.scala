package com.majesnix.sus.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UrlDTOSpec extends AnyFlatSpec with Matchers {

  "valid" should "accept http, https and ftp URLs" in {
    UrlDTO.valid("http://example.com") shouldBe true
    UrlDTO.valid("https://example.com/path?q=1") shouldBe true
    UrlDTO.valid("ftp://files.example.com/readme") shouldBe true
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
    // default server.url in application.conf is "localhost"
    UrlDTO.valid("https://localhost:8080/abc") shouldBe false
    UrlDTO.valid("http://LOCALHOST") shouldBe false
  }

  it should "not reject URLs that merely contain the server host as a substring" in {
    // previous `contains` check rejected these; parsed-host check must not
    UrlDTO.valid("https://example.com/?ref=localhost") shouldBe true
    UrlDTO.valid("https://mylocalhostmirror.com") shouldBe true
  }
}
