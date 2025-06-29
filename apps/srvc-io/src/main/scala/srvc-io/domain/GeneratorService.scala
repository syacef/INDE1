package srvc_io.domain

import cats.effect.IO
import srvc_io.entities.{ EnvConfig, Parking, ParkingEvent, Vehicle }

import java.time.Instant
import scala.collection.mutable
import scala.util.Random

object GeneratorService {
  case class ActiveSession(entryEvent: ParkingEvent, scheduledExitTime: Instant, durationMs: Long)

  private val activeParkingSessions = mutable.Map[String, ActiveSession]()
  private val occupiedSpots         = mutable.Map[String, mutable.Set[Int]]()

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

      markSpotAsAvailable(
        entryEvent.parking.parkingLotId,
        entryEvent.parking.parkingSpotId.toInt,
        entryEvent.vehicle.licensePlate
      )

      exitEvent
    }.toSeq
  }

  def generateEntryEvent(): IO[Option[ParkingEvent]] = {
    val random = new Random

    val parking = findAvailableParking(random)
    if (parking.parkingSpotId == "0") {
      IO(None)
    } else {
      val now = Instant.now()

      val vehicle = Vehicle(
        licensePlate = generateUniqueLicensePlate(random),
        vehicleType = EnvConfig.vehicleTypes(random.nextInt(EnvConfig.vehicleTypes.length)),
        color = EnvConfig.vehicleColors(random.nextInt(EnvConfig.vehicleColors.length))
      )

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
      activeParkingSessions(vehicle.licensePlate) = session
      markSpotAsOccupied(parking.parkingLotId, parking.parkingSpotId.toInt)

      IO(Some(event))
    }
  }

  private def findAvailableParking(random: Random): Parking = {
    val attempts: Range.Inclusive = 1 to 50
    for (_ <- attempts) {
      val parkingLotIndex = random.nextInt(EnvConfig.parkingLots.length)
      val parkingLotId    = EnvConfig.parkingLots(parkingLotIndex)
      val parkingSpotId   = generateParkingSpotId(random, parkingLotIndex)

      if (isSpotAvailable(parkingLotId, parkingSpotId)) {
        val isHandicapped = EnvConfig.handicapSlots(parkingLotIndex).contains(parkingSpotId)
        return Parking(parkingLotId, parkingSpotId.toString, isSlotHandicapped = isHandicapped)
      }
    }

    findAnyAvailableSpot(random)
  }

  private def findAnyAvailableSpot(random: Random): Parking = {
    for ((parkingLotId, lotIndex) <- EnvConfig.parkingLots.zipWithIndex) {
      val occupiedSpotsInLot = occupiedSpots.getOrElse(parkingLotId, mutable.Set.empty[Int])
      val availableSlots     = EnvConfig.parkingSlots(lotIndex)
      val totalSlots         = availableSlots.length

      if (occupiedSpotsInLot.size < totalSlots) {
        for (slotId <- availableSlots)
          if (!occupiedSpotsInLot.contains(slotId)) {
            val isHandicapped = EnvConfig.handicapSlots(lotIndex).contains(slotId)
            return Parking(parkingLotId, slotId.toString, isSlotHandicapped = isHandicapped)
          }
      }
    }

    val parkingLotId = EnvConfig.parkingLots(random.nextInt(EnvConfig.parkingLots.length))
    Parking(parkingLotId, "0", false)
  }

  private def isSpotAvailable(parkingLotId: String, spotId: Int): Boolean =
    !occupiedSpots.getOrElse(parkingLotId, mutable.Set.empty[Int]).contains(spotId)

  private def markSpotAsOccupied(parkingLotId: String, spotId: Int): Unit = {
    val spots      = occupiedSpots.getOrElseUpdate(parkingLotId, mutable.Set.empty[Int])
    val _: Boolean = spots.add(spotId)
  }

  private def markSpotAsAvailable(parkingLotId: String, spotId: Int, licensePlate: String): Unit = {
    occupiedSpots.get(parkingLotId).foreach(_.remove(spotId))
    val _: Option[ActiveSession] = activeParkingSessions.remove(licensePlate)
  }

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

  private def generateParkingSpotId(random: Random, lotIndex: Int): Int = {
    val availableSlots = EnvConfig.parkingSlots(lotIndex)
    availableSlots(random.nextInt(availableSlots.length))
  }

  def getActiveSessions: Map[String, ParkingEvent] =
    activeParkingSessions.map { case (k, v) => k -> v.entryEvent }.toMap

  def getActiveSessionsWithSchedule: Map[String, ActiveSession] = activeParkingSessions.toMap

  def getOccupiedSpots: Map[String, Set[Int]] =
    occupiedSpots.map { case (lot, spots) => lot -> spots.toSet }.toMap

  def getAvailableSpotCount: Map[String, Int] =
    EnvConfig.parkingLots.zipWithIndex.map { case (lotId, lotIndex) =>
      val occupied   = occupiedSpots.getOrElse(lotId, mutable.Set.empty[Int]).size
      val totalSlots = EnvConfig.parkingSlots(lotIndex).length
      lotId -> (totalSlots - occupied)
    }.toMap

  def clearActiveSessions(): Unit = {
    activeParkingSessions.clear()
    occupiedSpots.clear()
  }
}
