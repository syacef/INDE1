package srvc_alert.presentation.subscriber

import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser._
import org.apache.kafka.clients.consumer.{ ConsumerConfig, KafkaConsumer }
import org.apache.kafka.common.serialization.StringDeserializer
import org.typelevel.log4cats.Logger
import srvc_alert.domain.entity.{ AlertEvent, EnvConfig, ParkingEvent }
import srvc_alert.domain.service.{ AlertEventPublisher, UserService }

import java.time.Duration
import java.util.{ Collections, Properties }
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ParkingEventConsumer(
  userService: UserService,
  alertEventPublisher: AlertEventPublisher
)(implicit logger: Logger[IO], ec: ExecutionContext) {

  def start(): IO[Unit] = {
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, EnvConfig.kafkaServers)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, EnvConfig.consumerGroupId)
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, EnvConfig.autoOffsetReset)
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, EnvConfig.enableAutoCommit.toString)

    val consumer = new KafkaConsumer[String, String](props)
    consumer.subscribe(Collections.singletonList(EnvConfig.kafkaParkingTopic))

    def loop(): IO[Unit] =
      IO.blocking(consumer.poll(Duration.ofMillis(500))).attempt.flatMap {
        case Left(err) =>
          logger.error(err)("Error while polling Kafka").flatMap { _ =>
            IO.sleep(1.second).flatMap(_ => loop())
          }

        case Right(records) =>
          records.asScala.toList.traverse_ { record =>
            logger.debug(s"Received message from partition ${record.partition()}, offset ${record.offset()}").flatMap {
              _ =>
                processMessage(record.key(), record.value())
            }
          }.flatMap(_ => loop())
      }

    loop()
  }

  private def processMessage(key: String, value: String): IO[Unit] =
    decode[ParkingEvent](value) match {
      case Right(event) => handleParkingEvent(event)
      case Left(error) =>
        for {
          _ <- logger.error(s"Failed to parse message with key '$key': $error")
          _ <- logger.debug(s"Raw message: $value")
        } yield ()
    }

  private def handleParkingEvent(event: ParkingEvent): IO[Unit] =
    logger
      .info(s"""
      Processing parking event: ${event.eventType}
      Vehicle: ${event.vehicle.licensePlate} (${event.vehicle.vehicleType}, ${event.vehicle.color})
      Parking: ${event.parking.parkingLotId}/${event.parking.parkingSpotId} (handicapped: ${event.parking.isSlotHandicapped})
      Timestamp: ${event.timestamp}
    """)
      .flatMap { _ =>
        event.duration.fold(IO.unit)(d => logger.info(s"Duration: $d minutes"))
      }
      .flatMap { _ =>
        IO.fromFuture(IO(userService.isUserHandicapped(event.vehicle.licensePlate))).handleErrorWith { err =>
          logger
            .error(s"Error checking user status: ${err.getMessage}")
            .flatMap { _ =>
              if (event.parking.isSlotHandicapped) handleViolation(event, "unknown_user") else IO.unit
            }
            .as(false)
        }
      }
      .flatMap { isHandicapped =>
        if (!isHandicapped && event.parking.isSlotHandicapped) handleViolation(event, "unauthorized_user")
        else IO.unit
      }

  private def handleViolation(event: ParkingEvent, violationType: String): IO[Unit] = {
    val alertEvent = AlertEvent(
      vehiclePlate = event.vehicle.licensePlate,
      spotId = event.parking.parkingSpotId,
      lotId = event.parking.parkingLotId,
      violationType = violationType,
      timestamp = event.timestamp
    )

    alertEventPublisher.publish(alertEvent) *> logger.info(s"$violationType violation alert published: $alertEvent")
  }
}
