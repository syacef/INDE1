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
    val attempts = LazyList.continually {
      val parkingLotIndex = random.nextInt(EnvConfig.parkingLots.length)
      val parkingLotId    = EnvConfig.parkingLots(parkingLotIndex)
      val parkingSpotId   = generateParkingSpotId(random, parkingLotIndex)

      if (isSpotAvailable(parkingLotId, parkingSpotId)) {
        val isHandicapped = EnvConfig.handicapSlots(parkingLotIndex).contains(parkingSpotId)
        Some(Parking(parkingLotId, parkingSpotId.toString, isSlotHandicapped = isHandicapped))
      } else {
        None
      }
    }

    attempts
      .take(50)
      .collectFirst { case Some(parking) => parking }
      .getOrElse(findAnyAvailableSpot(random))
  }

  private def findAnyAvailableSpot(random: Random): Parking =
    EnvConfig.parkingLots.zipWithIndex.flatMap { case (parkingLotId, lotIndex) =>
      val occupiedSpotsInLot = occupiedSpots.getOrElse(parkingLotId, mutable.Set.empty[Int])
      val availableSlots     = EnvConfig.parkingSlots(lotIndex)

      if (occupiedSpotsInLot.size < availableSlots.length) {
        availableSlots
          .filterNot(occupiedSpotsInLot.contains)
          .headOption
          .map { slotId =>
            val isHandicapped = EnvConfig.handicapSlots(lotIndex).contains(slotId)
            Parking(parkingLotId, slotId.toString, isSlotHandicapped = isHandicapped)
          }
      } else {
        None
      }
    }.headOption.getOrElse {
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
    val usePattern = random.nextDouble() < EnvConfig.parkingPlateRandomProbability

    if (usePattern && EnvConfig.parkingPlatePattern.nonEmpty) {
      generateLicensePlateFromPattern(random)
    } else {
      generateRandomLicensePlate(random)
    }
  }

  private def generateLicensePlateFromPattern(random: Random): String = {
    val pattern = EnvConfig.parkingPlatePattern

    def parsePattern(chars: List[Char]): String = chars match {
      case Nil => ""
      case 'd' :: rest =>
        random.nextInt(10).toString + parsePattern(rest)
      case 'a' :: rest =>
        ('A' + random.nextInt(26)).toChar.toString + parsePattern(rest)
      case '[' :: rest =>
        val (choices, remaining) = rest.span(_ != ']')
        val choicesStr           = choices.mkString
        val selected = if (choicesStr.nonEmpty) choicesStr.charAt(random.nextInt(choicesStr.length)).toString else ""
        selected + parsePattern(remaining.drop(1))
      case c :: rest =>
        c.toString + parsePattern(rest)
    }

    parsePattern(pattern.toList)
  }

  private def generateRandomLicensePlate(random: Random): String = {
    val char1   = ('A' + random.nextInt(26)).toChar.toString
    val char2   = ('A' + random.nextInt(26)).toChar.toString
    val numbers = (random.nextInt(900) + 100).toString
    val char3   = ('A' + random.nextInt(26)).toChar.toString
    val char4   = ('A' + random.nextInt(26)).toChar.toString

    s"$char1$char2-$numbers-$char3$char4"
  }

  private def generateUniqueLicensePlate(random: Random): String = {
    val maxAttempts = 100

    LazyList
      .continually(generateLicensePlate(random))
      .take(maxAttempts)
      .find(!activeParkingSessions.contains(_))
      .getOrElse {
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
