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
}