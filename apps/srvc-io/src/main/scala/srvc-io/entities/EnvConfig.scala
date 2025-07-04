package srvc_io.entities

import io.github.cdimascio.dotenv.Dotenv

object EnvConfig {
  private val dotenv = Dotenv.configure().ignoreIfMissing().load()

  val eventsPerSecond: Int                  = dotenv.get("EVENTS_PER_SECOND", "10").toInt
  val parkingPlatePattern: String           = dotenv.get("PARKING_PLATE_PATTERN", "aa-ddd-aa")
  val parkingPlateRandomProbability: Double = dotenv.get("PARKING_PLATE_RANDOM_PROBABILITY", "0.5").toDouble

  val parkingLots: Seq[String] = dotenv.get("PARKING_LOTS", "lot-01,lot-02").split(",").toSeq
  val parkingSlots: Seq[Seq[Int]] = dotenv
    .get("PARKING_SLOTS", "1-10,11-20")
    .split(",")
    .map { range =>
      range.split("-") match {
        case Array(start, end) => (start.toInt to end.toInt).toSeq
        case Array(single)     => Seq(single.toInt)
        case _                 => Seq.empty[Int]
      }
    }
    .toSeq
  val handicapSlots: Seq[Seq[Int]] = dotenv
    .get("HANDICAP_SLOTS", "1|4-10,11-12|20")
    .split(",")
    .map { lot =>
      lot
        .split('|')
        .flatMap {
          case range if range.contains("-") =>
            val Array(start, end) = range.split("-").map(_.toInt)
            start to end
          case single =>
            Seq(single.toInt)
        }
        .toSeq
    }
    .toSeq
  val vehicleColors: Seq[String] =
    dotenv.get("VEHICLE_COLORS", "red,blue,black,white,gray,silver,green").split(",").toSeq
  val vehicleTypes: Seq[String] = dotenv.get("VEHICLE_TYPES", "car,truck,motorcycle,van").split(",").toSeq
  val minParkingDuration: Int   = dotenv.get("MIN_PARKING_DURATION", "5").toInt
  val maxParkingDuration: Int   = dotenv.get("MAX_PARKING_DURATION", "180").toInt

  // Prometheus
  val prometheusHost: String = dotenv.get("PROMETHEUS_HOST", "localhost")
  val prometheusPort: Int    = dotenv.get("PROMETHEUS_PORT", "9090").toInt

  // Kafka
  val kafkaTopic: String   = dotenv.get("KAFKA_TOPIC", "parking-events")
  val kafkaServers: String = dotenv.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  val backoffMs: Long      = dotenv.get("RESILIENCE_BACKOFF_MS", "500").toLong
  val maxRetries: Int      = dotenv.get("RESILIENCE_MAX_RETRIES", "5").toInt
}
