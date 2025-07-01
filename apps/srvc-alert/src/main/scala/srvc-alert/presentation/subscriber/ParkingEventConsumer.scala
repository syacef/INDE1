package srvc_alert.presentation.subscriber

import cats.effect.IO
import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser._
import org.apache.kafka.clients.consumer.{ ConsumerConfig, KafkaConsumer }
import org.apache.kafka.common.serialization.StringDeserializer
import org.typelevel.log4cats.Logger
import srvc_alert.domain.entity.{ EnvConfig, ParkingEvent }
import srvc_alert.domain.service.{ AlertService, UserService }

import java.time.Duration
import java.util.{ Collections, Properties }
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ParkingEventConsumer(
  userService: UserService,
  alertService: AlertService
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
    consumer.subscribe(Collections.singletonList(EnvConfig.kafkaTopic))

    def loop(): IO[Unit] =
      IO.blocking(consumer.poll(Duration.ofMillis(500))).attempt.flatMap {
        case Left(err) =>
          for {
            _ <- logger.error(err)("Error while polling Kafka")
            _ <- IO.sleep(1.second)
            _ <- loop()
          } yield ()

        case Right(records) =>
          records.asScala.toList.traverse_ { record =>
            for {
              _ <- logger.debug(s"Received message from partition ${record.partition()}, offset ${record.offset()}")
              _ <- processMessage(record.key(), record.value())
            } yield ()
          } *> loop()
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
    for {
      _ <- logger.info(s"Processing parking event: ${event.eventType}")
      _ <- logger.info(s"Vehicle: ${event.vehicle.licensePlate} (${event.vehicle.vehicleType}, ${event.vehicle.color})")
      _ <- logger.info(
        s"Parking: ${event.parking.parkingLotId}/${event.parking.parkingSpotId} (handicapped: ${event.parking.isSlotHandicapped})"
      )
      _ <- logger.info(s"Timestamp: ${event.timestamp}")
      _ <- event.duration.fold(IO.unit)(d => logger.info(s"Duration: $d minutes"))

      isHandicapped <- IO.fromFuture(IO(userService.isUserHandicapped(event.vehicle.licensePlate))).handleErrorWith {
        err =>
          for {
            _ <- logger.error(s"Error checking user status: ${err.getMessage}")
            _ <- if (event.parking.isSlotHandicapped) handleUnknownUser(event) else IO.unit
          } yield false
      }

      _ <-
        if (!isHandicapped && event.parking.isSlotHandicapped)
          for {
            _ <- logger.warn("HANDICAPPED PARKING VIOLATION DETECTED!")
            _ <- handleParkingViolation(event)
          } yield ()
        else IO.unit
    } yield ()

  private def handleParkingViolation(event: ParkingEvent): IO[Unit] =
    IO.fromFuture(IO(alertService.reportHandicappedParkingViolation(event, "unauthorized_user"))) *>
      logger.info("Violation alert sent to Prometheus")

  private def handleUnknownUser(event: ParkingEvent): IO[Unit] =
    IO.fromFuture(IO(alertService.reportHandicappedParkingViolation(event, "unknown_user"))) *>
      logger.info("Unknown user violation alert sent to Prometheus")
}
