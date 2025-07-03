package repo_account.domain.entity

import io.github.cdimascio.dotenv.Dotenv

object EnvConfig {
  private val dotenv = Dotenv.configure().ignoreIfMissing().load()

  // Redis
  val redisHosts: Set[String] = dotenv.get("REDIS_HOSTS", "localhost:6379").split(",").map(_.trim).toSet
  val redisPassword: String   = dotenv.get("REDIS_PASSWORD", "")
  val redisMasterName: String = dotenv.get("REDIS_MASTER_NAME", "")
  val userKeyPrefix: String   = dotenv.get("REDIS_USER_KEY_PREFIX", "user:")
  val temporaryAccountExpiration: Int = dotenv.get("TEMPORARY_ACCOUNT_EXPIRATION", "86400").toInt // Default 1 day

  // Prometheus
  val prometheusHost: String  = dotenv.get("PROMETHEUS_HOST", "localhost")
  val prometheusPort: Int  = dotenv.get("PROMETHEUS_PORT", "9090").toInt

  val appHost: String = dotenv.get("APP_HOST", "0.0.0.0")
  val appPort: Int    = dotenv.get("APP_PORT", "8080").toInt
}
