package srvc_alert.domain.entity

import io.github.cdimascio.dotenv.Dotenv

object EnvConfig {
  private val dotenv = Dotenv.configure().ignoreIfMissing().load()

  // Prometheus
  val prometheusEndpoint: String = dotenv.get("PROMETHEUS_ENDPOINT", "localhost:9100")

  // Kafka
  val kafkaTopic: String   = dotenv.get("KAFKA_TOPIC", "parking-events")
  val kafkaServers: String = dotenv.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  val backoffMs: Long      = dotenv.get("RESILIENCE_BACKOFF_MS", "500").toLong
  val maxRetries: Int      = dotenv.get("RESILIENCE_MAX_RETRIES", "5").toInt

  val consumerGroupId: String   = dotenv.get("KAFKA_CONSUMER_GROUP_ID", "parking-event-consumer")
  val autoOffsetReset: String   = dotenv.get("KAFKA_AUTO_OFFSET_RESET", "earliest")
  val enableAutoCommit: Boolean = dotenv.get("KAFKA_ENABLE_AUTO_COMMIT", "true").toBoolean

  // Redis
  val redisHost: String             = dotenv.get("REDIS_HOST", "localhost")
  val redisPort: Int                = dotenv.get("REDIS_PORT", "6379").toInt
  val redisDb: Int                  = dotenv.get("REDIS_DB", "0").toInt
  val redisPassword: Option[String] = Option(dotenv.get("REDIS_PASSWORD")).filter(_.nonEmpty)
}
