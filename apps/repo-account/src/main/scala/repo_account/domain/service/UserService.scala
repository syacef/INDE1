package repo_account.domain.service

import play.api.libs.json._
import redis.clients.jedis.JedisSentinelPool
import repo_account.data.model.UserModel
import repo_account.domain.entity.EnvConfig

import scala.concurrent.{ ExecutionContext, Future }
import scala.jdk.CollectionConverters._
import scala.util.Try

class UserService(redisClient: JedisSentinelPool)(implicit ec: ExecutionContext) {

  implicit val userFormat: Format[UserModel] = Json.format[UserModel]

  def getUserByPlate(parkingPlate: String): Future[Option[UserModel]] = Future {
    Try {
      val jedis = redisClient.getResource
      try
        Option(jedis.get(EnvConfig.userKeyPrefix + parkingPlate)).map(Json.parse(_).as[UserModel])
      finally
        jedis.close()
    }.getOrElse(None)
  }

  def getAllUsers(): Future[Seq[UserModel]] = Future {
    val jedis = redisClient.getResource
    try {
      val keys = jedis.keys(EnvConfig.userKeyPrefix + "*").asScala
      keys.flatMap { key =>
        Try(Json.parse(jedis.get(key)).as[UserModel]).toOption
      }.toSeq
    } finally
      jedis.close()
  }

  def createUser(parkingPlate: String, user: UserModel): Future[UserModel] = Future {
    val jedis = redisClient.getResource
    try {
      val json = Json.toJson(user).toString()
      jedis.set(EnvConfig.userKeyPrefix + parkingPlate, json)
      user
    } finally
      jedis.close()
  }

  def createUser(user: UserModel): Future[UserModel] =
    createUser(user.parkingPlate, user)

  def getUserById(id: Int): Future[Option[UserModel]] =
    getUserByPlate(id.toString)

  def deleteUser(parkingPlate: String): Future[Boolean] = Future {
    val jedis = redisClient.getResource
    try
      jedis.del(EnvConfig.userKeyPrefix + parkingPlate) > 0
    finally
      jedis.close()
  }

  def deleteUser(id: Int): Future[Boolean] =
    deleteUser(id.toString)
}

object UserService {
  def apply(redisClient: JedisSentinelPool)(implicit ec: ExecutionContext): UserService =
    new UserService(redisClient)
}
