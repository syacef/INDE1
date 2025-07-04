package srvc_io

import cats.effect._
import cats.syntax.all._
import fs2.kafka._
import io.circe.generic.auto._
import io.circe.syntax._
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import retry.RetryDetails._
import retry._
import srvc_io.domain.GeneratorService
import srvc_io.entities.{ EnvConfig, ParkingEvent }

import scala.concurrent.duration.{ DurationInt, DurationLong }

object Main extends IOApp {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val producerSettings: ProducerSettings[IO, String, String] =
    ProducerSettings[IO, String, String]
      .withBootstrapServers(EnvConfig.kafkaServers)

  val retryPolicy = RetryPolicies.limitRetries[IO](EnvConfig.maxRetries) join
    RetryPolicies.constantDelay[IO](EnvConfig.backoffMs.millis)

  def sendEvent(event: ParkingEvent, producer: KafkaProducer[IO, String, String]): IO[Unit] = {
    val json    = event.asJson.noSpaces
    val record  = ProducerRecord(EnvConfig.kafkaTopic, event.vehicle.licensePlate, json)
    val message = ProducerRecords.one(record)

    val startTime = System.currentTimeMillis()

    retryingOnAllErrors[Unit](
      policy = retryPolicy,
      onError = (e: Throwable, details: RetryDetails) =>
        logger.warn(s"Retry ${details match {
            case WillDelayAndRetry(_, retriesSoFar, _) => retriesSoFar
            case _                                     => "unknown"
          }} after error: ${e.getMessage}")
    ) {
      producer.produce(message).flatten.void
    }
  }

  def printConfiguration(): IO[Unit] =
    IO.println(
      s"""
         |=== Parking Event Generator Configuration ===
         | Kafka topic: ${EnvConfig.kafkaTopic}
         | Kafka servers: ${EnvConfig.kafkaServers}
         | Max retries: ${EnvConfig.maxRetries}
         | Backoff: ${EnvConfig.backoffMs}ms
         --------------------------------------------------------
         | Prometheus Server: ${EnvConfig.prometheusHost}:${EnvConfig.prometheusPort}
         --------------------------------------------------------
         | Events per second: ${EnvConfig.eventsPerSecond}
         | Parking lots: ${EnvConfig.parkingLots.mkString(", ")}
         | Vehicle colors: ${EnvConfig.vehicleColors.mkString(", ")}
         | Vehicle types: ${EnvConfig.vehicleTypes.mkString(", ")}
         | Parking plate pattern: ${EnvConfig.parkingPlatePattern}
         | Parking plate random probability: ${EnvConfig.parkingPlateRandomProbability}
         | Parking slots: ${EnvConfig.parkingSlots.map(_.mkString("-")).mkString(", ")}
         | Parking duration range: ${EnvConfig.minParkingDuration / 1000}-${EnvConfig.maxParkingDuration / 1000} s
         | Handicap slots: ${EnvConfig.handicapSlots.map(_.mkString(", ")).mkString("; ")}
         |=============================================
         |""".stripMargin
    )

  private def logEvent(event: ParkingEvent): IO[Unit] = logger.debug(
    s"Generated ${event.eventType} for ${event.vehicle.licensePlate} at ${event.parking.parkingLotId}:${event.parking.parkingSpotId}"
  )

  def startPrometheusServer(): Resource[IO, HTTPServer] =
    Resource.make(
      IO.blocking {
        DefaultExports.initialize()
        new HTTPServer(EnvConfig.prometheusHost, EnvConfig.prometheusPort)
      }
    )(server => IO.blocking(server.stop()))

  override def run(args: List[String]): IO[ExitCode] =
    startPrometheusServer().use { _ =>
      KafkaProducer.resource(producerSettings).use { producer =>
        def loop: IO[Unit] =
          IO.sleep((1000 / EnvConfig.eventsPerSecond).millis).flatMap { _ =>
            GeneratorService.generateEntryEvent().flatMap { entryEventOpt =>
              GeneratorService.cleanFinishedParkingSessions().flatMap { exitEvents =>
                val handleEntry = entryEventOpt match {
                  case Some(entryEvent) =>
                    logEvent(entryEvent) *> sendEvent(entryEvent, producer)
                  case None =>
                    logger.warn("No parking spot available - could not create parking event")
                }

                val handleExits = exitEvents.traverse { exitEvent =>
                  logEvent(exitEvent) *> sendEvent(exitEvent, producer)
                }

                handleEntry *> handleExits.void
              }
            }
          }

        val logStartup =
          printConfiguration() *>
            logger.info(
              s"Starting Prometheus metrics server on ${EnvConfig.prometheusHost}:${EnvConfig.prometheusPort}"
            ) *>
            logger.debug("Starting parking event generator...")

        logStartup *> fs2.Stream.repeatEval(loop).compile.drain.as(ExitCode.Success)
      }
    }
}
