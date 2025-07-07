package srvc_stats.domain.entity

case class ParkingEventSpark(
  parkingSpotId: String,
  parkingLotId: String,
  isSlotHandicapped: Boolean,
  duration: Long,
  eventType: String,
  timestamp: String,
  licensePlate: String,
  color: String,
  vehicleType: String
)
