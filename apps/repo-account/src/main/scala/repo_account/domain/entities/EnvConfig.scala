package repo_account.domain.entities

import io.github.cdimascio.dotenv.Dotenv

object EnvConfig {
  private val dotenv = Dotenv.configure().ignoreIfMissing().load()

  val redisHost: String     = dotenv.get("REDIS_HOST", "localhost")
  val redisPort: Int        = dotenv.get("REDIS_PORT", "6379").toInt
  val redisPassword: String = dotenv.get("REDIS_PASSWORD", "")

  val appHost: String = dotenv.get("APP_HOST", "0.0.0.0")
  val appPort: Int    = dotenv.get("APP_PORT", "8080").toInt
}
