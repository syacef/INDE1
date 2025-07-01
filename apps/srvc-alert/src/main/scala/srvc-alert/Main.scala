package srvc_alert

import akka.actor.ActorSystem
import cats.effect.{ IO, IOApp }
import org.typelevel.log4cats.slf4j.Slf4jLogger
import srvc_alert.domain.entity.EnvConfig
import srvc_alert.domain.service.{ AlertService, UserService }
import srvc_alert.presentation.subscriber.ParkingEventConsumer

import scala.concurrent.ExecutionContext

object Main extends IOApp.Simple {

  override def run: IO[Unit] =
    Slf4jLogger.create[IO].flatMap { implicit logger =>
      val system: ActorSystem           = ActorSystem("parking-event-consumer")
      implicit val ec: ExecutionContext = system.dispatcher

      for {
        _ <- IO.println(s"""
Configuration loaded:
  Kafka topic: ${EnvConfig.kafkaTopic}
  Kafka servers: ${EnvConfig.kafkaServers}
  Consumer group: ${EnvConfig.consumerGroupId}
  Redis: ${EnvConfig.redisHost}:${EnvConfig.redisPort}""")

        userService  = new UserService()
        alertService = new AlertService()
        consumer     = new ParkingEventConsumer(userService, alertService)

        fiber <- consumer.start().start

        _ <- IO.blocking(scala.io.StdIn.readLine("Press ENTER to stop the consumer...\n"))
        _ <- IO.println("Stopping consumer...")
        _ <- IO.fromFuture(IO(system.terminate()))
        _ <- fiber.cancel
      } yield ()
    }
}
