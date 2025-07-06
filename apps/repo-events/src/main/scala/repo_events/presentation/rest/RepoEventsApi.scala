package repo_events.presentation.rest

import cats.effect._
import com.comcast.ip4s._
import fs2.Stream
import fs2.kafka._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._
import org.http4s.ember.server._
import org.http4s.implicits._
import repo_events.domain.entity.{ EnvConfig, ParkingEvent }
import repo_events.presentation.dto.{ MetricPayloadRequest, ParkingSlotResponse, QueryRequest }

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

object RepoEventsApi extends IOApp {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val parkingSlots = new ConcurrentHashMap[String, ParkingSlotResponse]()
  private val eventHistory = new ConcurrentHashMap[Long, List[ParkingEvent]]()

  val consumerSettings = ConsumerSettings[IO, String, String]
    .withBootstrapServers(EnvConfig.kafkaBootstrapServers)
    .withGroupId(EnvConfig.kafkaGroupId)
    .withAutoOffsetReset(AutoOffsetReset.Latest)
    .withEnableAutoCommit(true)

  def processParkingEvent(event: ParkingEvent): IO[Unit] = IO {
    val slotKey   = s"${event.parking.parkingLotId}-${event.parking.parkingSpotId}"
    val timestamp = Instant.now().toEpochMilli

    val currentEvents = eventHistory.getOrDefault(timestamp, List.empty)
    eventHistory.put(timestamp, event :: currentEvents)

    event.eventType match {
      case "PARKING_ENTRY" =>
        parkingSlots.put(
          slotKey,
          ParkingSlotResponse(
            slot_id = event.parking.parkingSpotId,
            occupied = true,
            lot = event.parking.parkingLotId,
            plate = Some(event.vehicle.licensePlate)
          )
        )
      case "PARKING_EXIT" =>
        parkingSlots.put(
          slotKey,
          ParkingSlotResponse(
            slot_id = event.parking.parkingSpotId,
            occupied = false,
            lot = event.parking.parkingLotId,
            plate = None
          )
        )
      case _ =>
        println(s"Unknown event type: ${event.eventType}")
    }
  }

  def kafkaConsumer: Stream[IO, Unit] =
    KafkaConsumer
      .stream(consumerSettings)
      .subscribeTo(EnvConfig.kafkaTopic)
      .records
      .mapAsync(25) { committable =>
        val record = committable.record
        for {
          _           <- IO(println(s"Received message: ${record.value}"))
          eventResult <- IO(parseParkingEvent(record.value))
          _ <- eventResult match {
            case Right(event) => processParkingEvent(event)
            case Left(error)  => IO(println(s"Failed to parse event: $error"))
          }
          _ <- committable.offset.commit
        } yield ()
      }
      .handleErrorWith { error =>
        Stream.eval(IO(println(s"Kafka consumer error: ${error.getMessage}"))) >> Stream.empty
      }

  def parseParkingEvent(jsonString: String): Either[String, ParkingEvent] =
    decode[ParkingEvent](jsonString) match {
      case Right(event) => Right(event)
      case Left(error)  => Left(s"JSON parsing error: ${error.getMessage}")
    }

  // Missing function: getAvailableMetrics
  def getAvailableMetrics: List[String] =
    List("parking_occupancy", "parking_events", "parking_duration", "lot_capacity")

  // Missing function: getMetricPayloadOptions
  def getMetricPayloadOptions(metric: String, payload: Map[String, String], name: String): Json = {
    val result = metric match {
      case "parking_occupancy" =>
        Map(
          "options"     -> List("lot_id", "spot_id", "time_range"),
          "description" -> "Parking slot occupancy metrics"
        )
      case "parking_events" =>
        Map(
          "options"     -> List("event_type", "lot_id", "time_range"),
          "description" -> "Parking event history"
        )
      case "parking_duration" =>
        Map(
          "options"     -> List("lot_id", "spot_id", "duration_range"),
          "description" -> "Parking duration statistics"
        )
      case "lot_capacity" =>
        Map(
          "options"     -> List("lot_id", "capacity_threshold"),
          "description" -> "Parking lot capacity utilization"
        )
      case _ =>
        Map("error" -> s"Unknown metric: $metric")
    }
    Json.fromFields(result.map { case (k, v) =>
      k -> (v match {
        case s: String  => Json.fromString(s)
        case l: List[_] => Json.fromValues(l.map(_.toString).map(Json.fromString))
        case _          => Json.fromString(v.toString)
      })
    })
  }

  // Helper function to parse time strings to Long
  def parseTimeString(timeStr: String): Long =
    try
      // Try parsing as epoch milliseconds first
      timeStr.toLong
    catch {
      case _: NumberFormatException =>
        // If that fails, try parsing as ISO date string
        try
          java.time.Instant.parse(timeStr).toEpochMilli
        catch {
          case _: Exception =>
            // Fallback to current time
            Instant.now().toEpochMilli
        }
    }

  // Missing function: queryMetricData
  def queryMetricData(
    target: String,
    payload: Option[Map[String, String]],
    fromStr: String,
    toStr: String
  ): Map[String, Any] = {
    val from = parseTimeString(fromStr)
    val to   = parseTimeString(toStr)
    target match {
      case "parking_occupancy" =>
        val slots         = parkingSlots.values().asScala.toList
        val occupiedCount = slots.count(_.occupied)
        val totalCount    = slots.length
        Map(
          "target" -> target,
          "datapoints" -> List(
            List(occupiedCount, from),
            List(occupiedCount, to)
          ),
          "meta" -> Map(
            "total_slots"     -> totalCount,
            "occupied_slots"  -> occupiedCount,
            "available_slots" -> (totalCount - occupiedCount)
          )
        )

      case "parking_events" =>
        val events = eventHistory.asScala.flatMap { case (timestamp, eventList) =>
          if (timestamp >= from && timestamp <= to) {
            eventList.map(event =>
              Map(
                "timestamp"     -> timestamp,
                "event_type"    -> event.eventType,
                "lot_id"        -> event.parking.parkingLotId,
                "spot_id"       -> event.parking.parkingSpotId,
                "license_plate" -> event.vehicle.licensePlate
              )
            )
          } else {
            List.empty
          }
        }.toList

        Map(
          "target"     -> target,
          "datapoints" -> events,
          "meta" -> Map(
            "event_count" -> events.length,
            "time_range"  -> Map("from" -> from, "to" -> to)
          )
        )

      case "lot_capacity" =>
        val lotId = payload.flatMap(_.get("lot_id"))
        val filteredSlots = lotId match {
          case Some(id) => parkingSlots.values().asScala.filter(_.lot == id).toList
          case None     => parkingSlots.values().asScala.toList
        }

        val occupiedCount      = filteredSlots.count(_.occupied)
        val totalCount         = filteredSlots.length
        val capacityPercentage = if (totalCount > 0) (occupiedCount.toDouble / totalCount) * 100 else 0.0

        Map(
          "target" -> target,
          "datapoints" -> List(
            List(capacityPercentage, from),
            List(capacityPercentage, to)
          ),
          "meta" -> Map(
            "lot_id"              -> lotId.getOrElse("all"),
            "capacity_percentage" -> capacityPercentage,
            "occupied_slots"      -> occupiedCount,
            "total_slots"         -> totalCount
          )
        )

      case _ =>
        Map(
          "target"     -> target,
          "error"      -> s"Unknown target: $target",
          "datapoints" -> List.empty
        )
    }
  }

  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "events" =>
      val events = parkingSlots.values().asScala.toList
      Ok(events.asJson)

    case GET -> Root / "events" / lot =>
      val lotEvents = parkingSlots.values().asScala.filter(_.lot == lot).toList
      Ok(lotEvents.asJson)

    case GET -> Root =>
      Ok("OK")

    case req @ POST -> Root / "metrics" =>
      for {
        _ <- req.as[String]
        metrics = getAvailableMetrics
        response <- Ok(metrics.asJson)
      } yield response

    case req @ POST -> Root / "metric-payload-options" =>
      for {
        requestBody <- req.as[MetricPayloadRequest]
        options = getMetricPayloadOptions(requestBody.metric, requestBody.payload, requestBody.name)
        response <- Ok(options)
      } yield response

    case req @ POST -> Root / "query" =>
      for {
        queryRequest <- req.as[QueryRequest]
        responses = queryRequest.targets.map { target =>
          queryMetricData(target.target, target.payload, queryRequest.range.from, queryRequest.range.to)
        }
        jsonResponses = responses.map(mapData =>
          Json.fromFields(mapData.map { case (k, v) =>
            k -> Json.fromString(v.toString)
          })
        )
        response <- Ok(Json.fromValues(jsonResponses))
      } yield response

    case GET -> Root / "liveness" =>
      Ok("Alive")

    case GET -> Root / "readiness" =>
      Ok("Ready")
  }

  val httpApp = routes.orNotFound

  def run(args: List[String]): IO[ExitCode] = {
    val host = Host.fromString(EnvConfig.appHost).getOrElse(ipv4"0.0.0.0")
    val port = Port.fromInt(EnvConfig.appPort).getOrElse(port"8080")

    val startMetrics = IO {
      DefaultExports.initialize()
      new HTTPServer(EnvConfig.prometheusHost, EnvConfig.prometheusPort, true)
    }

    val server = EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(httpApp)
      .build

    val kafkaStream = kafkaConsumer.compile.drain

    startMetrics *> (
      server.use(_ => kafkaStream) &>
        IO.never
    ).as(ExitCode.Success)
  }
}
