package srvc_alert.domain.service

import org.slf4j.LoggerFactory
import play.api.libs.json._
import redis.clients.jedis.JedisPool
import srvc_alert.data.model.UserModel
import srvc_alert.domain.entity.EnvConfig

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Using

class UserService(implicit ec: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(getClass)

  implicit val userFormat: Format[UserModel] = Json.format[UserModel]
  private val USER_KEY_PREFIX                = "user:"

  private val redisPool = EnvConfig.redisPassword match {
    case Some(password) =>
      val uri = s"redis://:$password@${EnvConfig.redisHost}:${EnvConfig.redisPort}/${EnvConfig.redisDb}"
      new JedisPool(uri)
    case None =>
      new JedisPool(EnvConfig.redisHost, EnvConfig.redisPort)
  }

  def getUserByPlate(parkingPlate: String): Future[Option[UserModel]] = {
    val jedis  = redisPool.getResource
    val result = Option(jedis.get(USER_KEY_PREFIX + parkingPlate)).map(Json.parse(_).as[UserModel])
    jedis.close()
    Future.successful(result)
  }

  def isUserHandicapped(licensePlate: String): Future[Boolean] =
    getUserByPlate(licensePlate).map {
      case Some(user) => user.handicapped
      case None =>
        logger.warn(s"License plate $licensePlate not found - treating as non-handicapped")
        false
    }.recover { case ex: Exception =>
      logger.error(s"Error checking handicapped status for license plate $licensePlate")
      false
    }

  def healthCheck(): Future[Boolean] = Future {
    logger.debug("Performing Redis health check")

    Using(redisPool.getResource) { jedis =>
      val pong = jedis.ping()
      val ok   = pong.equalsIgnoreCase("PONG")
      if (ok) logger.debug("Redis health check passed")
      else logger.warn(s"Redis health check failed - response: $pong")
      ok
    }.getOrElse {
      logger.error("Redis health check failed - exception occurred")
      false
    }
  }
}
