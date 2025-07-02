package repo_account.domain.service

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{ Format, Json }
import redis.clients.jedis.{ Jedis, JedisSentinelPool }
import repo_account.data.model.UserModel

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

class UserServiceTest extends AsyncWordSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global

  implicit val userFormat: Format[UserModel] = Json.format[UserModel]

  // Sample test data
  val sampleUser: UserModel = UserModel(
    parkingPlate = "ABC123",
    firstName = "John",
    lastName = "Doe",
    email = "john.doe@example.com",
    username = "john.doe"
  )

  val sampleUserJson: String = Json.toJson(sampleUser).toString()

  "UserService" should {

    "getUserByPlate" should {
      "return Some(UserModel) when user exists in Redis" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.get("user:ABC123")).thenReturn(sampleUserJson)

        val userService = new UserService(mockJedisSentinelPool)

        userService.getUserByPlate("ABC123").map { result =>
          result shouldBe Some(sampleUser)
          verify(mockJedis).close()
          succeed
        }
      }

      "return None when user does not exist in Redis" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.get("user:XYZ789")).thenReturn("")

        val userService = new UserService(mockJedisSentinelPool)

        userService.getUserByPlate("XYZ789").map { result =>
          result shouldBe None
          verify(mockJedis).close()
          succeed
        }
      }

      "return None when Redis throws an exception" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.get("user:ERROR")).thenThrow(new RuntimeException("Redis error"))

        val userService = new UserService(mockJedisSentinelPool)

        userService.getUserByPlate("ERROR").map { result =>
          result shouldBe None
          verify(mockJedis).close()
          succeed
        }
      }

      "return None when JSON parsing fails" in {
        // Arrange
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.get("user:INVALID")).thenReturn("invalid json")

        val userService = new UserService(mockJedisSentinelPool)

        // Act & Assert
        userService.getUserByPlate("INVALID").map { result =>
          result shouldBe None
          verify(mockJedis).close()
          succeed
        }
      }
    }

    "getAllUsers" should {
      "return all users when multiple users exist" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        val user1     = sampleUser
        val user2     = sampleUser.copy(parkingPlate = "DEF456", firstName = "Jane")
        val user1Json = Json.toJson(user1).toString()
        val user2Json = Json.toJson(user2).toString()

        val keys = Set("user:ABC123", "user:DEF456").asJava

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.keys("user:*")).thenReturn(keys)
        when(mockJedis.get("user:ABC123")).thenReturn(user1Json)
        when(mockJedis.get("user:DEF456")).thenReturn(user2Json)

        val userService = new UserService(mockJedisSentinelPool)

        userService.getAllUsers().map { result =>
          result should contain theSameElementsAs Seq(user1, user2)
          verify(mockJedis).close()
          succeed
        }
      }

      "return empty sequence when no users exist" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.keys("user:*")).thenReturn(Set.empty[String].asJava)

        val userService = new UserService(mockJedisSentinelPool)

        userService.getAllUsers().map { result =>
          result shouldBe empty
          verify(mockJedis).close()
          succeed
        }
      }

      "filter out users with invalid JSON" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        val validUser     = sampleUser
        val validUserJson = Json.toJson(validUser).toString()

        val keys = Set("user:ABC123", "user:INVALID").asJava

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.keys("user:*")).thenReturn(keys)
        when(mockJedis.get("user:ABC123")).thenReturn(validUserJson)
        when(mockJedis.get("user:INVALID")).thenReturn("invalid json")

        val userService = new UserService(mockJedisSentinelPool)

        userService.getAllUsers().map { result =>
          result should contain only validUser
          verify(mockJedis).close()
          succeed
        }
      }
    }

    "createUser with parkingPlate parameter" should {
      "successfully create user and return the user" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.set("user:ABC123", sampleUserJson)).thenReturn("OK")

        val userService = new UserService(mockJedisSentinelPool)

        userService.createUser("ABC123", sampleUser).map { result =>
          result shouldBe sampleUser
          verify(mockJedis).set("user:ABC123", sampleUserJson)
          verify(mockJedis).close()
          succeed
        }
      }
    }

    "createUser with UserModel parameter" should {
      "successfully create user using user's parkingPlate" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.set("user:ABC123", sampleUserJson)).thenReturn("OK")

        val userService = new UserService(mockJedisSentinelPool)

        userService.createUser(sampleUser).map { result =>
          result shouldBe sampleUser
          verify(mockJedis).set("user:ABC123", sampleUserJson)
          verify(mockJedis).close()
          succeed
        }
      }
    }

    "getUserById" should {
      "delegate to getUserByPlate with string conversion" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.get("user:123")).thenReturn(sampleUserJson)

        val userService = new UserService(mockJedisSentinelPool)

        userService.getUserById(123).map { result =>
          result shouldBe Some(sampleUser)
          verify(mockJedis).get("user:123")
          verify(mockJedis).close()
          succeed
        }
      }
    }

    "deleteUser with parkingPlate parameter" should {
      "return true when user is successfully deleted" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.del("user:ABC123")).thenReturn(1L)

        val userService = new UserService(mockJedisSentinelPool)

        userService.deleteUser("ABC123").map { result =>
          result shouldBe true
          verify(mockJedis).del("user:ABC123")
          verify(mockJedis).close()
          succeed
        }
      }

      "return false when user does not exist" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.del("user:XYZ789")).thenReturn(0L)

        val userService = new UserService(mockJedisSentinelPool)

        userService.deleteUser("XYZ789").map { result =>
          result shouldBe false
          verify(mockJedis).del("user:XYZ789")
          verify(mockJedis).close()
          succeed
        }
      }
    }

    "deleteUser with id parameter" should {
      "delegate to deleteUser with string conversion" in {
        val mockJedisSentinelPool = mock[JedisSentinelPool]
        val mockJedis             = mock[Jedis]

        when(mockJedisSentinelPool.getResource).thenReturn(mockJedis)
        when(mockJedis.del("user:123")).thenReturn(1L)

        val userService = new UserService(mockJedisSentinelPool)

        userService.deleteUser(123).map { result =>
          result shouldBe true
          verify(mockJedis).del("user:123")
          verify(mockJedis).close()
          succeed
        }
      }
    }
  }
}
