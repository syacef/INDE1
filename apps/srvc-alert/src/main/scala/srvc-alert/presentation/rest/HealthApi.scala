package srvc_alert.presentation.rest

import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._
import org.http4s.ember.server._
import org.http4s.implicits._
import redis.clients.jedis.JedisPool
import srvc_alert.domain.entity.EnvConfig

object HealthApi {

  def serveHealthApi(redisPool: JedisPool): Resource[IO, Unit] = {
    val routes = HttpRoutes.of[IO] {
      case GET -> Root / "liveness" =>
        IO(redisPool.getResource.ping()).flatMap {
          case "PONG" => Ok("Alive")
          case _      => InternalServerError("Redis not responding")
        }

      case GET -> Root / "readiness" =>
        IO(redisPool.getResource.ping()).flatMap {
          case "PONG" => Ok("Ready")
          case _      => InternalServerError("Redis not ready")
        }
    }

    val httpApp = routes.orNotFound

    val host = Host.fromString(EnvConfig.appHost).getOrElse(ipv4"0.0.0.0")
    val port = Port.fromInt(EnvConfig.appPort).getOrElse(port"8080")

    EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(httpApp)
      .build
      .void
  }
}
