package srvc_io.entities

import io.github.cdimascio.dotenv.Dotenv

object EnvConfig {
  private val dotenv = Dotenv.configure().ignoreIfMissing().load()

  val eventsPerSecond: Int     = dotenv.get("EVENTS_PER_SECOND", "10").toInt
  val kafkaTopic: String       = dotenv.get("KAFKA_TOPIC", "parking-events")
  val kafkaServers: String     = dotenv.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  val parkingLots: Seq[String] = dotenv.get("PARKING_LOTS", "lot-01,lot-02").split(",").toSeq
  val maxRetries: Int          = dotenv.get("RESILIENCE_MAX_RETRIES", "5").toInt
  val backoffMs: Long          = dotenv.get("RESILIENCE_BACKOFF_MS", "500").toLong
  val metricsPort: Int         = dotenv.get("METRICS_PORT", "9100").toInt
  val vehicleColors: Seq[String] =
    dotenv.get("VEHICLE_COLORS", "red,blue,black,white,gray,silver,green").split(",").toSeq
  val vehicleTypes: Seq[String] = dotenv.get("VEHICLE_TYPES", "car,truck,motorcycle,van").split(",").toSeq
  val parkingZones: Seq[String] = dotenv.get("PARKING_ZONES", "Blue Zone,Red Zone,Green Zone").split(",").toSeq
  val driverNames: Seq[String]  = dotenv.get("DRIVER_NAMES", "John Smith,Jane Doe").split(",").toSeq
  val minParkingDuration: Int   = dotenv.get("MIN_PARKING_DURATION", "5").toInt
  val maxParkingDuration: Int   = dotenv.get("MAX_PARKING_DURATION", "180").toInt
  val maxSpotsPerLot: Int       = dotenv.get("MAX_SPOTS_PER_LOT", "100").toInt
  val exitProbability: Double   = dotenv.get("EXIT_PROBABILITY", "0.5").toDouble
}
