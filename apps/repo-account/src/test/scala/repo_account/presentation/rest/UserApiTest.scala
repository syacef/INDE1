/*

package repo_account.presentation.rest

import cats.effect._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import redis.clients.jedis.JedisPool
import repo_account.data.model.UserModel
import repo_account.domain.entities.EnvConfig
import repo_account.presentation.rest.UserApi
import scala.util.Try

class UserApiTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll {

  private val testRedisPool = new JedisPool(EnvConfig.redisHost, EnvConfig.redisPort)

  private def createTestUserApi(): HttpRoutes[IO] = UserApi.createRoutes(testRedisPool).routes

  private val httpApp = createTestUserApi().orNotFound
  private val client  = Client.fromHttpApp(httpApp)

  private val testUser1 = UserModel(
    parkingPlate = "ABC123",
    firstName = "John",
    lastName = "Doe",
    handicapped = false,
    username = "john.doe",
    email = "john.doe@email.com"
  )

  private val testUser2 = UserModel(
    parkingPlate = "XYZ987",
    firstName = "John2",
    lastName = "Doe",
    handicapped = false,
    username = "john2.doe",
    email = "john2.doe@email.com"
  )

  override def beforeEach(): Unit =
    Try {
      val jedis = testRedisPool.getResource
      try
        jedis.flushDB()
      finally
        jedis.close()
    }

  override def afterAll(): Unit =
    Try(testRedisPool.close())

  "UserApi GET /account" should "return empty list when no users exist" in {
    val request = Request[IO](Method.GET, uri"/account")

    val response = client.expect[List[UserModel]](request).unsafeRunSync()

    response shouldBe empty
  }

  "UserApi POST /account" should "create a new user successfully" in {
    val request = Request[IO](Method.POST, uri"/account")
      .withEntity(testUser1.asJson)

    val response = client
      .run(request)
      .use { resp =>
        for {
          status <- IO.pure(resp.status)
          body   <- resp.as[UserModel]
        } yield (status, body)
      }
      .unsafeRunSync()

    response._1 shouldBe Status.Created
    response._2.parkingPlate shouldBe testUser1.parkingPlate
    response._2.firstName shouldBe testUser1.firstName
    response._2.lastName shouldBe testUser1.lastName
    response._2.email shouldBe testUser1.email
  }

  it should "handle invalid JSON gracefully" in {
    val invalidJson = """{"invalid": "json"}"""
    val request = Request[IO](Method.POST, uri"/account")
      .withEntity(invalidJson)
      .withContentType(`Content-Type`(MediaType.application.json))

    val status = client.run(request).use(resp => IO.pure(resp.status)).unsafeRunSync()

    status should (be(Status.BadRequest) or be(Status.UnprocessableEntity))
  }

  "UserApi GET /account" should "retrieve all users after creating them" in {
    val createRequest1 = Request[IO](Method.POST, uri"/account").withEntity(testUser1.asJson)
    val createRequest2 = Request[IO](Method.POST, uri"/account").withEntity(testUser2.asJson)

    client.expect[UserModel](createRequest1).unsafeRunSync()
    client.expect[UserModel](createRequest2).unsafeRunSync()

    val getAllRequest = Request[IO](Method.GET, uri"/account")
    val users         = client.expect[List[UserModel]](getAllRequest).unsafeRunSync()

    users should have size 2
    users.map(_.parkingPlate) should contain allOf (testUser1.parkingPlate, testUser2.parkingPlate)
  }

  "UserApi GET /account/{id}" should "return 404 when user not found" in {
    val nonExistentId = 99999
    val request       = Request[IO](Method.GET, Uri.unsafeFromString(s"/account/$nonExistentId"))

    val status = client.run(request).use(resp => IO.pure(resp.status)).unsafeRunSync()

    status shouldBe Status.NotFound
  }

  "UserApi DELETE /account/{id}" should "return 404 when deleting non-existent user" in {
    val nonExistentId = 99999
    val request       = Request[IO](Method.DELETE, Uri.unsafeFromString(s"/account/$nonExistentId"))

    val status = client.run(request).use(resp => IO.pure(resp.status)).unsafeRunSync()

    status shouldBe Status.NotFound
  }

  "Health endpoints" should "return OK for liveness check" in {
    val request = Request[IO](Method.GET, uri"/liveness")

    val response = client
      .run(request)
      .use { resp =>
        for {
          status <- IO.pure(resp.status)
          body   <- resp.as[String]
        } yield (status, body)
      }
      .unsafeRunSync()

    response._1 shouldBe Status.Ok
    response._2 shouldBe "Service is alive"
  }

  it should "return OK for readiness check" in {
    val request = Request[IO](Method.GET, uri"/readiness")

    val response = client
      .run(request)
      .use { resp =>
        for {
          status <- IO.pure(resp.status)
          body   <- resp.as[String]
        } yield (status, body)
      }
      .unsafeRunSync()

    response._1 shouldBe Status.Ok
    response._2 shouldBe "Service is ready"
  }

  "UserApi integration" should "support full CRUD operations" in {
    val createRequest = Request[IO](Method.POST, uri"/account").withEntity(testUser1.asJson)
    val created       = client.expect[UserModel](createRequest).unsafeRunSync()
    created.parkingPlate shouldBe testUser1.parkingPlate

    val getAllRequest = Request[IO](Method.GET, uri"/account")
    val allUsers      = client.expect[List[UserModel]](getAllRequest).unsafeRunSync()
    allUsers should have size 1
    allUsers.head.parkingPlate shouldBe testUser1.parkingPlate
  }
}
 */
