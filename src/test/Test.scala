import org.http4s.client.Client

val request: Request[IO] = Request(method = Method.GET, uri = uri"/user/not-used")
val client: Client[IO] = Client.fromHttpApp(httpApp)

val resp: IO[Json]     = client.expect[Json](request)
assert(resp.unsafeRunSync() == expectedJson)