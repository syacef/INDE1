package srvc_alert

import akka.actor.ActorSystem
import cats.effect.{ IO, IOApp }
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import org.typelevel.log4cats.slf4j.Slf4jLogger
import redis.clients.jedis.JedisSentinelPool
import srvc_alert.domain.entity.EnvConfig
import srvc_alert.domain.service.{ AlertEventPublisher, UserService }
import srvc_alert.presentation.rest.HealthApi
import srvc_alert.presentation.subscriber.ParkingEventConsumer

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

object Main extends IOApp.Simple {

  override def run: IO[Unit] =
    Slf4jLogger.create[IO].flatMap { implicit logger =>
      val system: ActorSystem           = ActorSystem("parking-event-consumer")
      implicit val ec: ExecutionContext = system.dispatcher

      IO {
        DefaultExports.initialize()
        new HTTPServer(EnvConfig.prometheusHost, EnvConfig.prometheusPort)
      } *>
        IO.println(s"Prometheus metrics server on http://${EnvConfig.prometheusHost}:${EnvConfig.prometheusPort}") *>
        IO.println(s"Health API server on http://${EnvConfig.appHost}:${EnvConfig.appPort}") *>
        IO.println(s"""
Configuration loaded:
  Kafka parking topic: ${EnvConfig.kafkaParkingTopic}
  Kafka alert topic: ${EnvConfig.kafkaAlertTopic}
  Kafka servers: ${EnvConfig.kafkaServers}
  Consumer group: ${EnvConfig.consumerGroupId}
  Redis Sentinels: ${EnvConfig.redisHosts} (master: ${EnvConfig.redisMasterName})""") *> {
          val redisPool             = new JedisSentinelPool(EnvConfig.redisMasterName, EnvConfig.redisHosts.asJava)
          val userService           = new UserService()
          val alertEventPublisher   = new AlertEventPublisher()
          val consumer              = new ParkingEventConsumer(userService, alertEventPublisher)
          val waitForever: IO[Unit] = IO.never

          IO.race(
            HealthApi.serveHealthApi(redisPool).useForever,
            consumer.start()
          ).void
        }
    }
}
