package repo_account.domain.service
import play.api.libs.json._
import redis.clients.jedis.JedisSentinelPool
import repo_account.data.model.UserModel
import repo_account.domain.entity.EnvConfig
import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.CollectionConverters._

class UserService(redisClient: JedisSentinelPool)(implicit ec: ExecutionContext) {
  implicit val userFormat: Format[UserModel] = Json.format[UserModel]
  
  private def safeParseJson(jsonString: String): Option[UserModel] = {
    if (jsonString.trim.isEmpty) {
      None
    } else {
      scala.util.control.Exception.allCatch.opt {
        Json.parse(jsonString).validate[UserModel] match {
          case JsSuccess(user, _) => Some(user)
          case JsError(_) => None
        }
      }.flatten
    }
  }
  
  private def safeWithJedis[T](f: redis.clients.jedis.Jedis => T): Option[T] = {
    val jedis = redisClient.getResource
    val result = scala.util.control.Exception.allCatch.opt(f(jedis))
    jedis.close()
    result
  }
  
  private def withJedis[T](f: redis.clients.jedis.Jedis => T): T = {
    val jedis = redisClient.getResource
    val result = f(jedis)
    jedis.close()
    result
  }
  
  def getUserByPlate(parkingPlate: String): Future[Option[UserModel]] = Future {
    safeWithJedis { jedis =>
      Option(jedis.get(EnvConfig.userKeyPrefix + parkingPlate)).flatMap(safeParseJson)
    }.flatten
  }
  
  def getAllUsers(): Future[Seq[UserModel]] = Future {
    safeWithJedis { jedis =>
      val keys = jedis.keys(EnvConfig.userKeyPrefix + "*").asScala
      keys.flatMap { key =>
        Option(jedis.get(key)).flatMap(safeParseJson)
      }.toSeq
    }.getOrElse(Seq.empty)
  }
  
  def createUser(parkingPlate: String, user: UserModel): Future[UserModel] = Future {
    withJedis { jedis =>
      val json = Json.toJson(user).toString()
      jedis.set(EnvConfig.userKeyPrefix + parkingPlate, json)
      user
    }
  }
  
  def createUser(user: UserModel): Future[UserModel] =
    createUser(user.parkingPlate, user)
  
  def createUsers(users: List[UserModel]): Future[List[UserModel]] = Future {
    withJedis { jedis =>
      users.map { user =>
        val json = Json.toJson(user).toString()
        jedis.set(EnvConfig.userKeyPrefix + user.parkingPlate, json)
        user
      }
    }
  }

  def createTemporaryUser(user: UserModel, expirationSeconds: Int): Future[UserModel] = Future {
    withJedis { jedis =>
      val tempKey = EnvConfig.userKeyPrefix + user.parkingPlate
      val json = Json.toJson(user).toString()
      jedis.setex(tempKey, expirationSeconds, json)
      user
    }
  }
  
  def getUserById(id: Int): Future[Option[UserModel]] =
    getUserByPlate(id.toString)
  
  def deleteUser(parkingPlate: String): Future[Boolean] = Future {
    safeWithJedis { jedis =>
      jedis.del(EnvConfig.userKeyPrefix + parkingPlate) > 0
    }.getOrElse(false)
  }
  
  def deleteUser(id: Int): Future[Boolean] =
    deleteUser(id.toString)
}

object UserService {
  def apply(redisClient: JedisSentinelPool)(implicit ec: ExecutionContext): UserService =
    new UserService(redisClient)
}
