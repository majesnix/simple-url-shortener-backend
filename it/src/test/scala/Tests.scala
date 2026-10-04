import org.scalatest.flatspec.AnyFlatSpec
import sttp.client4.quick._
import sttp.model.StatusCode

class Tests extends AnyFlatSpec {

  it should "create short and resolve back to url" in {
    val url = ujson.Obj(
      "url" -> "https://foo.com/bar"
    )

    val createdShortUrl = quickRequest
      .post(uri"http://localhost:8080")
      .header("Content-Type", "application/json")
      .body(ujson.write(url))
      .send()

    assert(createdShortUrl.code == StatusCode.Ok)

    val returnedShort = ujson.read(createdShortUrl.body)

    val resolvedShortUrl = quickRequest
      .get(uri"http://localhost:8080/${returnedShort("short").str}")
      .send()

    assert(resolvedShortUrl.code == StatusCode.Ok)

    val returnedLong = ujson.read(resolvedShortUrl.body)

    assert(returnedLong("url").str == "https://foo.com/bar")
  }

  it should "return 404 if url does not exist" in {
    val notFoundLong = quickRequest
      .get(uri"http://localhost:8080/abc")
      .send()

    assert(notFoundLong.code == StatusCode.NotFound)
  }

  it should "deny shorten own server url" in {
    val url = ujson.Obj(
      "url" -> "https://localhost:8080"
    )

    val createdShortUrl = quickRequest
      .post(uri"http://localhost:8080")
      .header("Content-Type", "application/json")
      .body(ujson.write(url))
      .send()

    assert(createdShortUrl.code == StatusCode.BadRequest)
  }

  it should "deny disallowed URL schemes" in {
    val url = ujson.Obj(
      "url" -> "javascript:alert(1)"
    )

    val response = quickRequest
      .post(uri"http://localhost:8080")
      .header("Content-Type", "application/json")
      .body(ujson.write(url))
      .send()

    assert(response.code == StatusCode.BadRequest)
  }

  it should "return 4xx for a missing url field" in {
    val response = quickRequest
      .post(uri"http://localhost:8080")
      .header("Content-Type", "application/json")
      .body("""{}""")
      .send()

    assert(response.code.isClientError)
  }

  it should "return 4xx for a malformed JSON body" in {
    val response = quickRequest
      .post(uri"http://localhost:8080")
      .header("Content-Type", "application/json")
      .body("not json")
      .send()

    assert(response.code.isClientError)
  }

  it should "return 4xx for an empty body" in {
    val response = quickRequest
      .post(uri"http://localhost:8080")
      .header("Content-Type", "application/json")
      .body("")
      .send()

    assert(response.code.isClientError)
  }

  private def shorten(body: ujson.Value) =
    quickRequest
      .post(uri"http://localhost:8080")
      .header("Content-Type", "application/json")
      .body(ujson.write(body))
      .send()

  private def resolve(short: String) =
    quickRequest.get(uri"http://localhost:8080/$short").send()

  it should "resolve a link with an expiry" in {
    val created =
      shorten(ujson.Obj("url" -> "https://foo.com/expiring", "expiry" -> "1d"))
    assert(created.code == StatusCode.Ok)
    val resolved = resolve(ujson.read(created.body)("short").str)
    assert(resolved.code == StatusCode.Ok)
    assert(ujson.read(resolved.body)("url").str == "https://foo.com/expiring")
  }

  it should "resolve a one-time link exactly once" in {
    val created =
      shorten(ujson.Obj("url" -> "https://foo.com/once", "expiry" -> "1x"))
    assert(created.code == StatusCode.Ok)
    val short = ujson.read(created.body)("short").str

    val first = resolve(short)
    assert(first.code == StatusCode.Ok)
    assert(ujson.read(first.body)("url").str == "https://foo.com/once")

    assert(resolve(short).code == StatusCode.NotFound)
  }

  it should "reject an unknown expiry value" in {
    val created =
      shorten(ujson.Obj("url" -> "https://foo.com", "expiry" -> "2d"))
    assert(created.code == StatusCode.BadRequest)
  }

  it should "deny shortening the configured SERVER_URL host" in {
    val created = shorten(ujson.Obj("url" -> "https://sus.local/some/link"))
    assert(created.code == StatusCode.BadRequest)
  }

  it should "reject oversized request bodies with 413" in {
    val created =
      shorten(ujson.Obj("url" -> ("https://foo.com/" + "a" * 20000)))
    assert(created.code == StatusCode.PayloadTooLarge)
  }
}
