/*
package srvc_stats

import io.minio.{ GetObjectArgs, ListObjectsArgs, MinioClient }
import java.util.zip.GZIPInputStream
import scala.io.Source
import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.jdk.CollectionConverters._
import redis.clients.jedis.Jedis
import redis.clients.jedis.Protocol
import redis.clients.jedis.commands.ProtocolCommand
import redis.clients.jedis.util.SafeEncoder
import java.time.{ LocalDateTime, ZoneOffset }
import java.time.format.DateTimeFormatter
import scala.util.matching.Regex
import scala.util.Try

object MainWeekly extends App {

  val now = LocalDateTime.now(ZoneOffset.UTC)

  // To test
  // val now = LocalDateTime.of(2025, 7, 8, 1, 0)

  val endDate   = now.minusDays(1)
  val startDate = endDate.minusDays(6)
  val weekParam =
    s"${startDate.getYear}-${f"${startDate.getMonthValue}%02d"}-${f"${startDate.getDayOfMonth}%02d"}_to_${endDate.getYear}-${f"${endDate.getMonthValue}%02d"}-${f"${endDate.getDayOfMonth}%02d"}"

  val minioClient = MinioClient
    .builder()
    .endpoint("http://localhost:9000")
    .credentials("minio", "minio123")
    .build()

  val redis          = new Jedis("localhost", 6379)
  val bucketName     = "parking-events"
  val revenuePerHour = 2.0

  case class ParkingEvent(
    parkingSpotId: String,
    parkingLotId: String,
    isSlotHandicapped: Boolean,
    duration: Long,
    eventType: String,
    timestamp: String,
    licensePlate: String,
    color: String,
    vehicleType: String
  )

  case class EventWithDirectoryTime(
    event: ParkingEvent,
    directoryTimestamp: Long
  )

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  def parseJsonToParkingEvent(jsonNode: JsonNode): Option[ParkingEvent] =
    Try {
      val parking = jsonNode.get("parking")
      val vehicle = jsonNode.get("vehicle")
      ParkingEvent(
        parkingSpotId = parking.get("parkingSpotId").asText(),
        parkingLotId = parking.get("parkingLotId").asText(),
        isSlotHandicapped = parking.get("isSlotHandicapped").asBoolean(),
        duration = jsonNode.get("duration").asLong(),
        eventType = jsonNode.get("eventType").asText(),
        timestamp = jsonNode.get("timestamp").asText(),
        licensePlate = vehicle.get("licensePlate").asText(),
        color = vehicle.get("color").asText(),
        vehicleType = vehicle.get("vehicleType").asText()
      )
    }.toOption

  def extractTimestampFromPath(path: String): Option[Long] = {
    val pathPattern: Regex = """.*?(\d{4})/(\d{2})/(\d{2})/(\d{2})/(\d{2})/.*""".r

    path match {
      case pathPattern(year, month, day, hour, minute) =>
        Try {
          val dateTime = LocalDateTime.of(
            year.toInt,
            month.toInt,
            day.toInt,
            hour.toInt,
            minute.toInt
          )
          dateTime.toEpochSecond(ZoneOffset.UTC)
        }.toOption
      case _ => None
    }
  }

  def extractDateHour(timestamp: String): String =
    Try {
      val dateTime = LocalDateTime.parse(timestamp.replace("Z", ""))
      f"${dateTime.getYear}-${dateTime.getMonthValue}%02d-${dateTime.getDayOfMonth}%02d-${dateTime.getHour}%02d"
    }.getOrElse(timestamp.take(13))

  def dateHourToEpochSeconds(dateHour: String): Long =
    Try {
      val Array(year, month, day, hour) = dateHour.split("-")
      LocalDateTime
        .of(year.toInt, month.toInt, day.toInt, hour.toInt, 0)
        .toEpochSecond(ZoneOffset.UTC)
    }.getOrElse(0L)

  def calculateParkingDurations(events: List[ParkingEvent]): List[ParkingEvent] = {
    val (entries, exits) = events.partition(_.eventType == "PARKING_ENTRY")

    val entriesByKey = entries.groupBy(e => (e.licensePlate, e.parkingSpotId))
    val exitsByKey   = exits.groupBy(e => (e.licensePlate, e.parkingSpotId))

    exitsByKey.flatMap { case (key, exitEvents) =>
      entriesByKey.get(key).map { entryEvents =>
        exitEvents.zip(entryEvents).map { case (exit, entry) =>
          val entryTime      = LocalDateTime.parse(entry.timestamp.replace("Z", ""))
          val exitTime       = LocalDateTime.parse(exit.timestamp.replace("Z", ""))
          val actualDuration = java.time.Duration.between(entryTime, exitTime).toMinutes

          exit.copy(duration = actualDuration)
        }
      }
    }.flatten.toList
  }

  def calculateRevenue(durationMinutes: Long): Double =
    (durationMinutes * revenuePerHour) / 60.0

  val weekDates = (0 to 6).map(i => startDate.plusDays(i)).toList
  val prefixes = weekDates.map { date =>
    val year  = date.getYear.toString
    val month = f"${date.getMonthValue}%02d"
    val day   = f"${date.getDayOfMonth}%02d"
    s"topics/parking-event-topic/$year/$month/$day/"
  }

  val allGzippedFilesWithTimestamps: List[(String, Long)] = prefixes.flatMap { prefix =>
    Try {
      val objects = minioClient.listObjects(
        ListObjectsArgs
          .builder()
          .bucket(bucketName)
          .prefix(prefix)
          .recursive(true)
          .build()
      )
      objects
        .iterator()
        .asScala
        .map(_.get())
        .filterNot(_.isDir)
        .map(_.objectName())
        .filter(_.endsWith(".json.gz"))
        .flatMap { objectPath =>
          extractTimestampFromPath(objectPath).map(timestamp => (objectPath, timestamp))
        }
        .toList
    }.getOrElse(List.empty[(String, Long)])
  }

  val allEventsWithDirTimestamps: List[EventWithDirectoryTime] = allGzippedFilesWithTimestamps.flatMap {
    case (objectPath, dirTimestamp) =>
      Try {
        val stream = minioClient.getObject(
          GetObjectArgs
            .builder()
            .bucket(bucketName)
            .`object`(objectPath)
            .build()
        )
        val gzipStream = new GZIPInputStream(stream)
        val jsonLines  = Source.fromInputStream(gzipStream).getLines().toList
        jsonLines.flatMap { line =>
          if (line.trim.nonEmpty) {
            Try {
              val jsonNode = mapper.readTree(line)
              parseJsonToParkingEvent(jsonNode).map(event => EventWithDirectoryTime(event, dirTimestamp))
            }.toOption.flatten
          } else None
        }
      }.getOrElse(List.empty[EventWithDirectoryTime])
  }

  val allEvents                  = allEventsWithDirTimestamps.map(_.event)
  val eventsWithCorrectDurations = calculateParkingDurations(allEvents)

  object TSAddCommand extends ProtocolCommand {
    override def getRaw: Array[Byte] = SafeEncoder.encode("TS.ADD")
  }

  object TSCreateCommand extends ProtocolCommand {
    override def getRaw: Array[Byte] = SafeEncoder.encode("TS.CREATE")
  }

  object JsonSetCommand extends ProtocolCommand {
    override def getRaw: Array[Byte] = SafeEncoder.encode("JSON.SET")
  }

  def extractDateFromTimestamp(timestamp: Long): String = {
    val dateTime = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC)
    f"${dateTime.getYear}-${dateTime.getMonthValue}%02d-${dateTime.getDayOfMonth}%02d"
  }

  def dateToEpochSeconds(date: String): Long =
    LocalDateTime.parse(s"${date}T00:00:00").toEpochSecond(ZoneOffset.UTC)

  def createEntriesTimeseriesDaily(eventsWithTimestamps: List[EventWithDirectoryTime]): List[(Long, Long)] =
    eventsWithTimestamps
      .filter(_.event.eventType == "PARKING_ENTRY")
      .groupBy(ewt => extractDateFromTimestamp(ewt.directoryTimestamp))
      .toList
      .sortBy { case (date, _) => date }
      .map { case (date, events) =>
        val dayTimestamp = dateToEpochSeconds(date)
        (dayTimestamp, events.size.toLong)
      }

  def createExitsTimeseriesDaily(eventsWithTimestamps: List[EventWithDirectoryTime]): List[(Long, Long)] =
    eventsWithTimestamps
      .filter(_.event.eventType == "PARKING_EXIT")
      .groupBy(ewt => extractDateFromTimestamp(ewt.directoryTimestamp))
      .toList
      .sortBy { case (date, _) => date }
      .map { case (date, events) =>
        val dayTimestamp = dateToEpochSeconds(date)
        (dayTimestamp, events.size.toLong)
      }

  def createRevenueTimeseriesDaily(
    eventsWithTimestamps: List[EventWithDirectoryTime],
    correctedDurations: List[ParkingEvent]
  ): List[(Long, Double)] = {
    val durationMap = correctedDurations.map { e =>
      ((e.licensePlate, e.parkingSpotId, e.timestamp) -> e.duration)
    }.toMap

    eventsWithTimestamps
      .filter(_.event.eventType == "PARKING_EXIT")
      .groupBy(ewt => extractDateFromTimestamp(ewt.directoryTimestamp))
      .toList
      .sortBy { case (date, _) => date }
      .map { case (date, events) =>
        val dayTimestamp = dateToEpochSeconds(date)
        val totalRevenue = events.foldLeft(0.0) { case (acc, ewt) =>
          val key      = (ewt.event.licensePlate, ewt.event.parkingSpotId, ewt.event.timestamp)
          val duration = durationMap.getOrElse(key, ewt.event.duration)
          acc + calculateRevenue(duration)
        }
        (dayTimestamp, totalRevenue)
      }
  }

  def createAndPopulateTimeseries(key: String, data: List[(Long, Double)]): Unit =
    data.foreach { case (timestamp, value) =>
      Try {
        val timestampMs = timestamp * 1000
        redis.sendCommand(TSAddCommand, key, timestampMs.toString, value.toString)
      }.recover { case e: Exception =>
        println(s"Error adding data point to $key at ${timestamp * 1000}: ${e.getMessage}")
      }
    }

  val entriesTimeseries = createEntriesTimeseriesDaily(allEventsWithDirTimestamps)
  val exitsTimeseries   = createExitsTimeseriesDaily(allEventsWithDirTimestamps)
  val revenueTimeseries = createRevenueTimeseriesDaily(allEventsWithDirTimestamps, eventsWithCorrectDurations)

  val entriesKey = s"parking-stats:weekly:$weekParam:entries"
  createAndPopulateTimeseries(entriesKey, entriesTimeseries.map(t => (t._1, t._2.toDouble)))

  val exitsKey = s"parking-stats:weekly:$weekParam:exits"
  createAndPopulateTimeseries(exitsKey, exitsTimeseries.map(t => (t._1, t._2.toDouble)))

  val revenueKey = s"parking-stats:weekly:$weekParam:revenue"
  createAndPopulateTimeseries(revenueKey, revenueTimeseries)

  val avgSpentByVehicleTypeTimeseries: Map[String, List[(Long, Double)]] = allEventsWithDirTimestamps
    .filter(_.event.eventType == "PARKING_EXIT")
    .groupBy(ewt => extractDateFromTimestamp(ewt.directoryTimestamp))
    .map { case (date, events) =>
      val dayTimestamp = dateToEpochSeconds(date)
      val byType = events.groupBy(_.event.vehicleType).map { case (vehicleType, evs) =>
        val avgSpent = if (evs.nonEmpty) {
          evs.map(ewt => calculateRevenue(ewt.event.duration)).sum / evs.size
        } else 0.0
        vehicleType -> avgSpent
      }
      dayTimestamp -> byType
    }
    .toList
    .sortBy(_._1)
    .flatMap { case (dayTimestamp, typeMap) =>
      typeMap.map { case (vehicleType, avg) =>
        vehicleType -> (dayTimestamp, avg)
      }
    }
    .groupBy(_._1)
    .view
    .mapValues(_.map(_._2).sortBy(_._1))
    .toMap

  avgSpentByVehicleTypeTimeseries.foreach { case (vehicleType, data) =>
    val key = s"parking-stats:weekly:$weekParam:avgspent:$vehicleType"
    createAndPopulateTimeseries(key, data)
  }

  val revenueByVehicleType: Map[String, Double] = eventsWithCorrectDurations
    .filter(_.eventType == "PARKING_EXIT")
    .groupBy(_.vehicleType)
    .view
    .mapValues(events => events.map(ev => calculateRevenue(ev.duration)).sum)
    .toMap

  val revenueByVehicleTypeJson = mapper.writeValueAsString(revenueByVehicleType)
  val revenueByVehicleTypeKey  = s"parking-stats:weekly:$weekParam:revenue-by-type"
  redis.sendCommand(JsonSetCommand, revenueByVehicleTypeKey, ".", revenueByVehicleTypeJson)

  println(s"Processed ${allEventsWithDirTimestamps.size} events")
  println(s"Entries timeseries points: ${entriesTimeseries.size}")
  println(s"Exits timeseries points: ${exitsTimeseries.size}")
  println(s"Revenue timeseries points: ${revenueTimeseries.size}")
  println(s"Vehicle types: ${avgSpentByVehicleTypeTimeseries.keys.mkString(", ")}")

  println("\nEntries per day:")
  entriesTimeseries.foreach { case (timestamp, count) =>
    val date = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC)
    println(s"  $date: $count entries")
  }

  println("\nExits per day:")
  exitsTimeseries.foreach { case (timestamp, count) =>
    val date = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC)
    println(s"  $date: $count exits")
  }

  println("\nRevenue per day:")
  revenueTimeseries.foreach { case (timestamp, revenue) =>
    val date = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC)
    println(s"  $date: $revenue")
  }

  redis.close()
}
 */
