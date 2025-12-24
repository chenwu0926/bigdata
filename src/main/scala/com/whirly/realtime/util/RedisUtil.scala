package com.whirly.realtime.util

import com.whirly.realtime.config.FlinkConfig
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

/**
 * Redis 工具类
 * 用于存储实时统计结果
 */
object RedisUtil {

  private var jedisPool: JedisPool = _

  def getJedisPool: JedisPool = {
    if (jedisPool == null) {
      synchronized {
        if (jedisPool == null) {
          val config = new JedisPoolConfig()
          config.setMaxTotal(100)
          config.setMaxIdle(20)
          config.setMinIdle(5)
          config.setTestOnBorrow(true)
          config.setTestOnReturn(true)
          jedisPool = new JedisPool(config, FlinkConfig.REDIS_HOST, FlinkConfig.REDIS_PORT, 5000)
        }
      }
    }
    jedisPool
  }

  def getResource: Jedis = {
    getJedisPool.getResource
  }

  /**
   * 存储 PV/UV 统计结果
   * Key: realtime:pv:uv:{cmsType}:{cmsId}:{windowEnd}
   */
  def savePvUv(cmsType: String, cmsId: Long, windowEnd: Long, pv: Long, uv: Long): Unit = {
    val jedis = getResource
    try {
      val key = s"realtime:pv:uv:$cmsType:$cmsId:$windowEnd"
      jedis.hset(key, "pv", pv.toString)
      jedis.hset(key, "uv", uv.toString)
      jedis.expire(key, 3600L * 24)  // 24小时过期
    } finally {
      jedis.close()
    }
  }

  /**
   * 存储热门课程 TopN
   * Key: realtime:topn:{cmsType}:{windowEnd}
   */
  def saveTopN(cmsType: String, windowEnd: Long, cmsId: Long, score: Long): Unit = {
    val jedis = getResource
    try {
      val key = s"realtime:topn:$cmsType:$windowEnd"
      jedis.zadd(key, score.toDouble, cmsId.toString)
      jedis.expire(key, 3600L * 24)  // 24小时过期
    } finally {
      jedis.close()
    }
  }

  /**
   * 存储在线人数
   * Key: realtime:online:{windowEnd}
   */
  def saveOnlineCount(windowEnd: Long, count: Long): Unit = {
    val jedis = getResource
    try {
      val key = s"realtime:online:$windowEnd"
      jedis.set(key, count.toString)
      jedis.expire(key, 3600L * 24)  // 24小时过期
    } finally {
      jedis.close()
    }
  }
}
