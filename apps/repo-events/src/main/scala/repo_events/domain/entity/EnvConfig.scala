package repo_events.domain.entity

object EnvConfig {
  val appHost: String        = sys.env.getOrElse("APP_HOST", "0.0.0.0")
  val appPort: Int           = sys.env.getOrElse("APP_PORT", "8080").toInt
  val prometheusHost: String = sys.env.getOrElse("PROMETHEUS_HOST", "0.0.0.0")
  val prometheusPort: Int    = sys.env.getOrElse("PROMETHEUS_PORT", "9090").toInt

  // Kafka configuration
  val kafkaBootstrapServers: String = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  val kafkaGroupId: String          = sys.env.getOrElse("KAFKA_GROUP_ID", "repo-events-consumer")
  val kafkaTopic: String            = sys.env.getOrElse("KAFKA_PARKING_TOPIC", "parking-events")
}
