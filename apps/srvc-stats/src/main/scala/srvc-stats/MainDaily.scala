/*
package srvc_stats

import io.minio.{ GetObjectArgs, ListObjectsArgs, MinioClient }
import java.util.zip.GZIPInputStream
import scala.io.Source
import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import scala.jdk.CollectionConverters._
import redis.clients.jedis.Jedis
import redis.clients.jedis.Protocol
import redis.clients.jedis.commands.ProtocolCommand
import redis.clients.jedis.util.SafeEncoder
import java.time.{ LocalDateTime, ZoneOffset }
import java.time.format.DateTimeFormatter
import scala.util.{ Failure, Success, Try }
import java.io.{ ByteArrayInputStream, StringWriter }
import java.nio.charset.StandardCharsets
import java.time.Instant

object MainDaily extends App {

  val now = LocalDateTime.now(ZoneOffset.UTC)

  // For tests
  // val now = LocalDateTime.of(2025, 7, 7, 0, 0)

  // We compute the previous day to get info
  val previousDay = now.minusDays(1)
  val year        = previousDay.getYear.toString
  val month       = f"${previousDay.getMonthValue}%02d"
  val day         = f"${previousDay.getDayOfMonth}%02d"
  val dateParam   = s"$year-$month-$day"

  val minioClient = MinioClient
    .builder()
    .endpoint("http://localhost:9000")
    .credentials("minio", "minio123")
    .build()

  val redis = new Jedis("localhost", 6379)

  val bucketName = "parking-events"
  val prefix     = s"topics/parking-event-topic/$year/$month/$day/"

  println(s"Processing data for the entire day: $dateParam")

  def extractTimestampFromPath(objectPath: String): Option[String] = {
    val pathPattern = raw"topics/parking-event-topic/(\d{4})/(\d{2})/(\d{2})/(\d{2})/(\d{2})/.*".r

    pathPattern.findFirstMatchIn(objectPath).map { m =>
      val year   = m.group(1)
      val month  = m.group(2)
      val day    = m.group(3)
      val hour   = m.group(4)
      val minute = m.group(5)
      s"$year-$month-${day}T$hour:$minute:00Z"
    }
  }

  def timestampToUnixMillis(timestamp: String): Long =
    Instant.parse(timestamp).toEpochMilli

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

  def calculateRealTimeOccupancy(allEvents: List[ParkingEvent], upToTimestamp: String): Map[String, Int] = {
    val sortedEvents = allEvents.sortBy(_.timestamp)

    val occupancyState = sortedEvents
      .takeWhile(_.timestamp <= upToTimestamp)
      .foldLeft(Map.empty[String, Set[String]]) { (state, event) =>
        val lotId        = event.parkingLotId
        val spotId       = event.parkingSpotId
        val currentSpots = state.getOrElse(lotId, Set.empty)

        event.eventType match {
          case "PARKING_ENTRY" =>
            state.updated(lotId, currentSpots + spotId)
          case "PARKING_EXIT" =>
            state.updated(lotId, currentSpots - spotId)
          case _ => state
        }
      }

    occupancyState.view.mapValues(_.size).toMap
  }

  def calculateOccupancy(events: List[ParkingEvent]): Map[String, Int] = {
    val entriesByLot = events
      .filter(_.eventType == "PARKING_ENTRY")
      .groupBy(_.parkingLotId)
      .view
      .mapValues(_.length)
      .toMap

    val exitsByLot = events
      .filter(_.eventType == "PARKING_EXIT")
      .groupBy(_.parkingLotId)
      .view
      .mapValues(_.length)
      .toMap

    val allLots = entriesByLot.keySet ++ exitsByLot.keySet

    allLots.map { lot =>
      val entries = entriesByLot.getOrElse(lot, 0)
      val exits   = exitsByLot.getOrElse(lot, 0)
      lot -> math.max(0, entries - exits)
    }.toMap
  }

  def calculateRevenueSimulation(occupancy: Map[String, Int], revenuePerHour: Double = 2.0): Double = {
    val totalOccupiedSpots = occupancy.values.sum
    totalOccupiedSpots * revenuePerHour * 24 // 24 hours in a day
  }

  def aggregateEventsForTimestamp(allEvents: List[ParkingEvent], timestamp: String): Map[String, Double] = {
    val eventsUpToTimestamp = allEvents.filter(_.timestamp <= timestamp)

    val entriesCount = eventsUpToTimestamp.count(_.eventType == "PARKING_ENTRY").toDouble
    val exitsCount   = eventsUpToTimestamp.count(_.eventType == "PARKING_EXIT").toDouble

    val occupancy         = calculateRealTimeOccupancy(allEvents, timestamp)
    val totalOccupancy    = occupancy.values.sum.toDouble
    val revenueSimulation = calculateRevenueSimulation(occupancy)

    val vehicleTypeCounts = eventsUpToTimestamp
      .groupBy(_.vehicleType)
      .view
      .mapValues(_.length.toDouble)
      .toMap

    val baseMetrics = Map(
      "entries"            -> entriesCount,
      "exits"              -> exitsCount,
      "total_occupancy"    -> totalOccupancy,
      "revenue_simulation" -> revenueSimulation
    )

    val vehicleTypeMetrics = vehicleTypeCounts.map { case (vehicleType, count) =>
      s"vehicle_type_$vehicleType" -> count
    }

    val occupancyMetrics = occupancy.map { case (lotId, count) =>
      s"occupancy_lot_$lotId" -> count.toDouble
    }

    baseMetrics ++ vehicleTypeMetrics ++ occupancyMetrics
  }

  def aggregateEvents(events: List[ParkingEvent]): AggregatedStats = {
    val entriesCount = events.count(_.eventType == "PARKING_ENTRY")
    val exitsCount   = events.count(_.eventType == "PARKING_EXIT")

    val lastTimestamp = events.map(_.timestamp).maxOption.getOrElse("")
    val occupancy = if (lastTimestamp.nonEmpty) {
      calculateRealTimeOccupancy(events, lastTimestamp)
    } else {
      Map.empty[String, Int]
    }

    val vehicleTypeCounts = events
      .groupBy(_.vehicleType)
      .view
      .mapValues(_.length)
      .toMap

    val revenueSimulation = calculateRevenueSimulation(occupancy)

    AggregatedStats(
      date = dateParam,
      nbrEntries = entriesCount,
      nbrExit = exitsCount,
      occupancy = occupancy,
      revenueSimulation = revenueSimulation,
      vehicleTypes = vehicleTypeCounts
    )
  }

  def statsToJson(stats: AggregatedStats): String = {
    val occupancyJson    = stats.occupancy.map { case (k, v) => s""""$k": $v""" }.mkString("{", ", ", "}")
    val vehicleTypesJson = stats.vehicleTypes.map { case (k, v) => s""""$k": $v""" }.mkString("{", ", ", "}")

    s"""{
      "date": "${stats.date}",
      "NbrEntries": ${stats.nbrEntries},
      "NbrExit": ${stats.nbrExit},
      "Occupancy": $occupancyJson,
      "RevenueSimulation": ${stats.revenueSimulation},
      "VehicleTypes": $vehicleTypesJson
    }"""
  }

  def readEventsFromFile(objectPath: String, mapper: ObjectMapper): List[ParkingEvent] =
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
            parseJsonToParkingEvent(jsonNode)
          }.toOption.flatten
        } else {
          None
        }
      }
    }.recover { case e: Exception =>
      println(s"Failed to process $objectPath: ${e.getMessage}")
      List.empty[ParkingEvent]
    }.getOrElse(List.empty)

  def groupEventsByTimestamp(
    filesWithTimestamps: List[FileWithTimestamp],
    mapper: ObjectMapper
  ): Map[String, List[ParkingEvent]] =
    filesWithTimestamps.foldLeft(Map.empty[String, List[ParkingEvent]]) { (acc, fileWithTimestamp) =>
      val events = readEventsFromFile(fileWithTimestamp.objectName, mapper)
      acc.updated(fileWithTimestamp.timestamp, events)
    }

  def createTimeSeriesData(
    eventsGroupedByTimestamp: Map[String, List[ParkingEvent]],
    allEvents: List[ParkingEvent]
  ): List[TimeSeriesData] = {
    val allMetrics = eventsGroupedByTimestamp.toList.sortBy(_._1).map { case (timestamp, _) =>
      timestamp -> aggregateEventsForTimestamp(allEvents, timestamp)
    }

    val allAttributes = allMetrics.flatMap(_._2.keys).toSet

    allAttributes.map { attribute =>
      val dataPoints = allMetrics.map { case (timestamp, metrics) =>
        TimeSeriesPoint(timestamp, metrics.getOrElse(attribute, 0.0))
      }
      TimeSeriesData(attribute, dataPoints)
    }.toList
  }

  def createTimeSeriesCommands(timeSeriesData: TimeSeriesData, dateParam: String): List[Try[Unit]] = {
    val redisKey = s"parking-events:daily:$dateParam:timeseries:${timeSeriesData.attribute}"

    val createCommand: Try[Unit] = Try {
      redis.sendCommand(TSCreateCommand, redisKey, "RETENTION", "0")
      println(s"Created time series for attribute '${timeSeriesData.attribute}' with key: $redisKey")
    }.recover { case _: Exception =>
      // Key might already exist, ignore
      ()
    }

    val addCommands: List[Try[Unit]] = timeSeriesData.dataPoints.map { point =>
      Try {
        val unixTimestamp = timestampToUnixMillis(point.timestamp)
        redis.sendCommand(TSAddCommand, redisKey, unixTimestamp.toString, point.value.toString)
      }
    }

    createCommand :: addCommands
  }

  def createDailyStatsOperation(aggregatedStats: AggregatedStats): Try[Unit] =
    Try {
      val statsJson = statsToJson(aggregatedStats)
      val redisKey  = s"parking-events:daily:$dateParam"

      redis.sendCommand(JsonSetCommand, redisKey, ".", statsJson)
      println(s"Uploaded aggregated daily stats to Redis with key: $redisKey")
      println(s"Stats: ${statsJson}")
    }

  def executeOperations[T](operations: List[Try[T]], operationType: String): Unit = {
    val (successes, failures) = operations.partition(_.isSuccess)
    val successCount          = successes.length
    val failureCount          = failures.length

    if (failureCount > 0) {
      println(s"$operationType: $successCount successful, $failureCount failed")
      failures.collect { case Failure(exception) =>
        println(s"  Error: ${exception.getMessage}")
      }
    } else {
      println(s"$operationType: All $successCount operations successful")
    }
  }

  object JsonSetCommand extends ProtocolCommand {
    override def getRaw: Array[Byte] = SafeEncoder.encode("JSON.SET")
  }

  object TSCreateCommand extends ProtocolCommand {
    override def getRaw: Array[Byte] = SafeEncoder.encode("TS.CREATE")
  }

  object TSAddCommand extends ProtocolCommand {
    override def getRaw: Array[Byte] = SafeEncoder.encode("TS.ADD")
  }

  def processFiles(): Unit = {
    val objectsResult = Try {
      minioClient.listObjects(
        ListObjectsArgs
          .builder()
          .bucket(bucketName)
          .prefix(prefix)
          .recursive(true)
          .build()
      )
    }

    objectsResult.fold(
      exception => println(s"Failed to list objects: ${exception.getMessage}"),
      objects => {
        val gzippedFiles = objects
          .iterator()
          .asScala
          .map(_.get())
          .filterNot(_.isDir)
          .map(_.objectName())
          .filter(_.endsWith(".json.gz"))
          .toList

        val mapper = new ObjectMapper()

        val filesWithTimestamps = gzippedFiles.flatMap { objectPath =>
          extractTimestampFromPath(objectPath).map { timestamp =>
            FileWithTimestamp(objectPath, timestamp)
          }
        }

        println(s"Total files with timestamps: ${filesWithTimestamps.length}")

        if (filesWithTimestamps.nonEmpty) {
          val eventsGroupedByTimestamp = groupEventsByTimestamp(filesWithTimestamps, mapper)

          val allEvents = eventsGroupedByTimestamp.values.flatten.toList

          println(s"Total events parsed: ${allEvents.length}")

          if (allEvents.nonEmpty) {
            val aggregatedStats = aggregateEvents(allEvents)

            val timeSeriesDataList = createTimeSeriesData(eventsGroupedByTimestamp, allEvents)

            val dailyStatsOperation = createDailyStatsOperation(aggregatedStats)
            executeOperations(List(dailyStatsOperation), "Daily stats storage")

            val redisTimeSeriesOperations = timeSeriesDataList.flatMap(createTimeSeriesCommands(_, dateParam))
            executeOperations(redisTimeSeriesOperations, "Redis Time Series operations")

            println(s"Total files processed: ${gzippedFiles.length}")
            println(s"Total time series attributes: ${timeSeriesDataList.length}")

            println(s"Final occupancy by lot: ${aggregatedStats.occupancy}")

          } else {
            println("No events found to aggregate")
          }
        } else {
          println("No files with valid timestamps found")
        }
      }
    )
  }

  processFiles()
  redis.close()
}
 */
