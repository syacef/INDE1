package repo_account.presentation.rest

import cats.effect._
import com.comcast.ip4s._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._
import org.http4s.ember.server._
import org.http4s.implicits._
import redis.clients.jedis.JedisPool
import repo_account.data.model.UserModel
import repo_account.domain.entities.EnvConfig
import repo_account.domain.service.UserService

import scala.concurrent.ExecutionContext

object UserApi extends IOApp {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val redisPool   = new JedisPool(EnvConfig.redisHost, EnvConfig.redisPort)
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
      for {
        user  <- req.as[UserModel]
        saved <- IO.fromFuture(IO(userService.createUser(user)))
        res   <- Created(saved.asJson)
      } yield res

    case DELETE -> Root / "account" / IntVar(id) =>
      IO.fromFuture(IO(userService.deleteUser(id))).flatMap {
        case true  => NoContent()
        case false => NotFound()
      }

    case GET -> Root / "liveness" =>
      redisPool.getResource.ping() match {
        case "PONG" => Ok("Service is ready")
        case _      => InternalServerError("Redis connection failed")
      }

    case GET -> Root / "readiness" =>
      redisPool.getResource.ping() match {
        case "PONG" => Ok("Service is ready")
        case _      => InternalServerError("Redis connection failed")
      }
  }

  val httpApp = routes.orNotFound

  def run(args: List[String]): IO[ExitCode] = {
    val host = Host.fromString(EnvConfig.appHost).getOrElse(ipv4"0.0.0.0")
    val port = Port.fromInt(EnvConfig.appPort).getOrElse(port"8080")

    EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}
