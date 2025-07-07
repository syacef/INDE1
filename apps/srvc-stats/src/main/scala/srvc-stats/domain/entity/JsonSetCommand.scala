package srvc_stats.domain.entity
import redis.clients.jedis.commands.ProtocolCommand
import redis.clients.jedis.util.SafeEncoder

object JsonSetCommand extends ProtocolCommand {
  override def getRaw: Array[Byte] = SafeEncoder.encode("JSON.SET")
}
