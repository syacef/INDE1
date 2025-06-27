# srvc-io

Scala app managing generation of parking events sent to kafka

| Variable Name        | Environment Variable      | Default Value                                                                                                                           | Type          |
| -------------------- | ------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | ------------- |
| `eventsPerSecond`    | `EVENTS_PER_SECOND`       | `10`                                                                                                                                    | `Int`         |
| `kafkaTopic`         | `KAFKA_TOPIC`             | `"parking-events"`                                                                                                                      | `String`      |
| `kafkaServers`       | `KAFKA_BOOTSTRAP_SERVERS` | `"localhost:9092"`                                                                                                                      | `String`      |
| `parkingLots`        | `PARKING_LOTS`            | `"lot-01"`                                                                         | `Seq[String]` |
| `maxRetries`         | `RESILIENCE_MAX_RETRIES`  | `5`                                                                                                                                     | `Int`         |
| `backoffMs`          | `RESILIENCE_BACKOFF_MS`   | `500`                                                                                                                                   | `Long`        |
| `metricsPort`        | `METRICS_PORT`            | `9100`                                                                                                                                  | `Int`         |
| `vehicleColors`      | `VEHICLE_COLORS`          | `"red,blue,black,white,gray,silver,green"`                                                                                              | `Seq[String]` |
| `vehicleTypes`       | `VEHICLE_TYPES`           | `"car,truck,motorcycle,van"`                                                                                                            | `Seq[String]` |
| `parkingZones`       | `PARKING_ZONES`           | `"Blue Zone,Red Zone,Green Zone"`                                                                                                       | `Seq[String]` |
| `driverNames`        | `DRIVER_NAMES`            | `"John Smith,Jane Doe,Mike Johnson,Sarah Wilson,David Brown,Lisa Davis,Robert Miller,Emily Garcia,Michael Rodriguez,Jennifer Martinez"` | `Seq[String]` |
| `minParkingDuration` | `MIN_PARKING_DURATION`    | `5`                                                                                                                                     | `Int`         |
| `maxParkingDuration` | `MAX_PARKING_DURATION`    | `180`                                                                                                                                   | `Int`         |
| `maxSpotsPerLot`     | `MAX_SPOTS_PER_LOT`       | `100`                                                                                                                                   | `Int`         |
