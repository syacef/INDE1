package srvc_stats

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{ Dataset, SparkSession }
import redis.clients.jedis.JedisSentinelPool
import srvc_stats.domain.entity.{ AggregatedStatsSpark, EnvConfig, ParkingEventSpark }
import srvc_stats.domain.service.{ MinioService, RedisService }

import java.time.{ LocalDateTime, ZoneOffset }

object MainHourly {

  def getDateTimeParams(): (String, String, String, String, String, String) = {
    val now          = LocalDateTime.now(ZoneOffset.UTC)
    val previousHour = now.minusHours(1)
    val year         = previousHour.getYear.toString
    val month        = f"${previousHour.getMonthValue}%02d"
    val day          = f"${previousHour.getDayOfMonth}%02d"
    val hour         = f"${previousHour.getHour}%02d"
    val dateParam    = s"$year-$month-$day"

    (year, month, day, hour, dateParam, s"$year/$month/$day/$hour")
  }

  def readParkingEvents(spark: SparkSession, s3Path: String, minioService: MinioService): Dataset[ParkingEventSpark] = {
    import spark.implicits._

    val schema = StructType(
      Seq(
        StructField(
          "parking",
          StructType(
            Seq(
              StructField("parkingSpotId", StringType, nullable = false),
              StructField("parkingLotId", StringType, nullable = false),
              StructField("isSlotHandicapped", BooleanType, nullable = false)
            )
          ),
          nullable = false
        ),
        StructField(
          "vehicle",
          StructType(
            Seq(
              StructField("licensePlate", StringType, nullable = false),
              StructField("color", StringType, nullable = false),
              StructField("vehicleType", StringType, nullable = false)
            )
          ),
          nullable = false
        ),
        StructField("duration", LongType, nullable = false),
        StructField("eventType", StringType, nullable = false),
        StructField("timestamp", StringType, nullable = false)
      )
    )

    val allFiles = minioService.listFiles(s3Path)

    println(s"Found ${allFiles.length} files to process")

    val df = spark.read
      .schema(schema)
      .option("multiline", "false")
      .option("compression", "gzip")
      .json(s3Path)
      .filter(col("eventType").isin("PARKING_ENTRY", "PARKING_EXIT"))

    val parkingEventsDF = df.select(
      col("parking.parkingSpotId").as("parkingSpotId"),
      col("parking.parkingLotId").as("parkingLotId"),
      col("parking.isSlotHandicapped").as("isSlotHandicapped"),
      col("duration"),
      col("eventType"),
      col("timestamp"),
      col("vehicle.licensePlate").as("licensePlate"),
      col("vehicle.color").as("color"),
      col("vehicle.vehicleType").as("vehicleType")
    )

    val sample = parkingEventsDF.limit(1).collect()
    if (sample.isEmpty) {
      throw new RuntimeException(s"No valid parking events found in S3 path: $s3Path")
    } else {
      println(s"Sample parking event: ${sample(0).mkString(", ")}")
    }

    parkingEventsDF.cache()

    parkingEventsDF.as[ParkingEventSpark]
  }

  def calculateOccupancyEfficient(events: Dataset[ParkingEventSpark]): Map[String, Long] = {

    val eventCounts = events
      .groupBy(col("parkingLotId"), col("eventType"))
      .agg(count("*").as("count"))
      .collect()

    val entriesByLot = eventCounts
      .filter(_.getString(1) == "PARKING_ENTRY")
      .map(row => row.getString(0) -> row.getLong(2))
      .toMap

    val exitsByLot = eventCounts
      .filter(_.getString(1) == "PARKING_EXIT")
      .map(row => row.getString(0) -> row.getLong(2))
      .toMap

    val allLots = entriesByLot.keySet ++ exitsByLot.keySet

    allLots.map { lot =>
      val entries = entriesByLot.getOrElse(lot, 0L)
      val exits   = exitsByLot.getOrElse(lot, 0L)
      lot -> math.max(0L, entries - exits)
    }.toMap
  }

  def calculateVehicleTypeCounts(events: Dataset[ParkingEventSpark]): Map[String, Long] =
    events
      .groupBy(col("vehicleType"))
      .agg(count("*").as("count"))
      .collect()
      .map(row => row.getString(0) -> row.getLong(1))
      .toMap

  def calculateRevenueSimulation(
    occupancy: Map[String, Long],
    revenuePerHour: Double = EnvConfig.revenuePerHour
  ): Double = {
    val totalOccupiedSpots = occupancy.values.sum
    totalOccupiedSpots * revenuePerHour
  }

  def aggregateEvents(events: Dataset[ParkingEventSpark], date: String, hour: String): AggregatedStatsSpark = {
    val totalEvents  = events.count()
    val entriesCount = events.filter(col("eventType") === "PARKING_ENTRY").count()
    val exitsCount   = events.filter(col("eventType") === "PARKING_EXIT").count()

    val occupancy         = calculateOccupancyEfficient(events)
    val vehicleTypeCounts = calculateVehicleTypeCounts(events)
    val revenueSimulation = calculateRevenueSimulation(occupancy)

    println(s"Processed $totalEvents events: $entriesCount entries, $exitsCount exits")

    AggregatedStatsSpark(
      date = date,
      hour = hour,
      nbrEntries = entriesCount,
      nbrExit = exitsCount,
      occupancy = occupancy,
      revenueSimulation = revenueSimulation,
      vehicleTypes = vehicleTypeCounts
    )
  }

  def statsToJson(stats: AggregatedStatsSpark): String = {
    val occupancyJson    = stats.occupancy.map { case (k, v) => s""""$k": $v""" }.mkString("{", ", ", "}")
    val vehicleTypesJson = stats.vehicleTypes.map { case (k, v) => s""""$k": $v""" }.mkString("{", ", ", "}")

    s"""{
      "date": "${stats.date}",
      "hour": "${stats.hour}",
      "NbrEntries": ${stats.nbrEntries},
      "NbrExit": ${stats.nbrExit},
      "Occupancy": $occupancyJson,
      "RevenueSimulation": ${stats.revenueSimulation},
      "VehicleTypes": $vehicleTypesJson
    }"""
  }

  def uploadToRedis(stats: AggregatedStatsSpark, redisKey: String, redisPool: JedisSentinelPool): Unit = {
    val statsJson = statsToJson(stats)

    val jedis = redisPool.getResource
    jedis.sendCommand(
      redis.clients.jedis.Protocol.Command.valueOf("JSON.SET"),
      redisKey.getBytes("UTF-8"),
      ".".getBytes("UTF-8"),
      statsJson.getBytes("UTF-8")
    )
    println(s"Successfully uploaded stats to Redis with key: $redisKey")
    println(
      s"Stats summary: ${stats.nbrEntries} entries, ${stats.nbrExit} exits, revenue: ${stats.revenueSimulation}"
    )
    jedis.close()
  }

  def main(args: Array[String]): Unit = {
    val redisPool: JedisSentinelPool = RedisService.createRedisPool()
    val minioService: MinioService = new MinioService()
    val spark = SparkSession
      .builder()
      .appName("ParkingStatsHourly")
      .config("spark.sql.adaptive.enabled", "true")
      .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
      .config("spark.sql.adaptive.skewJoin.enabled", "true")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .config("spark.sql.execution.arrow.pyspark.enabled", "true")
      .config("spark.sql.streaming.checkpointLocation", "/tmp/spark-checkpoints")

      /*
      // S3N Configuration for MinIO (Native S3 implementation)
      .config("spark.hadoop.fs.s3n.impl", "org.apache.hadoop.fs.s3native.NativeS3FileSystem")
      .config("spark.hadoop.fs.s3n.awsAccessKeyId", "xxx")
      .config("spark.hadoop.fs.s3n.awsSecretAccessKey", "xxx")
      
      // MinIO endpoint configuration for S3N
      .config("spark.hadoop.fs.s3n.endpoint", "localhost:9000")
      .config("spark.hadoop.fs.s3n.connection.ssl.enabled", "false")
      .config("spark.hadoop.fs.s3n.path.style.access", "true")
      
      // S3N specific settings
      .config("spark.hadoop.fs.s3n.multipart.uploads.enabled", "false")
      .config("spark.hadoop.fs.s3n.multipart.uploads.block.size", "67108864")
      .config("spark.hadoop.fs.s3n.buffer.dir", "/tmp")
      
      // Connection timeouts
      .config("spark.hadoop.fs.s3n.connection.timeout", "30000")
      .config("spark.hadoop.fs.s3n.socket.timeout", "30000")
      .config("spark.hadoop.fs.s3n.connection.establish.timeout", "30000")
      .config("spark.hadoop.fs.s3n.connection.request.timeout", "30000")
      
      // Retry settings
      .config("spark.hadoop.fs.s3n.attempts.maximum", "3")
      .config("spark.hadoop.fs.s3n.retry.limit", "3")
      .config("spark.hadoop.fs.s3n.retry.interval", "1000")
      */
      .getOrCreate()

    val (year, month, day, hour, date, pathSuffix) = getDateTimeParams()
    val s3Path = s"${EnvConfig.minioPrefixPath}/$year/$month/05/$hour/*.json.gz"

    println(s"Starting Spark application for date: $date, hour: $hour")
    println(s"Reading from S3 path: $s3Path (bucket ${EnvConfig.minioBucket})")

    val events     = readParkingEvents(spark, s3Path, minioService)
    val eventCount = events.count()

    if (eventCount > 0) {
      println(s"Found $eventCount events to process")

      val aggregatedStats = aggregateEvents(events, date, hour)
      val redisKey        = s"parking-stats:hourly:$date:$hour"

      uploadToRedis(aggregatedStats, redisKey, redisPool)
    } else {
      println("No events found for processing")
    }

    events.unpersist()
    redisPool.close()
    spark.stop()
  }
}
