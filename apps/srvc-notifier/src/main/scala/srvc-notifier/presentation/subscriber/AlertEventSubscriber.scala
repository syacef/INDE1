package srvc_notifier.presentation.subscriber

import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser._
import org.apache.kafka.clients.consumer.{ ConsumerConfig, KafkaConsumer }
import org.apache.kafka.common.serialization.StringDeserializer
import org.typelevel.log4cats.Logger
import srvc_notifier.domain.entity.{ AlertEvent, AlertNotification, EnvConfig }

import java.time.Duration
import java.util.{ Collections, Properties }
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class AlertEventConsumer()(implicit logger: Logger[IO], ec: ExecutionContext) {

  def start(): IO[Unit] = {
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, EnvConfig.kafkaServers)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, EnvConfig.consumerGroupId)
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, EnvConfig.autoOffsetReset)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, EnvConfig.enableAutoCommit.toString)

    val consumer = new KafkaConsumer[String, String](props)
    consumer.subscribe(Collections.singletonList(EnvConfig.kafkaAlertTopic))

    def loop(): IO[Unit] =
      IO.blocking(consumer.poll(Duration.ofMillis(500))).attempt.flatMap {
        case Left(err) =>
          logger.error(err)("Error while polling Kafka").flatMap { _ =>
            IO.sleep(1.second).flatMap(_ => loop())
          }
        case Right(records) =>
          records.asScala.toList.traverse_ { record =>
            logger.debug(s"Received message from partition ${record.partition()}, offset ${record.offset()}").flatMap {
              _ => processMessage(record.key(), record.value())
            }
          }.flatMap(_ => loop())
      }

    loop()
  }

  private def processMessage(key: String, value: String): IO[Unit] =
    decode[AlertEvent](value) match {
      case Right(event) => handleAlertEvent(event)
      case Left(error)  => logger.error(s"Failed to parse message with key '$key': $error")
    }

  private def handleAlertEvent(event: AlertEvent): IO[Unit] =
    logger
      .info(s"""
        Processing parking event: ${event.violationType}
        Vehicle: ${event.vehiclePlate}
        Parking: ${event.lotId}/${event.spotId}
        Violation Type: ${event.violationType}
        Timestamp: ${event.timestamp}
      """)
      .flatMap { _ =>
        val alert = createAlertNotification(event)

        discordNotifier.notify(alert).handleErrorWith { err =>
          logger.error(s"Failed to send Discord notification: ${err.getMessage}")
        }
      }

  private def createAlertNotification(event: AlertEvent): AlertNotification = {
    val severity = determineSeverity(event.violationType)
    val message =
      s"Parking violation detected: ${event.violationType} - Vehicle ${event.vehiclePlate} at ${event.lotId}/${event.spotId}"

    AlertNotification(
      message = message,
      severity = severity,
      timestamp = event.timestamp
    )
  }

  private def determineSeverity(violationType: String): String =
    violationType.toLowerCase match {
      case "unauthorized_user"     => "HIGH"
      case "unknown_user"          => "MEDIUM"
      case "handicapped_violation" => "CRITICAL"
      case _                       => "MEDIUM"
    }
}
