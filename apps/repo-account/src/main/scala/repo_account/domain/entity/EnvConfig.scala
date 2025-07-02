package repo_account.domain.entity

import io.github.cdimascio.dotenv.Dotenv

object EnvConfig {
  private val dotenv = Dotenv.configure().ignoreIfMissing().load()

  // Redis
  val redisHosts: Set[String] = dotenv.get("REDIS_HOSTS", "localhost:6379").split(",").map(_.trim).toSet
  val redisPassword: String   = dotenv.get("REDIS_PASSWORD", "")
  val redisMasterName: String = dotenv.get("REDIS_MASTER_NAME", "")
  val userKeyPrefix: String   = dotenv.get("REDIS_USER_KEY_PREFIX", "user:")

  val appHost: String = dotenv.get("APP_HOST", "0.0.0.0")
  val appPort: Int    = dotenv.get("APP_PORT", "8080").toInt
}
