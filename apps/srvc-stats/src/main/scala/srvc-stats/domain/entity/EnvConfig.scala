package srvc_stats.domain.entity

object EnvConfig {
  val redisHosts: Set[String] = {
    val hostsString = sys.env.getOrElse("REDIS_HOSTS", "localhost:26379")
    val hostsArray  = hostsString.split(",")
    val hostsList   = hostsArray.toList.map(_.trim)
    hostsList.toSet
  }
  val redisPassword: String   = sys.env.getOrElse("REDIS_PASSWORD", "")
  val redisMasterName: String = sys.env.getOrElse("REDIS_MASTER_NAME", "mymaster")

  val minioHost: String      = sys.env.getOrElse("MINIO_HOST", "http://localhost:9000")
  val minioAccessKey: String = sys.env.getOrElse("MINIO_ACCESS_KEY", "minio")
  val minioSecretKey: String = sys.env.getOrElse("MINIO_SECRET_KEY", "minio123")
  val minioBucket: String    = sys.env.getOrElse("MINIO_BUCKET", "parking-events")
  val minioPrefixPath: String = sys.env.getOrElse("MINIO_PREFIX_PATH", "topics/parking-event-topic")

  val revenuePerHour: Int = sys.env.getOrElse("REVENUE_PER_HOUR", "2").toInt
}
