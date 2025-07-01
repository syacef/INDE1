package srvc_alert.data.model

case class UserModel(
  parkingPlate: String,
  username: String,
  email: String,
  firstName: String,
  lastName: String,
  createdAt: Long = System.currentTimeMillis(),
  handicapped: Boolean = false
)
