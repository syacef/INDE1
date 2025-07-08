package repo_events.presentation.dto

case class ParkingSlotResponse(
  slot_id: String,
  occupied: Boolean,
  lot: String,
  plate: Option[String] = None
)
