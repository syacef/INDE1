package srvc_stats

import io.minio.{MinioClient, ListObjectsArgs, GetObjectArgs}
import java.util.zip.GZIPInputStream
import scala.io.Source
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import scala.jdk.CollectionConverters._
import scala.collection.mutable.ListBuffer
import redis.clients.jedis.Jedis

object Main extends App {

  // Format : Year-Month-Day
  val Date_param = "2025-07-03"
  val Array(year, month, day) = Date_param.split("-")

  // Minio Client
  val minioClient = MinioClient.builder()
    .endpoint("http://localhost:9000")
    .credentials("minio", "minio123")
    .build()

  // Redis Client
  val redis = new Jedis("localhost", 6379)

  // Get the files from minio on the specified date
  val bucketName = "parking-events"
  val prefix = s"topics/parking-event-topic/$year/$month/$day/"
  val objects = minioClient.listObjects(
    ListObjectsArgs.builder()
      .bucket(bucketName)
      .prefix(prefix)
      .recursive(true)
      .build()
  )

  val gzippedFiles = objects.iterator().asScala
    .map(_.get())
    .filterNot(_.isDir)
    .map(_.objectName())
    .filter(_.endsWith(".json.gz"))
    .toList

  // Print the format of each file
  val mapper = new ObjectMapper()
  def printSchema(node: JsonNode, indent: String = ""): Unit = {
    val fields = node.fieldNames()
    while (fields.hasNext) {
      val field = fields.next()
      val child = node.get(field)
      val typeName =
        if (child.isTextual) "String"
        else if (child.isInt) "Int"
        else if (child.isLong) "Long"
        else if (child.isBoolean) "Boolean"
        else if (child.isDouble) "Double"
        else if (child.isArray) "Array"
        else if (child.isObject) "Object"
        else "Unknown"

      println(s"$indent$field: $typeName")

      if (child.isObject) printSchema(child, indent + "  ")
      if (child.isArray && child.size() > 0 && child.get(0).isObject)
        printSchema(child.get(0), indent + "  ")
    }
  }

  val allJsonObjects = ListBuffer[JsonNode]()
  var totalProcessed = 0

  try {
    // For each files
    gzippedFiles.foreach { objectPath =>
      println(s"\n Processing file : $objectPath")
      try {
        val stream = minioClient.getObject(
          GetObjectArgs.builder()
            .bucket(bucketName)
            .`object`(objectPath)
            .build()
        )

        val gzipStream = new GZIPInputStream(stream)
        val jsonLines = Source.fromInputStream(gzipStream).getLines()

        var fileObjectCount = 0
        jsonLines.foreach { line =>
          if (line.trim.nonEmpty) {
            try {
              val jsonNode = mapper.readTree(line)
              allJsonObjects += jsonNode
              fileObjectCount += 1
              totalProcessed += 1
            } catch {
              case e: Exception =>
                println(s"Error jsonlLines.foreach : ${e.getMessage}")
            }
          }
        }

        println(s"$fileObjectCount JSON objects in objectPath")

        // Print schema for the first object of each file (optional)
        if (fileObjectCount > 0) {
          println("📋 Schema for first object in this file:")
          printSchema(allJsonObjects.last)
        }

      } catch {
        case e: Exception =>
          println(s"❌ Failed to process $objectPath: ${e.getMessage}")
      }
    }

    // Upload to Redis
    val redisKey = s"parking-events:$Date_param"
    
    if (allJsonObjects.nonEmpty) {
      try {
        // Create JSON array string
        val jsonArrayBuilder = new StringBuilder()
        jsonArrayBuilder.append("[\n")
        allJsonObjects.zipWithIndex.foreach { case (jsonNode, index) =>
          jsonArrayBuilder.append(mapper.writeValueAsString(jsonNode))
          if (index < allJsonObjects.length - 1) {
            jsonArrayBuilder.append(",")
          }
          jsonArrayBuilder.append("\n")
        }
        jsonArrayBuilder.append("]")
        
        val finalJsonArray = jsonArrayBuilder.toString()
        
        redis.set(redisKey, finalJsonArray)
        
        println(s"\n ✅ Uploaded to Redis with key: $redisKey")
        println(s"Total files concatenated: ${gzippedFiles.length}")
        
      } catch {
        case e: Exception =>
          println(s"❌ Failed to upload to Redis: ${e.getMessage}")
      }
    } else {
      println("No JSON objects found to upload")
    }
    
  } finally {
    // Close Redis connection
    redis.close()
    println("Redis connection closed")
  }
}
