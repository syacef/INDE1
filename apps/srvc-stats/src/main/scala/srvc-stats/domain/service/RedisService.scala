package srvc_stats.domain.service
import redis.clients.jedis.{ JedisPoolConfig, JedisSentinelPool }
import srvc_stats.domain.entity.EnvConfig

import scala.jdk.CollectionConverters._

object RedisService {
  def createRedisPool(): JedisSentinelPool = {
    val poolConfig = new JedisPoolConfig()
    poolConfig.setTestOnBorrow(true)
    poolConfig.setTestOnReturn(true)
    poolConfig.setTestWhileIdle(true)
    poolConfig.setMinEvictableIdleTimeMillis(60000)
    poolConfig.setTimeBetweenEvictionRunsMillis(30000)

    new JedisSentinelPool(
      EnvConfig.redisMasterName,
      EnvConfig.redisHosts.asJava
    )
  }
}
