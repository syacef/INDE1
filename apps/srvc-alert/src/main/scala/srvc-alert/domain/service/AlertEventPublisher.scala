package srvc_alert.domain.service

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import org.apache.kafka.clients.producer.{ KafkaProducer, ProducerConfig, ProducerRecord }
import org.apache.kafka.common.serialization.StringSerializer
import srvc_alert.domain.entity.{ AlertEvent, EnvConfig }

import java.util.Properties

class AlertEventPublisher() {

  val props = new Properties()
  props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, EnvConfig.kafkaServers)
  props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
  props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)

  val producer = new KafkaProducer[String, String](props)

  def publish(event: AlertEvent): IO[Unit] = {
    val json   = event.asJson.noSpaces
    val record = new ProducerRecord[String, String](EnvConfig.kafkaAlertTopic, event.vehiclePlate, json)

    IO.async_[Unit] { cb =>
      producer.send(
        record,
        (metadata, exception) =>
          Option(exception) match {
            case Some(ex) => cb(Left(ex))
            case None     => cb(Right(()))
          }
      )
    }
  }
}
