package srvc_io.domain

import cats.effect.unsafe.implicits.global
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import srvc_io.entities.EnvConfig

class GeneratorServiceSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    GeneratorService.clearActiveSessions()
  }

  "GeneratorService" should "create a parking event when spots are available" in {
    val result = GeneratorService.generateEntryEvent().unsafeRunSync()

    result shouldBe defined
    result.fold(fail("Expected Some but got None")) { event =>
      event.eventType should be("PARKING_ENTRY")
      event.vehicle.licensePlate should not be empty
      event.parking.parkingSpotId should not be "0"
      event.duration shouldBe defined
    }
  }

  it should "generate unique license plates" in {
    val results = (1 to 10).map(_ => GeneratorService.generateEntryEvent().unsafeRunSync())

    val licensePlates = results.flatten.map(_.vehicle.licensePlate)
    licensePlates should have size 10
    licensePlates.toSet should have size 10
  }

  it should "track active parking sessions" in {
    val result = GeneratorService.generateEntryEvent().unsafeRunSync()

    result shouldBe defined
    result.fold(fail("Expected Some but got None")) { event =>
      val licensePlate = event.vehicle.licensePlate

      val activeSessions = GeneratorService.getActiveSessions
      activeSessions should contain key licensePlate
      activeSessions(licensePlate).eventType should be("PARKING_ENTRY")
    }
  }

  it should "mark spots as occupied when creating parking events" in {
    val result = GeneratorService.generateEntryEvent().unsafeRunSync()

    result shouldBe defined
    result.fold(fail("Expected Some but got None")) { event =>
      val parking = event.parking

      val occupiedSpots = GeneratorService.getOccupiedSpots
      occupiedSpots should contain key parking.parkingLotId
      occupiedSpots(parking.parkingLotId) should contain(parking.parkingSpotId)
    }
  }

  it should "clean finished parking sessions correctly" in {
    val entryResult = GeneratorService.generateEntryEvent().unsafeRunSync()
    entryResult shouldBe defined

    entryResult.fold(fail("Expected Some but got None")) { entryEvent =>
      val licensePlate = entryEvent.vehicle.licensePlate

      val exitEvents = GeneratorService.cleanFinishedParkingSessions().unsafeRunSync()
      exitEvents should have size 0

      val activeSessions = GeneratorService.getActiveSessions
      activeSessions should contain key licensePlate
    }
  }

  it should "generate exit events with correct structure" in {
    val exitEvents = GeneratorService.cleanFinishedParkingSessions().unsafeRunSync()

    exitEvents.foreach { exitEvent =>
      exitEvent.eventType should be("PARKING_EXIT")
      exitEvent.timestamp should not be empty
      exitEvent.duration shouldBe defined
    }
  }

  it should "track available spot counts correctly" in {
    val initialCounts = GeneratorService.getAvailableSpotCount
    val result        = GeneratorService.generateEntryEvent().unsafeRunSync()

    result shouldBe defined
    result.fold(fail("Expected Some but got None")) { event =>
      val parking = event.parking

      val updatedCounts = GeneratorService.getAvailableSpotCount
      updatedCounts(parking.parkingLotId) should be < initialCounts(parking.parkingLotId)
    }
  }

  it should "generate valid vehicle properties" in {
    val result = GeneratorService.generateEntryEvent().unsafeRunSync()

    result shouldBe defined
    result.fold(fail("Expected Some but got None")) { event =>
      val vehicle = event.vehicle

      vehicle.licensePlate should fullyMatch regex "[A-Z]{2}-[0-9]{3}-[A-Z]{2}.*".r
      EnvConfig.vehicleTypes should contain(vehicle.vehicleType)
      EnvConfig.vehicleColors should contain(vehicle.color)
    }
  }

  it should "generate valid parking properties" in {
    val result = GeneratorService.generateEntryEvent().unsafeRunSync()

    result shouldBe defined
    result.fold(fail("Expected Some but got None")) { event =>
      val parking = event.parking

      EnvConfig.parkingLots should contain(parking.parkingLotId)
      EnvConfig.parkingZones should contain(parking.zone)
      parking.parkingSpotId should fullyMatch regex "[A-F][0-9]+".r
    }
  }

  it should "handle clearing active sessions" in {
    GeneratorService.generateEntryEvent().unsafeRunSync()

    val activeBefore = GeneratorService.getActiveSessions
    activeBefore should not be empty

    GeneratorService.clearActiveSessions()

    val activeAfter   = GeneratorService.getActiveSessions
    val occupiedAfter = GeneratorService.getOccupiedSpots

    activeAfter shouldBe empty
    occupiedAfter.values.foreach(_ shouldBe empty)
  }

  it should "maintain session schedule information" in {
    val result = GeneratorService.generateEntryEvent().unsafeRunSync()

    result shouldBe defined
    result.fold(fail("Expected Some but got None")) { event =>
      val licensePlate = event.vehicle.licensePlate

      val sessionsWithSchedule = GeneratorService.getActiveSessionsWithSchedule
      sessionsWithSchedule should contain key licensePlate

      val session = sessionsWithSchedule(licensePlate)
      session.entryEvent should be(event)
      event.duration.fold(fail("Expected duration to be defined")) { duration =>
        session.durationMs should be(duration)
      }
    }
  }

  it should "handle multiple concurrent parking events" in {
    val results = (1 to 5).map(_ => GeneratorService.generateEntryEvent().unsafeRunSync())

    results.flatten should have size 5

    val activeSessions = GeneratorService.getActiveSessions
    activeSessions should have size 5

    val occupiedSpots = GeneratorService.getOccupiedSpots
    val totalOccupied = occupiedSpots.values.map(_.size).sum
    totalOccupied should be(5)
  }

  it should "prevent double booking of the same spot" in {
    val result1 = GeneratorService.generateEntryEvent().unsafeRunSync()
    val result2 = GeneratorService.generateEntryEvent().unsafeRunSync()

    result1 shouldBe defined
    result2 shouldBe defined

    (result1, result2) match {
      case (Some(event1), Some(event2)) =>
        val spot1 = event1.parking
        val spot2 = event2.parking

        (spot1.parkingLotId != spot2.parkingLotId) || (spot1.parkingSpotId != spot2.parkingSpotId) shouldBe true
      case _ =>
        fail("Expected both events to be defined")
    }
  }
}
