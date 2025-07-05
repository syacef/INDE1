package srvc_notifier.domain.service

import cats.effect._
import io.circe._
import org.http4s.Method._
import org.http4s._
import org.http4s.circe._
import org.http4s.client._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import srvc_notifier.domain.entity.AlertNotification

class DiscordNotifier(webhookUrl: String, client: Client[IO]) {
  implicit val logger = Slf4jLogger.getLogger[IO]

  def notify(alert: AlertNotification): IO[Unit] = {
    val payload = Json.obj(
      "content" -> Json.fromString(s"**[${alert.severity}]** ${alert.message}"),
      "embeds" -> Json.arr(
        Json.obj(
          "title"       -> Json.fromString("Alert"),
          "description" -> Json.fromString(alert.message),
          "color"       -> Json.fromInt(0xff0000),
          "fields" -> Json.arr(
            Json.obj(
              "name"   -> Json.fromString("Severity"),
              "value"  -> Json.fromString(alert.severity),
              "inline" -> Json.fromBoolean(true)
            ),
            Json.obj(
              "name"   -> Json.fromString("Time"),
              "value"  -> Json.fromString(alert.timestamp.toString),
              "inline" -> Json.fromBoolean(true)
            )
          )
        )
      )
    )

    val request = Request[IO](
      method = POST,
      uri = Uri.unsafeFromString(webhookUrl)
    ).withEntity(payload)

    client
      .expect[String](request)
      .flatMap(response => logger.info(s"Discord notification sent successfully: $response"))
      .handleErrorWith(e => logger.error(e)(s"Failed to send Discord notification: ${e.getMessage}"))
  }
}
