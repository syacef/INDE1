package srvc_stats.domain.entity

case class ParkingEvent(
  eventType: String,
  timestamp: String,
  vehicle: Vehicle,
  parking: Parking,
  duration: Option[Long]
)

case class Vehicle(
  licensePlate: String,
  vehicleType: String,
  color: String
)

case class Parking(
  parkingLotId: String,
  parkingSpotId: String,
  isSlotHandicapped: Boolean
)
