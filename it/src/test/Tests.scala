import org.scalatest._

class CubeCalculatorTest extends AnyFlatSpec {
  val client: HttpApp[IO] = httpRoutes[IO](success).orNotFound

  qui
}