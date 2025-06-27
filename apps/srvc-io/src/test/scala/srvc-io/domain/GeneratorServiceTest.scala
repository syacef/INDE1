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
    val result = GeneratorService.createParkingEvent().unsafeRunSync()

    result shouldBe defined
    result.get.eventType should be("PARKING_ENTRY")
    result.get.vehicle.licensePlate should not be empty
    result.get.parking.parkingSpotId should not be "0"
    result.get.duration shouldBe defined
  }

  it should "generate unique license plates" in {
    val results = (1 to 10).map(_ => GeneratorService.createParkingEvent().unsafeRunSync())

    val licensePlates = results.flatten.map(_.vehicle.licensePlate)
    licensePlates should have size 10
    licensePlates.toSet should have size 10
  }

  it should "track active parking sessions" in {
    val result = GeneratorService.createParkingEvent().unsafeRunSync()

    result shouldBe defined
    val licensePlate = result.get.vehicle.licensePlate

    val activeSessions = GeneratorService.getActiveSessions
    activeSessions should contain key licensePlate
    activeSessions(licensePlate).eventType should be("PARKING_ENTRY")
  }

  it should "mark spots as occupied when creating parking events" in {
    val result = GeneratorService.createParkingEvent().unsafeRunSync()

    result shouldBe defined
    val parking = result.get.parking

    val occupiedSpots = GeneratorService.getOccupiedSpots
    occupiedSpots should contain key parking.parkingLotId
    occupiedSpots(parking.parkingLotId) should contain(parking.parkingSpotId)
  }

  it should "clean finished parking sessions correctly" in {
    val entryResult = GeneratorService.createParkingEvent().unsafeRunSync()
    entryResult shouldBe defined

    val entryEvent   = entryResult.get
    val licensePlate = entryEvent.vehicle.licensePlate

    val exitEvents = GeneratorService.cleanFinishedParkingSessions().unsafeRunSync()
    exitEvents should have size 0

    val activeSessions = GeneratorService.getActiveSessions
    activeSessions should contain key licensePlate
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
    val result        = GeneratorService.createParkingEvent().unsafeRunSync()

    result shouldBe defined
    val parking = result.get.parking

    val updatedCounts = GeneratorService.getAvailableSpotCount
    updatedCounts(parking.parkingLotId) should be < initialCounts(parking.parkingLotId)
  }

  it should "generate valid vehicle properties" in {
    val result = GeneratorService.createParkingEvent().unsafeRunSync()

    result shouldBe defined
    val vehicle = result.get.vehicle

    vehicle.licensePlate should fullyMatch regex "[A-Z]{2}-[0-9]{3}-[A-Z]{2}.*".r
    EnvConfig.vehicleTypes should contain(vehicle.vehicleType)
    EnvConfig.vehicleColors should contain(vehicle.color)
  }

  it should "generate valid parking properties" in {
    val result = GeneratorService.createParkingEvent().unsafeRunSync()

    result shouldBe defined
    val parking = result.get.parking

    EnvConfig.parkingLots should contain(parking.parkingLotId)
    EnvConfig.parkingZones should contain(parking.zone)
    parking.parkingSpotId should fullyMatch regex "[A-F][0-9]+".r
  }

  it should "handle clearing active sessions" in {
    GeneratorService.createParkingEvent().unsafeRunSync()

    val activeBefore = GeneratorService.getActiveSessions
    activeBefore should not be empty

    GeneratorService.clearActiveSessions()

    val activeAfter   = GeneratorService.getActiveSessions
    val occupiedAfter = GeneratorService.getOccupiedSpots

    activeAfter shouldBe empty
    occupiedAfter.values.foreach(_ shouldBe empty)
  }

  it should "maintain session schedule information" in {
    val result = GeneratorService.createParkingEvent().unsafeRunSync()

    result shouldBe defined
    val licensePlate = result.get.vehicle.licensePlate

    val sessionsWithSchedule = GeneratorService.getActiveSessionsWithSchedule
    sessionsWithSchedule should contain key licensePlate

    val session = sessionsWithSchedule(licensePlate)
    session.entryEvent should be(result.get)
    session.durationMs should be(result.get.duration.get)
  }

  it should "handle multiple concurrent parking events" in {
    val results = (1 to 5).map(_ => GeneratorService.createParkingEvent().unsafeRunSync())

    results.flatten should have size 5

    val activeSessions = GeneratorService.getActiveSessions
    activeSessions should have size 5

    val occupiedSpots = GeneratorService.getOccupiedSpots
    val totalOccupied = occupiedSpots.values.map(_.size).sum
    totalOccupied should be(5)
  }

  it should "prevent double booking of the same spot" in {
    val result1 = GeneratorService.createParkingEvent().unsafeRunSync()
    val result2 = GeneratorService.createParkingEvent().unsafeRunSync()

    result1 shouldBe defined
    result2 shouldBe defined

    val spot1 = result1.get.parking
    val spot2 = result2.get.parking

    (spot1.parkingLotId != spot2.parkingLotId) || (spot1.parkingSpotId != spot2.parkingSpotId) shouldBe true
  }
}
