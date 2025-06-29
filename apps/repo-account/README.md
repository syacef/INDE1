# srvc-io

Scala app managing generation of parking events sent to kafka

# srvc-io

**Scala app managing generation of parking events sent to Kafka**

| Variable Name         | Environment Variable       | Default Value                                       | Type          |
|-----------------------|----------------------------|-----------------------------------------------------|---------------|
| `eventsPerSecond`     | `EVENTS_PER_SECOND`        | `10`                                                | `Int`         |
| `kafkaTopic`          | `KAFKA_TOPIC`              | `"parking-events"`                                  | `String`      |
| `kafkaServers`        | `KAFKA_BOOTSTRAP_SERVERS`  | `"localhost:9092"`                                  | `String`      |
| `parkingLots`         | `PARKING_LOTS`             | `"lot-01,lots-02"`                                  | `Seq[String]` |
| `parkingSlots`        | `PARKING_SLOTS`            | `"0-50,10-20"`                                      | `Seq[String]` |
| `handicapSlots`       | `HANDICAP_SLOTS`           | `"10|11,10-20"`                                     | `Seq[String]` |
| `maxRetries`          | `RESILIENCE_MAX_RETRIES`   | `5`                                                 | `Int`         |
| `backoffMs`           | `RESILIENCE_BACKOFF_MS`    | `500`                                               | `Long`        |
| `prometheusEndpoint`  | `PROMETHEUS_ENDPOINT`      | `localhost:9100`                                    | `String`         |
| `vehicleColors`       | `VEHICLE_COLORS`           | `"red,blue,black,white,gray,silver,green"`          | `Seq[String]` |
| `vehicleTypes`        | `VEHICLE_TYPES`            | `"car,truck,motorcycle,van"`                        | `Seq[String]` |
| `minParkingDuration`  | `MIN_PARKING_DURATION`     | `5`                                                 | `Int`         |
| `maxParkingDuration`  | `MAX_PARKING_DURATION`     | `180`                                               | `Int`         |
