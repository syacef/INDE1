# srvc-alert

Scala app producing alert when handicaped slot is used by non authorized users

| Variable Name       | Environment Variable       | Default Value              | Type             |
| ------------------- | -------------------------- | -------------------------- | ---------------- |
| `appHost`           | `APP_HOST`                 | `"localhost"`              | `String`         |
| `appPort`           | `APP_PORT`                 | `"8080"`                   | `Int`            |
| `prometheusHost`    | `PROMETHEUS_HOST`          | `"localhost"`              | `String`         |
| `prometheusPort`    | `PROMETHEUS_PORT`          | `"9090"`                   | `Int`            |
| `kafkaAlertTopic`   | `KAFKA_ALERT_TOPIC`        | `"alert-events"`           | `String`         |
| `kafkaParkingTopic` | `KAFKA_PARKING_TOPIC`      | `"parking-events"`         | `String`         |
| `kafkaServers`      | `KAFKA_BOOTSTRAP_SERVERS`  | `"localhost:9092"`         | `String`         |
| `backoffMs`         | `RESILIENCE_BACKOFF_MS`    | `"500"`                    | `Long`           |
| `maxRetries`        | `RESILIENCE_MAX_RETRIES`   | `"5"`                      | `Int`            |
| `consumerGroupId`   | `KAFKA_CONSUMER_GROUP_ID`  | `"parking-event-consumer"` | `String`         |
| `autoOffsetReset`   | `KAFKA_AUTO_OFFSET_RESET`  | `"earliest"`               | `String`         |
| `enableAutoCommit`  | `KAFKA_ENABLE_AUTO_COMMIT` | `"true"`                   | `Boolean`        |
| `redisHost`         | `REDIS_HOST`               | `"localhost"`              | `String`         |
| `redisPort`         | `REDIS_PORT`               | `"6379"`                   | `Int`            |
| `redisDb`           | `REDIS_DB`                 | `"0"`                      | `Int`            |
| `redisPassword`     | `REDIS_PASSWORD`           | `null`                     | `Option[String]` |
