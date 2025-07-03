package repo_account.presentation.rest

import cats.effect._
import com.comcast.ip4s._
import io.circe.syntax._
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._
import org.http4s.ember.server._
import org.http4s.implicits._
import redis.clients.jedis.JedisSentinelPool
import repo_account.data.model.UserModel
import repo_account.domain.entity.EnvConfig
import repo_account.domain.service.UserService

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

object UserApi extends IOApp {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val sentinels   = EnvConfig.redisHosts.asJava
  val masterName  = EnvConfig.redisMasterName
  val redisPool   = new JedisSentinelPool(masterName, sentinels)
  val userService = UserService(redisPool)

  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "account" =>
      IO.fromFuture(IO(userService.getAllUsers())).flatMap(users => Ok(users.asJson))

    case GET -> Root / "account" / IntVar(id) =>
      IO.fromFuture(IO(userService.getUserById(id))).flatMap {
        case Some(user) => Ok(user.asJson)
        case None       => NotFound()
      }

    case req @ POST -> Root / "account" =>
      req.as[UserModel].flatMap { user =>
        IO.fromFuture(IO(userService.createUser(user))).flatMap { saved =>
          Created(saved.asJson)
        }
      }

    case req @ POST -> Root / "account" / "bulk" =>
      req.as[List[UserModel]].flatMap { users =>
        IO.fromFuture(IO(userService.createUsers(users))).flatMap { savedUsers =>
          Created(savedUsers.asJson)
        }
      }

    case req @ POST -> Root / "account" / "tmp" =>
      req.as[UserModel].flatMap { user =>
        IO.fromFuture(IO(userService.createTemporaryUser(user, EnvConfig.temporaryAccountExpiration))).flatMap { tempUser =>
          Created(tempUser.asJson)
        }
      }

    case DELETE -> Root / "account" / IntVar(id) =>
      IO.fromFuture(IO(userService.deleteUser(id))).flatMap {
        case true  => NoContent()
        case false => NotFound()
      }

    case GET -> Root / "liveness" =>
      redisPool.getResource.ping() match {
        case "PONG" => Ok("Alive")
        case _      => InternalServerError("Redis connection failed")
      }

    case GET -> Root / "readiness" =>
      redisPool.getResource.ping() match {
        case "PONG" => Ok("Ready")
        case _      => InternalServerError("Redis connection failed")
      }
  }

  val httpApp = routes.orNotFound

  def run(args: List[String]): IO[ExitCode] = {
    val host = Host.fromString(EnvConfig.appHost).getOrElse(ipv4"0.0.0.0")
    val port = Port.fromInt(EnvConfig.appPort).getOrElse(port"8080")

    val startMetrics = IO {
      DefaultExports.initialize()
      new HTTPServer(EnvConfig.prometheusHost, EnvConfig.prometheusPort, true)
    }

    startMetrics *> EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}
