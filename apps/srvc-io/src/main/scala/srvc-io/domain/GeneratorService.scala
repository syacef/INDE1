package srvc_io.domain

import cats.effect.IO
import srvc_io.entities.{ EnvConfig, Parking, ParkingEvent, Vehicle }

import java.time.Instant
import scala.collection.mutable
import scala.util.Random
import scala.collection.immutable

object GeneratorService {
  case class ActiveSession(entryEvent: ParkingEvent, scheduledExitTime: Instant, durationMs: Long)

  private val activeParkingSessions = mutable.Map[String, ActiveSession]()
  private val occupiedSpots         = mutable.Map[String, mutable.Set[String]]()

  def cleanFinishedParkingSessions(): IO[Seq[ParkingEvent]] = IO {
    val now = Instant.now()
    val finishedSessions = activeParkingSessions.filter { case (_, session) =>
      now.isAfter(session.scheduledExitTime) || now.equals(session.scheduledExitTime)
    }

    finishedSessions.map { case (licensePlate, session) =>
      val entryEvent = session.entryEvent

      val exitEvent = ParkingEvent(
        eventType = "PARKING_EXIT",
        timestamp = now.toString,
        vehicle = entryEvent.vehicle,
        parking = entryEvent.parking,
        duration = Some(session.durationMs)
      )

      markSpotAsAvailable(entryEvent.parking.parkingLotId, entryEvent.parking.parkingSpotId)
      val _: Option[ActiveSession] = activeParkingSessions.remove(licensePlate)

      exitEvent
    }.toSeq
  }

  def generateEntryEvent(): IO[Option[ParkingEvent]] = {
    val random       = new Random
    val licensePlate = generateUniqueLicensePlate(random)

    val vehicle = Vehicle(
      licensePlate = licensePlate,
      vehicleType = EnvConfig.vehicleTypes(random.nextInt(EnvConfig.vehicleTypes.length)),
      color = EnvConfig.vehicleColors(random.nextInt(EnvConfig.vehicleColors.length))
    )

    val parking = generateAvailableParking(random)
    if (parking.parkingSpotId == "0") {
      IO(None)
    } else {
      val now = Instant.now()

      val durationMs = EnvConfig.minParkingDuration +
        random.nextLong(EnvConfig.maxParkingDuration - EnvConfig.minParkingDuration + 1)

      val scheduledExitTime = now.plusMillis(durationMs)

      val event = ParkingEvent(
        eventType = "PARKING_ENTRY",
        timestamp = now.toString,
        vehicle = vehicle,
        parking = parking,
        duration = Some(durationMs)
      )

      val session = ActiveSession(event, scheduledExitTime, durationMs)
      activeParkingSessions(licensePlate) = session
      markSpotAsOccupied(parking.parkingLotId, parking.parkingSpotId)

      IO(Some(event))
    }
  }

  private def generateAvailableParking(random: Random): Parking = {
    val attempts: Range.Inclusive = 1 to 50
    for (_ <- attempts) {
      val parkingLotId  = EnvConfig.parkingLots(random.nextInt(EnvConfig.parkingLots.length))
      val parkingSpotId = generateParkingSpotId(random)
      val zone          = EnvConfig.parkingZones(random.nextInt(EnvConfig.parkingZones.length))

      if (isSpotAvailable(parkingLotId, parkingSpotId)) {
        return Parking(parkingLotId, parkingSpotId, zone)
      }
    }

    findAnyAvailableSpot(random)
  }

  private def findAnyAvailableSpot(random: Random): Parking = {
    for (parkingLotId <- EnvConfig.parkingLots) {
      val occupiedSpotsInLot = occupiedSpots.getOrElse(parkingLotId, mutable.Set.empty[String])
      val totalSpots         = EnvConfig.maxSpotsPerLot

      if (occupiedSpotsInLot.size < totalSpots) {
        val sections: immutable.NumericRange.Inclusive[Char] = 'A' to 'F'
        for (section <- sections) {
          val numbers: Range.Inclusive = 1 to (totalSpots / 6 + 1)
          for (number <- numbers) {
            val spotId = s"${section.toString}${number.toString}"
            if (!occupiedSpotsInLot.contains(spotId)) {
              val zone = EnvConfig.parkingZones(random.nextInt(EnvConfig.parkingZones.length))
              return Parking(parkingLotId, spotId, zone)
            }
          }
        }
      }
    }

    val parkingLotId = EnvConfig.parkingLots(random.nextInt(EnvConfig.parkingLots.length))
    val zone         = EnvConfig.parkingZones(random.nextInt(EnvConfig.parkingZones.length))
    Parking(parkingLotId, "0", zone)
  }

  private def isSpotAvailable(parkingLotId: String, spotId: String): Boolean =
    !occupiedSpots.getOrElse(parkingLotId, mutable.Set.empty[String]).contains(spotId)

  private def markSpotAsOccupied(parkingLotId: String, spotId: String): Unit = {
    val spots      = occupiedSpots.getOrElseUpdate(parkingLotId, mutable.Set.empty[String])
    val _: Boolean = spots.add(spotId)
  }

  private def markSpotAsAvailable(parkingLotId: String, spotId: String): Unit =
    occupiedSpots.get(parkingLotId).foreach(_.remove(spotId))

  private def generateLicensePlate(random: Random): String = {
    val char1   = ('A' + random.nextInt(26)).toChar.toString
    val char2   = ('A' + random.nextInt(26)).toChar.toString
    val numbers = (random.nextInt(900) + 100).toString
    val char3   = ('A' + random.nextInt(26)).toChar.toString
    val char4   = ('A' + random.nextInt(26)).toChar.toString

    s"$char1$char2-$numbers-$char3$char4"
  }

  private def generateUniqueLicensePlate(random: Random): String = {
    val maxAttempts = 100

    val uniquePlate = (for {
      attempt <- 0 until maxAttempts
      licensePlate = generateLicensePlate(random)
      if !activeParkingSessions.contains(licensePlate)
    } yield licensePlate).headOption

    uniquePlate.getOrElse {
      val basePlate = generateLicensePlate(random)
      val suffix    = f"${random.nextInt(999)}%03d"
      basePlate.take(6) + suffix
    }
  }

  private def generateParkingSpotId(random: Random): String = {
    val section = ('A' + random.nextInt(6)).toChar
    val number  = random.nextInt(EnvConfig.maxSpotsPerLot) + 1
    s"${section.toString}${number.toString}"
  }

  def getActiveSessions: Map[String, ParkingEvent] =
    activeParkingSessions.map { case (k, v) => k -> v.entryEvent }.toMap

  def getActiveSessionsWithSchedule: Map[String, ActiveSession] = activeParkingSessions.toMap

  def getOccupiedSpots: Map[String, Set[String]] =
    occupiedSpots.map { case (lot, spots) => lot -> spots.toSet }.toMap

  def getAvailableSpotCount: Map[String, Int] =
    EnvConfig.parkingLots.map { lotId =>
      val occupied = occupiedSpots.getOrElse(lotId, mutable.Set.empty[String]).size
      lotId -> (EnvConfig.maxSpotsPerLot - occupied)
    }.toMap

  def clearActiveSessions(): Unit = {
    activeParkingSessions.clear()
    occupiedSpots.clear()
  }
}
