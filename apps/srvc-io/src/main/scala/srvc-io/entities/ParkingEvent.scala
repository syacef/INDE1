package srvc_io.entities

case class ParkingEvent(
  eventType: String,
  timestamp: String,
  vehicle: Vehicle,
  parking: Parking,
  duration: Some[Long]
)

case class Vehicle(
  licensePlate: String,
  vehicleType: String,
  color: String
)

case class Parking(
  parkingLotId: String,
  parkingSpotId: String,
  zone: String
)
