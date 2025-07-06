package srvc_stats

import io.minio.{MinioClient, ListObjectsArgs, GetObjectArgs}
import java.util.zip.GZIPInputStream
import scala.io.Source
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import scala.jdk.CollectionConverters._
import redis.clients.jedis.Jedis
import redis.clients.jedis.Protocol
import redis.clients.jedis.commands.ProtocolCommand
import redis.clients.jedis.util.SafeEncoder
import java.time.{LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter

object MainWeekly extends App {

  // val now = LocalDateTime.now(ZoneOffset.UTC)

  // Set back the value above afterwards 
  val now = LocalDateTime.of(2025, 7, 10, 1, 0)

  // We compute the previous week to get info (7 days ago until yesterday)
  val endDate = now.minusDays(1)
  val startDate = endDate.minusDays(6)
  
  val weekParam = s"${startDate.getYear}-${f"${startDate.getMonthValue}%02d"}-${f"${startDate.getDayOfMonth}%02d"}_to_${endDate.getYear}-${f"${endDate.getMonthValue}%02d"}-${f"${endDate.getDayOfMonth}%02d"}"

  val minioClient = MinioClient.builder()
    .endpoint("http://localhost:9000")
    .credentials("minio", "minio123")
    .build()

  val redis = new Jedis("localhost", 6379)

  val bucketName = "parking-events"
  
  println(s"Processing data for the entire week: $weekParam")
  
  // Generate all dates in the week range
  val weekDates = (0 to 6).map(i => startDate.plusDays(i)).toList
  println(s"Processing dates: ${weekDates.map(d => s"${d.getYear}-${f"${d.getMonthValue}%02d"}-${f"${d.getDayOfMonth}%02d"}").mkString(", ")}")
  
  // Get all prefixes for the week
  val prefixes = weekDates.map { date =>
    val year = date.getYear.toString
    val month = f"${date.getMonthValue}%02d"
    val day = f"${date.getDayOfMonth}%02d"
    s"topics/parking-event-topic/$year/$month/$day/"
  }
  
  // Get all JSONs for the entire week (all days)
  val allGzippedFiles = prefixes.flatMap { prefix =>
    try {
      val objects = minioClient.listObjects(
        ListObjectsArgs.builder()
          .bucket(bucketName)
          .prefix(prefix)
          .recursive(true)
          .build()
      )

      val files = objects.iterator().asScala
        .map(_.get())
        .filterNot(_.isDir)
        .map(_.objectName())
        .filter(_.endsWith(".json.gz"))
        .toList
      
      println(s"Found ${files.length} files for prefix: $prefix")
      files
    } catch {
      case e: Exception =>
        println(s"Error listing objects for prefix $prefix: ${e.getMessage}")
        List.empty[String]
    }
  }

  val mapper = new ObjectMapper()

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

  case class AggregatedStats(
    weekRange: String,
    nbrEntries: Int,
    nbrExit: Int,
    occupancy: Map[String, Int],
    revenueSimulation: Double,
    vehicleTypes: Map[String, Int]
  )

  def parseJsonToParkingEvent(jsonNode: JsonNode): Option[ParkingEvent] = {
    try {
      val parking = jsonNode.get("parking")
      val vehicle = jsonNode.get("vehicle")
      
      Some(ParkingEvent(
        parkingSpotId = parking.get("parkingSpotId").asText(),
        parkingLotId = parking.get("parkingLotId").asText(),
        isSlotHandicapped = parking.get("isSlotHandicapped").asBoolean(),
        duration = jsonNode.get("duration").asLong(),
        eventType = jsonNode.get("eventType").asText(),
        timestamp = jsonNode.get("timestamp").asText(),
        licensePlate = vehicle.get("licensePlate").asText(),
        color = vehicle.get("color").asText(),
        vehicleType = vehicle.get("vehicleType").asText()
      ))
    } catch {
      case _: Exception => None
    }
  }

  def calculateOccupancy(events: List[ParkingEvent]): Map[String, Int] = {
    val entriesByLot = events
      .filter(_.eventType == "PARKING_ENTRY")
      .groupBy(_.parkingLotId)
      .map { case (lotId, entries) => lotId -> entries.length }
    
    val exitsByLot = events
      .filter(_.eventType == "PARKING_EXIT")
      .groupBy(_.parkingLotId)
      .map { case (lotId, exits) => lotId -> exits.length }
    
    val allLots = (entriesByLot.keySet ++ exitsByLot.keySet).toList
    
    allLots.map { lot =>
      val entries = entriesByLot.getOrElse(lot, 0)
      val exits = exitsByLot.getOrElse(lot, 0)
      lot -> math.max(0, entries - exits)
    }.toMap
  }

  def calculateRevenueSimulation(occupancy: Map[String, Int], revenuePerHour: Double = 2.0): Double = {
    val totalOccupiedSpots = occupancy.values.sum
    // For weekly calculation, we assume average occupancy throughout the week
    totalOccupiedSpots * revenuePerHour * 24 * 7
  }

  def aggregateEvents(events: List[ParkingEvent]): AggregatedStats = {
    val entriesCount = events.count(_.eventType == "PARKING_ENTRY")
    val exitsCount = events.count(_.eventType == "PARKING_EXIT")
    
    val occupancy = calculateOccupancy(events)
    
    val vehicleTypeCounts = events
      .groupBy(_.vehicleType)
      .map { case (vehicleType, eventList) => vehicleType -> eventList.length }
    
    val revenueSimulation = calculateRevenueSimulation(occupancy)
    
    AggregatedStats(
      weekRange = weekParam,
      nbrEntries = entriesCount,
      nbrExit = exitsCount,
      occupancy = occupancy,
      revenueSimulation = revenueSimulation,
      vehicleTypes = vehicleTypeCounts
    )
  }

  def statsToJson(stats: AggregatedStats): String = {
    val occupancyJson = stats.occupancy.map { case (k, v) => s""""$k": $v""" }.mkString("{", ", ", "}")
    val vehicleTypesJson = stats.vehicleTypes.map { case (k, v) => s""""$k": $v""" }.mkString("{", ", ", "}")
    
    s"""{
      "weekRange": "${stats.weekRange}",
      "NbrEntries": ${stats.nbrEntries},
      "NbrExit": ${stats.nbrExit},
      "Occupancy": $occupancyJson,
      "RevenueSimulation": ${stats.revenueSimulation},
      "VehicleTypes": $vehicleTypesJson
    }"""
  }

  object JsonSetCommand extends ProtocolCommand {
    override def getRaw: Array[Byte] = SafeEncoder.encode("JSON.SET")
  }

  try {
    val allEvents = allGzippedFiles.flatMap { objectPath =>
      println(s"Processing file: $objectPath")
      try {
        val stream = minioClient.getObject(
          GetObjectArgs.builder()
            .bucket(bucketName)
            .`object`(objectPath)
            .build()
        )

        val gzipStream = new GZIPInputStream(stream)
        val jsonLines = Source.fromInputStream(gzipStream).getLines().toList

        jsonLines.flatMap { line =>
          if (line.trim.nonEmpty) {
            try {
              val jsonNode = mapper.readTree(line)
              parseJsonToParkingEvent(jsonNode)
            } catch {
              case e: Exception =>
                println(s"Error parsing line: ${e.getMessage}")
                None
            }
          } else {
            None
          }
        }
      } catch {
        case e: Exception =>
          println(s"Failed to process $objectPath: ${e.getMessage}")
          List.empty[ParkingEvent]
      }
    }

    println(s"Total events parsed: ${allEvents.length}")

    if (allEvents.nonEmpty) {
      val aggregatedStats = aggregateEvents(allEvents)
      val statsJson = statsToJson(aggregatedStats)
      
      val redisKey = s"parking-stats:weekly:$weekParam"
      
      try {
        redis.sendCommand(JsonSetCommand, redisKey, ".", statsJson)
        
        println(s"Uploaded aggregated weekly stats to Redis with key: $redisKey")
        println(s"Stats: ${statsJson}")
        println(s"Total files processed: ${allGzippedFiles.length}")
        
      } catch {
        case e: Exception =>
          println(s"Failed to upload to Redis: ${e.getMessage}")
      }
    } else {
      println("No events found to aggregate")
    }
    
  } finally {
    redis.close()
  }
}
