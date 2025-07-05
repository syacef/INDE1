package srvc_notifier

import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.client.blaze.BlazeClientBuilder
import org.typelevel.log4cats.slf4j.Slf4jLogger
import srvc_notifier.domain.entity.EnvConfig
import srvc_notifier.domain.service.DiscordNotifier
import srvc_notifier.presentation.subscriber.AlertEventConsumer

import scala.concurrent.ExecutionContext

object Main extends IOApp {
  implicit val logger               = Slf4jLogger.getLogger[IO]
  implicit val ec: ExecutionContext = ExecutionContext.global

  def run(args: List[String]): IO[ExitCode] = {
    val clientResource = BlazeClientBuilder[IO](ec).resource

    clientResource.use { client =>
      val discordNotifier = new DiscordNotifier(EnvConfig.discordWebhookUrl, client)

      for {
        _ <- logger.info("Starting Parking Alert Notifier Service...")
        _ <- startConsumer(discordNotifier)
      } yield ExitCode.Success
    }
  }

  private def startConsumer(discordNotifier: DiscordNotifier): IO[Unit] = {
    val consumer = new AlertEventConsumer(discordNotifier)
    consumer.start()
  }
}
