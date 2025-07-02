package srvc_alert.domain.entity

final case class AlertEvent(
  vehiclePlate: String,
  spotId: String,
  lotId: String,
  violationType: String,
  timestamp: String
)
