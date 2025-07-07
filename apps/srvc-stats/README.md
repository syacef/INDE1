# srvc-stats

Spark app creating metrics on the kafka events

| Variable Name         | Environment Variable       | Default Value                                       | Type          |
|-----------------------|----------------------------|-----------------------------------------------------|---------------|
| `redisHosts`          | `REDIS_HOSTS`              | `"localhost:6379"` (comma separated)                | `String`      |
| `redisMatserName`     | `REDIS_MASTER_NAME`        | `""`                                                | `String`      |
| `redisPassword`       | `REDIS_PASSWORD`           | `""`                                                | `String`      |
| `minioHost`           | `MINIO_HOST`               | `"localhost:9000"`                                  | `String`      |
| `minioAccessKey`      | `MINIO_ACCESS_KEY`         | `""`                                                | `String`      |
| `minioSecretKey`      | `MINIO_SECRET_KEY`         | `""`                                                | `String`      |
| `minioBucket`         | `MINIO_BUCKET`             | `"parkings-events"`                                 | `String`      |
| `revenuePerHour`      | `REVENUE_PER_HOUR`         | `"2"`                                               | `Int`         |
