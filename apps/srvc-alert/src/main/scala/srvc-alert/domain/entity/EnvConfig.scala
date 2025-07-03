package srvc_alert.domain.entity

import io.github.cdimascio.dotenv.Dotenv

object EnvConfig {
  private val dotenv = Dotenv.configure().ignoreIfMissing().load()

  // Heatlh API
  val appPort: Int    = dotenv.get("APP_PORT", "8080").toInt
  val appHost: String = dotenv.get("APP_HOST", "localhost")

  // Prometheus
  val prometheusPort: Int    = dotenv.get("PROMETHEUS_PORT", "9090").toInt
  val prometheusHost: String = dotenv.get("PROMETHEUS_HOST", "localhost")

  // Kafka
  val kafkaAlertTopic: String   = dotenv.get("KAFKA_ALERT_TOPIC", "alert-events")
  val kafkaParkingTopic: String = dotenv.get("KAFKA_PARKING_TOPIC", "parking-events")
  val kafkaServers: String      = dotenv.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  val backoffMs: Long           = dotenv.get("RESILIENCE_BACKOFF_MS", "500").toLong
  val maxRetries: Int           = dotenv.get("RESILIENCE_MAX_RETRIES", "5").toInt

  val consumerGroupId: String   = dotenv.get("KAFKA_CONSUMER_GROUP_ID", "parking-event-consumer")
  val autoOffsetReset: String   = dotenv.get("KAFKA_AUTO_OFFSET_RESET", "earliest")
  val enableAutoCommit: Boolean = dotenv.get("KAFKA_ENABLE_AUTO_COMMIT", "true").toBoolean

  // Redis
  val redisHosts: Set[String]       = dotenv.get("REDIS_HOSTS", "localhost:6379").split(",").map(_.trim).toSet
  val redisMasterName: String       = dotenv.get("REDIS_MASTER_NAME", "")
  val redisPassword: Option[String] = Option(dotenv.get("REDIS_PASSWORD")).filter(_.nonEmpty)
  val userKeyPrefix: String         = dotenv.get("REDIS_USER_KEY_PREFIX", "user:")
}
