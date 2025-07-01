package srvc_alert.domain.service

import cats.effect.IO
import io.prometheus.client.{ Counter, Gauge }
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import srvc_alert.domain.entity.ParkingEvent

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class AlertService(implicit ec: ExecutionContext) {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val illegalDisabledParkingGauge = Gauge
    .build()
    .name("illegal_disabled_parking")
    .help("Active illegal parking violations in disabled spots")
    .labelNames("vehicle_plate", "spot_id", "lot_id", "violation_type", "timestamp")
    .register()

  private val disabledParkingViolationsTotal = Counter
    .build()
    .name("disabled_parking_violations_total")
    .help("Total number of disabled parking violations detected")
    .labelNames("violation_type", "lot_id", "spot_id", "hour_of_day", "day_of_week")
    .register()

  def reportHandicappedParkingViolation(
    event: ParkingEvent,
    violationType: String = "unauthorized_user"
  ): Future[Unit] =
    Future {
      Try {
        val timestamp = event.timestamp.toString
        val hourOfDay = extractHourFromTimestamp(event.timestamp.toString)
        val dayOfWeek = extractDayOfWeekFromTimestamp(event.timestamp.toString)

        illegalDisabledParkingGauge
          .labels(
            event.vehicle.licensePlate,
            event.parking.parkingSpotId,
            event.parking.parkingLotId,
            event.vehicle.vehicleType,
            event.vehicle.color,
            violationType,
            timestamp
          )
          .set(1)

        disabledParkingViolationsTotal
          .labels(
            violationType,
            event.parking.parkingLotId,
            event.parking.parkingSpotId,
            event.vehicle.vehicleType,
            event.vehicle.color,
            hourOfDay,
            dayOfWeek
          )
          .inc()

        logger.info(s"Prometheus alert generated for handicapped parking violation:")
        logger.info(
          s"  Metric: illegal_disabled_parking{vehicle_plate=\"${event.vehicle.licensePlate}\", spot_id=\"${event.parking.parkingSpotId}\", lot_id=\"${event.parking.parkingLotId}\"} 1"
        )
        logger.info(s"  Violation type: $violationType")

      } match {
        case Success(_) =>
          logger.debug("Successfully reported handicapped parking violation to Prometheus")
        case Failure(exception) =>
          logger.error(exception)("Failed to report handicapped parking violation to Prometheus")
      }
    }

  private def extractHourFromTimestamp(timestamp: String): String =
    Try {
      java.time.LocalDateTime.parse(timestamp).getHour.toString
    }.getOrElse("unknown")

  private def extractDayOfWeekFromTimestamp(timestamp: String): String =
    Try {
      java.time.LocalDateTime.parse(timestamp).getDayOfWeek.toString.toLowerCase
    }.getOrElse("unknown")

  def clearAllActiveViolations(): Future[Unit] =
    Future {
      Try {
        illegalDisabledParkingGauge.clear()
        logger.info("Cleared all active violation metrics")
      } match {
        case Success(_) =>
          logger.debug("Successfully cleared all active violations")
        case Failure(exception) =>
          logger.error("Failed to clear active violations")
      }
    }
}
