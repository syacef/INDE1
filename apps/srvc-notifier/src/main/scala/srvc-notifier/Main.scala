package srvc_notifier

import cats.effect.{ ExitCode, IO, IOApp }
import org.typelevel.log4cats.slf4j.Slf4jLogger
import srvc_notifier.domain.entity.EnvConfig
import srvc_notifier.domain.service.DiscordNotifier
import srvc_notifier.presentation.subscriber.AlertEventConsumer
import scala.concurrent.ExecutionContext

object Main extends IOApp {

  implicit val logger               = Slf4jLogger.getLogger[IO]
  implicit val ec: ExecutionContext = ExecutionContext.global

  val discordNotifier = new DiscordNotifier(EnvConfig.discordWebhookUrl)

  def run(args: List[String]): IO[ExitCode] =
    logger.info("Starting Parking Alert Notifier Service...") *>
      startConsumer().as(ExitCode.Success)

  private def startConsumer(): IO[Unit] = {
    val consumer = new AlertEventConsumer()
    consumer.start()
  }
}
