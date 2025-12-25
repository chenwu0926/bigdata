package com.whirly.realtime.util

import com.whirly.realtime.config.FlinkConfig
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

import java.text.SimpleDateFormat
import java.util.Date

/**
 * Redis 工具类
 * 用于存储实时统计结果
 */
object RedisUtil {

  private var jedisPool: JedisPool = _

  // 时间格式化器：用于 Redis Key
  private val keyDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm")

  /**
   * 将时间戳转换为可读格式
   * 例如：1766598240000 -> "2025-12-24_21:44"
   */
  def formatWindowTime(timestamp: Long): String = {
    keyDateFormat.format(new Date(timestamp))
  }

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
   * Key: realtime:pv:uv:{cmsType}:{cmsId}:{formattedTime}
   * 例如: realtime:pv:uv:video:123:2025-12-24_21:44
   */
  def savePvUv(cmsType: String, cmsId: Long, windowEnd: Long, pv: Long, uv: Long): Unit = {
    val jedis = getResource
    try {
      val timeStr = formatWindowTime(windowEnd)
      val key = s"realtime:pv:uv:$cmsType:$cmsId:$timeStr"
      jedis.hset(key, "pv", pv.toString)
      jedis.hset(key, "uv", uv.toString)
      jedis.expire(key, 3600L * 24)  // 24小时过期
    } finally {
      jedis.close()
    }
  }

  /**
   * 存储热门课程 TopN
   * Key: realtime:topn:{cmsType}:{formattedTime}
   * 例如: realtime:topn:video:2025-12-24_21:44
   */
  def saveTopN(cmsType: String, windowEnd: Long, cmsId: Long, score: Long): Unit = {
    val jedis = getResource
    try {
      val timeStr = formatWindowTime(windowEnd)
      val key = s"realtime:topn:$cmsType:$timeStr"
      jedis.zadd(key, score.toDouble, cmsId.toString)
      jedis.expire(key, 3600L * 24)  // 24小时过期
    } finally {
      jedis.close()
    }
  }

  /**
   * 存储在线人数
   * Key: realtime:online:{formattedTime}
   * 例如: realtime:online:2025-12-24_21:44
   */
  def saveOnlineCount(windowEnd: Long, count: Long): Unit = {
    val jedis = getResource
    try {
      val timeStr = formatWindowTime(windowEnd)
      val key = s"realtime:online:$timeStr"
      jedis.set(key, count.toString)
      jedis.expire(key, 3600L * 24)  // 24小时过期
    } finally {
      jedis.close()
    }
  }
}
