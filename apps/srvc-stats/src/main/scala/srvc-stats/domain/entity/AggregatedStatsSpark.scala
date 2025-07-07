package srvc_stats.domain.entity
import scala.collection.immutable.Map

case class AggregatedStatsSpark(
  date: String,
  hour: String,
  nbrEntries: Long,
  nbrExit: Long,
  occupancy: Map[String, Long],
  revenueSimulation: Double,
  vehicleTypes: Map[String, Long]
)
