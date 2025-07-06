package repo_events.presentation.rest

import cats.effect._
import com.comcast.ip4s._
import fs2.Stream
import fs2.kafka._
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
import repo_events.domain.entity.{EnvConfig, ParkingEvent, ParkingSlotResponse}

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

object RepoEventsApi extends IOApp {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val parkingSlots = new ConcurrentHashMap[String, ParkingSlotResponse]()

  val consumerSettings = ConsumerSettings[IO, String, String]
    .withBootstrapServers(EnvConfig.kafkaBootstrapServers)
    .withGroupId(EnvConfig.kafkaGroupId)
    .withAutoOffsetReset(AutoOffsetReset.Latest)
    .withEnableAutoCommit(true)

  def processParkingEvent(event: ParkingEvent): IO[Unit] = IO {
    val slotKey = s"${event.parking.parkingLotId}-${event.parking.parkingSpotId}"

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

  // Proper JSON parsing using circe
  def parseParkingEvent(jsonString: String): Either[String, ParkingEvent] =
    decode[ParkingEvent](jsonString) match {
      case Right(event) => Right(event)
      case Left(error)  => Left(s"JSON parsing error: ${error.getMessage}")
    }

  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "events" =>
      val events = parkingSlots.values().asScala.toList
      Ok(events.asJson)

    case GET -> Root / "events" / lot =>
      val lotEvents = parkingSlots.values().asScala.filter(_.lot == lot).toList
      Ok(lotEvents.asJson)

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
