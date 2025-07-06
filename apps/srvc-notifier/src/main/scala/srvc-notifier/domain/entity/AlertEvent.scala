package srvc_notifier.domain.entity

final case class AlertEvent(
  vehiclePlate: String,
  spotId: String,
  lotId: String,
  violationType: String,
  timestamp: String
)
