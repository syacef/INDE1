package srvc_notifier.domain.entity

final case class AlertNotification(
  message: String,
  severity: String,
  timestamp: String
)
