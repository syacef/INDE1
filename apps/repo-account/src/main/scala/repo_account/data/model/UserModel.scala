package repo_account.data.model

import io.circe._
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

case class UserModel(
  parkingPlate: String,
  username: String,
  email: String,
  firstName: String,
  lastName: String,
  createdAt: Long = System.currentTimeMillis(),
  handicapped: Boolean = false
)

object UserModel {
  implicit val encoder: Encoder[UserModel] = deriveEncoder[UserModel]
  implicit val decoder: Decoder[UserModel] = deriveDecoder[UserModel]

  def create(
    username: String,
    parkingPlate: String,
    email: String,
    firstName: String,
    lastName: String,
    handicapped: Boolean = false
  ): UserModel =
    UserModel(
      parkingPlate = parkingPlate,
      username = username,
      email = email,
      firstName = firstName,
      lastName = lastName,
      handicapped = handicapped
    )
}
