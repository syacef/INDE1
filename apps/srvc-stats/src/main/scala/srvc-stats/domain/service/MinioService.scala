package srvc_stats.domain.service

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import io.minio.{GetObjectArgs, ListObjectsArgs, MinioClient}
import srvc_stats.domain.entity.{EnvConfig, ParkingEvent, Parking, Vehicle}

import java.util.zip.GZIPInputStream
import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Failure, Success, Try}

class MinioService {

  private val minioClient: MinioClient = MinioClient.builder()
    .endpoint(EnvConfig.minioHost)
    .credentials(EnvConfig.minioAccessKey, EnvConfig.minioSecretKey)
    .build()

  private val bucketName: String = EnvConfig.minioBucket
  private val prefix: String = EnvConfig.minioPrefixPath

  def processFile(objectPath: String, mapper: ObjectMapper): List[ParkingEvent] = {
    println(s"Processing file: $objectPath")

    val result = for {
      stream <- Try(minioClient.getObject(
        GetObjectArgs.builder()
          .bucket(bucketName)
          .`object`(objectPath)
          .build()
      ))
      gzipStream <- Try(new GZIPInputStream(stream))
      jsonLines <- Try(Source.fromInputStream(gzipStream).getLines().toList)
    } yield {
      jsonLines.flatMap { line =>
        if (line.trim.nonEmpty) {
          Try(mapper.readTree(line)) match {
            case Success(jsonNode) => parseJsonToParkingEvent(jsonNode)
            case Failure(e) =>
              println(s"Error parsing line: ${e.getMessage}")
              None
          }
        } else {
          None
        }
      }
    }

    result match {
      case Success(events) => events
      case Failure(e) =>
        println(s"Failed to process $objectPath: ${e.getMessage}")
        List.empty[ParkingEvent]
    }
  }

  def getGzippedFiles(): List[String] = {
    val results = minioClient.listObjects(
      ListObjectsArgs.builder()
        .bucket(bucketName)
        .prefix(prefix)
        .recursive(true)
        .build()
    )

    results.iterator().asScala
      .map(_.get())
      .filterNot(_.isDir)
      .map(_.objectName())
      .filter(_.endsWith(".json.gz"))
      .toList
  }

  def listFiles(prefix: String): List[String] = {
    import scala.jdk.CollectionConverters._

    val objects = minioClient
        .listObjects(
        ListObjectsArgs.builder()
            .bucket(EnvConfig.minioBucket)
            .prefix(prefix)
            .recursive(true)
            .build()
        )

    objects.iterator().asScala
        .filter(_.get().isDir == false)
        .map(obj => s"s3n://${EnvConfig.minioBucket}/${obj.get().objectName()}")
        .toList
  }

  def downloadFilesForHour(year: String, month: String, day: String, hour: String): String = {
    val prefix = s"${EnvConfig.minioPrefixPath}/$year/$month/$day/$hour"
    val localDir = s"/tmp/parking-events/$year/$month/$day/$hour"

    new java.io.File(localDir).mkdirs()

    val objectList = listFiles(prefix)

    objectList.foreach { key =>
        val fileName = key.split("/").last
        val localPath = s"$localDir/$fileName"
        downloadObject(key, localPath)
    }

    s"$localDir/*.json.gz"
  }

  private def downloadObject(s3nKey: String, localPath: String): Unit = {
    try {
        val objectKey = s3nKey.stripPrefix(s"s3n://${bucketName}/")

        val stream = minioClient.getObject(
        GetObjectArgs.builder()
            .bucket(bucketName)
            .`object`(objectKey)
            .build()
        )

        val outFile = new java.io.File(localPath)
        val outStream = new java.io.FileOutputStream(outFile)

        val buffer = new Array[Byte](8000)
        Iterator
        .continually(stream.read(buffer))
        .takeWhile(_ != -1)
        .foreach(read => outStream.write(buffer, 0, read))

        outStream.close()
        stream.close()
    } catch {
        case e: Exception =>
        println(s"Failed to download $s3nKey to $localPath: ${e.getMessage}")
    }
  }

  private def parseJsonToParkingEvent(jsonNode: JsonNode): Option[ParkingEvent] = {
    try {
      val eventType = jsonNode.get("eventType").asText()
      val timestamp = jsonNode.get("timestamp").asText()

      val vehicleNode = jsonNode.get("vehicle")
      val licensePlate = vehicleNode.get("licensePlate").asText()
      val vehicleType = vehicleNode.get("vehicleType").asText()
      val color = vehicleNode.get("color").asText()
      val vehicle = Vehicle(licensePlate, vehicleType, color)

      val parkingNode = jsonNode.get("parking")
      val parkingLotId = parkingNode.get("parkingLotId").asText()
      val parkingSpotId = parkingNode.get("parkingSpotId").asText()
      val isSlotHandicapped = parkingNode.get("isSlotHandicapped").asBoolean()
      val parking = Parking(parkingLotId, parkingSpotId, isSlotHandicapped)

      val durationNode = jsonNode.get("duration")
      val duration = if (durationNode != null && durationNode.isNumber) Some(durationNode.asLong()) else None

      Some(ParkingEvent(eventType, timestamp, vehicle, parking, duration))
    } catch {
      case e: Exception =>
        println(s"Error converting JSON to ParkingEvent: ${e.getMessage}")
        None
    }
  }
}
