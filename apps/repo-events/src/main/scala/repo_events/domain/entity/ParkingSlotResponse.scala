package repo_events.domain.entity

case class ParkingSlotResponse(
  slot_id: String,
  occupied: Boolean,
  lot: String,
  plate: Option[String] = None
)
