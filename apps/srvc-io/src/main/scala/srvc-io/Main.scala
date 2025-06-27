package srvc_io

import cats.effect._
import cats.syntax.all._
import fs2.kafka._
import io.circe.generic.auto._
import io.circe.syntax._
import org.slf4j.MDC
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import retry.RetryDetails._
import retry._
import srvc_io.domain.GeneratorService
import srvc_io.entities.{ EnvConfig, ParkingEvent }

import scala.concurrent.duration.{ DurationInt, DurationLong }

object Main extends IOApp {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val producerSettings: ProducerSettings[IO, String, String] =
    ProducerSettings[IO, String, String]
      .withBootstrapServers(EnvConfig.kafkaServers)

  val retryPolicy = RetryPolicies.limitRetries[IO](EnvConfig.maxRetries) join
    RetryPolicies.constantDelay[IO](EnvConfig.backoffMs.millis)

  def sendEvent(event: ParkingEvent, producer: KafkaProducer[IO, String, String]): IO[Unit] = {
    val json    = event.asJson.noSpaces
    val record  = ProducerRecord(EnvConfig.kafkaTopic, event.vehicle.licensePlate, json)
    val message = ProducerRecords.one(record)

    IO {
      MDC.put("event_type", event.eventType)
      MDC.put("license_plate", event.vehicle.licensePlate)
      MDC.put("parking_lot", event.parking.parkingLotId)
      MDC.put("parking_spot", event.parking.parkingSpotId)
    } *>
      retryingOnAllErrors[Unit](
        policy = retryPolicy,
        onError = (e: Throwable, details: RetryDetails) =>
          logger.warn(s"Retry ${details match {
              case WillDelayAndRetry(_, retriesSoFar, _) => retriesSoFar
              case _                                     => "unknown"
            }} after error: ${e.getMessage}")
      ) {
        producer.produce(message).flatten.void
      } <* IO(MDC.clear())
  }

  def printConfiguration(): IO[Unit] =
    IO.println(
      s"""
         |=== Parking Event Generator Configuration ===
         |Events per second: ${EnvConfig.eventsPerSecond}
         |Kafka topic: ${EnvConfig.kafkaTopic}
         |Kafka servers: ${EnvConfig.kafkaServers}
         |Parking lots: ${EnvConfig.parkingLots.mkString(", ")}
         |Vehicle colors: ${EnvConfig.vehicleColors.mkString(", ")}
         |Vehicle types: ${EnvConfig.vehicleTypes.mkString(", ")}
         |Parking zones: ${EnvConfig.parkingZones.mkString(", ")}
         |Parking duration range: ${EnvConfig.minParkingDuration / 1000}-${EnvConfig.maxParkingDuration / 1000} s
         |Max spots per lot: ${EnvConfig.maxSpotsPerLot}
         |Total spots: ${EnvConfig.parkingLots.length * EnvConfig.maxSpotsPerLot}
         |Max retries: ${EnvConfig.maxRetries}
         |Backoff: ${EnvConfig.backoffMs}ms
         |=============================================
         |""".stripMargin
    )

  def logMetrics: IO[Unit] = {
    val activeSessions = GeneratorService.getActiveSessions
    val availableSpots = GeneratorService.getAvailableSpotCount

    IO {
      val metricsLogger = org.slf4j.LoggerFactory.getLogger("metrics")
      MDC.put("metric_type", "parking_status")
      MDC.put("active_sessions", activeSessions.size.toString)
      MDC.put("total_available_spots", availableSpots.values.sum.toString)

      availableSpots.foreach { case (lot, count) =>
        MDC.put(s"available_spots_$lot", count.toString)
      }

      metricsLogger.info("Parking status update")
      MDC.clear()
    }
  }

  private def logEvent(event: ParkingEvent): IO[Unit] = IO {
    MDC.put("event_type", event.eventType)
    MDC.put("license_plate", event.vehicle.licensePlate)
    MDC.put("parking_lot", event.parking.parkingLotId)
    MDC.put("parking_spot", event.parking.parkingSpotId)
    MDC.put("vehicle_type", event.vehicle.vehicleType)
    MDC.put("vehicle_color", event.vehicle.color)
  } *> logger.debug(
    s"Generated ${event.eventType} for ${event.vehicle.licensePlate} at ${event.parking.parkingLotId}:${event.parking.parkingSpotId}"
  )

  override def run(args: List[String]): IO[ExitCode] =
    KafkaProducer.resource(producerSettings).use { producer =>
      Ref.of[IO, Int](0).flatMap { counterRef =>
        def loop: IO[Unit] = for {
          _             <- IO.sleep((1000 / EnvConfig.eventsPerSecond).millis)
          entryEventOpt <- GeneratorService.generateEntryEvent()
          exitEvents    <- GeneratorService.cleanFinishedParkingSessions()
          _ <- entryEventOpt match {
            case Some(entryEvent) => logEvent(entryEvent) *> sendEvent(entryEvent, producer)
            case None             => logger.warn("No parking spot available - could not create parking event")
          }
          _ <- exitEvents.traverse { exitEvent =>
            logEvent(exitEvent) *> sendEvent(exitEvent, producer)
          }
          _ <- counterRef.updateAndGet(_ + 1).flatMap { count =>
            if (count % 100 == 0) logMetrics else IO.unit
          }
        } yield ()

        printConfiguration() *>
          logger.debug("Starting parking event generator...") *>
          fs2.Stream.repeatEval(loop).compile.drain.as(ExitCode.Success)
      }
    }
}
