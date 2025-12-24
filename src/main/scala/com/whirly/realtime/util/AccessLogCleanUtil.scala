package com.whirly.realtime.util

import com.whirly.realtime.entity.AccessLog
import com.whirly.util.{IpUtil, RegexUtil}
import org.apache.commons.lang3.StringUtils

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

/**
 * 实时数据清洗工具类
 * 与离线批处理保持一致的清洗逻辑
 */
object AccessLogCleanUtil {

  // 内网 IP 黑名单
  private val INTERNAL_IP_LIST = Set(
    "10.100.0.1",
    "127.0.0.1",
    "0.0.0.0"
  )

  // 有效的 cmsType 类型
  private val VALID_CMS_TYPES = Set("video", "code", "learn", "article", "course", "ceping")

  // 慕课网域名
  private val IMOOC_DOMAIN = "http://www.imooc.com/"

  // 原始日志时间格式: [10/Nov/2016:00:01:02 +0800]
  private val RAW_DATE_FORMAT = new SimpleDateFormat("[dd/MMM/yyyy:HH:mm:ss Z]", Locale.ENGLISH)
  // 标准时间格式
  private val STANDARD_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  /**
   * 清洗原始 Nginx 日志
   * 原始格式: 183.162.52.7 - - [10/Nov/2016:00:01:02 +0800] "POST /api3/getadv HTTP/1.1" 200 813 "www.imooc.com" "-" ...
   *
   * @param rawLog 原始日志行
   * @return Option[AccessLog] 清洗后的日志，无效数据返回 None
   */
  def cleanRawNginxLog(rawLog: String): Option[AccessLog] = {
    try {
      if (rawLog == null || rawLog.isEmpty) return None

      val splits = rawLog.split(" ")
      if (splits.length < 12) return None

      // 1. 提取 IP
      val ip = splits(0)

      // 2. 过滤内网 IP
      if (isInternalIp(ip)) return None

      // 3. 提取并转换时间
      val rawTime = splits(3) + " " + splits(4)
      val time = parseRawTime(rawTime)
      if (time <= 0) return None

      // 4. 提取 URL，过滤无效 URL
      val url = splits(11).replaceAll("\"", "")
      if (url == "-" || url.isEmpty) return None

      // 5. 提取流量
      val traffic = try {
        splits(9).toLong
      } catch {
        case _: Exception => 0L
      }

      // 6. 解析 URL，提取 cmsType 和 cmsId
      val (cmsType, cmsId) = parseCmsFromUrl(url)

      // 7. IP 解析城市
      val city = IpUtil.findRegionByIp(ip)

      Some(AccessLog(url, cmsType, cmsId, traffic, ip, city, time))
    } catch {
      case e: Exception =>
        println(s"[DataClean] 清洗失败: ${e.getMessage}, log: $rawLog")
        None
    }
  }

  /**
   * 清洗已格式化的日志（第一步清洗后的格式）
   * 格式: 2016-11-10 00:01:27\thttp://www.imooc.com/video/6689\t320\t14.153.236.58
   *
   * @param formattedLog 格式化后的日志
   * @return Option[AccessLog]
   */
  def cleanFormattedLog(formattedLog: String): Option[AccessLog] = {
    try {
      if (formattedLog == null || formattedLog.isEmpty) return None

      val splits = formattedLog.split("\t")
      if (splits.length < 4) return None

      val timeStr = splits(0)
      val url = splits(1)
      val trafficStr = splits(2)
      val ip = splits(3)

      // 过滤内网 IP
      if (isInternalIp(ip)) return None

      // 过滤无效 URL
      if (url == "-" || url.isEmpty) return None

      // 解析时间
      val time = try {
        STANDARD_DATE_FORMAT.parse(timeStr).getTime
      } catch {
        case _: Exception => System.currentTimeMillis()
      }

      // 解析流量
      val traffic = try {
        trafficStr.toLong
      } catch {
        case _: Exception => 0L
      }

      // 解析 cmsType 和 cmsId
      val (cmsType, cmsId) = parseCmsFromUrl(url)

      // IP 解析城市
      val city = IpUtil.findRegionByIp(ip)

      Some(AccessLog(url, cmsType, cmsId, traffic, ip, city, time))
    } catch {
      case e: Exception =>
        None
    }
  }

  /**
   * 清洗 CSV 格式日志（已清洗过的数据）
   * 格式: url,cmsType,cmsId,traffic,ip,city,time
   *
   * @param csvLog CSV 格式日志
   * @return Option[AccessLog]
   */
  def cleanCsvLog(csvLog: String): Option[AccessLog] = {
    try {
      if (csvLog == null || csvLog.isEmpty) return None

      val parts = csvLog.split(",", -1)
      if (parts.length < 7) return None

      val url = parts(0).trim
      val cmsType = parts(1).trim
      val cmsId = try { parts(2).trim.toLong } catch { case _: Exception => 0L }
      val traffic = try { parts(3).trim.toLong } catch { case _: Exception => 0L }
      val ip = parts(4).trim
      val city = parts(5).trim
      val time = try { parts(6).trim.toLong } catch { case _: Exception => System.currentTimeMillis() }

      // 过滤内网 IP
      if (isInternalIp(ip)) return None

      // 过滤无效数据
      if (url.isEmpty || cmsType.isEmpty) return None

      // 过滤 cmsId <= 0 的记录（可选，某些统计需要）
      // if (cmsId <= 0) return None

      Some(AccessLog(url, cmsType, cmsId, traffic, ip, city, time))
    } catch {
      case _: Exception => None
    }
  }

  /**
   * 对 AccessLog 进行验证和补充清洗
   *
   * @param log AccessLog 对象
   * @return Option[AccessLog] 验证通过返回 Some，否则返回 None
   */
  def validateAndClean(log: AccessLog): Option[AccessLog] = {
    // 过滤内网 IP
    if (isInternalIp(log.ip)) return None

    // 过滤无效 URL
    if (log.url == null || log.url.isEmpty || log.url == "-") return None

    // 过滤无效时间戳
    if (log.time <= 0) return None

    // 过滤无效 cmsType（如果需要）
    // if (!VALID_CMS_TYPES.contains(log.cmsType)) return None

    // 过滤无效 cmsId（如果需要）
    // if (log.cmsId <= 0) return None

    Some(log)
  }

  /**
   * 判断是否为内网 IP
   */
  def isInternalIp(ip: String): Boolean = {
    if (ip == null || ip.isEmpty) return true

    // 检查黑名单
    if (INTERNAL_IP_LIST.contains(ip)) return true

    // 检查私有 IP 段
    if (ip.startsWith("10.") ||
        ip.startsWith("192.168.") ||
        ip.startsWith("172.16.") ||
        ip.startsWith("172.17.") ||
        ip.startsWith("172.18.") ||
        ip.startsWith("172.19.") ||
        ip.startsWith("172.20.") ||
        ip.startsWith("172.21.") ||
        ip.startsWith("172.22.") ||
        ip.startsWith("172.23.") ||
        ip.startsWith("172.24.") ||
        ip.startsWith("172.25.") ||
        ip.startsWith("172.26.") ||
        ip.startsWith("172.27.") ||
        ip.startsWith("172.28.") ||
        ip.startsWith("172.29.") ||
        ip.startsWith("172.30.") ||
        ip.startsWith("172.31.")) {
      return true
    }

    false
  }

  /**
   * 解析原始日志时间格式
   * [10/Nov/2016:00:01:02 +0800] => 时间戳
   */
  private def parseRawTime(rawTime: String): Long = {
    try {
      RAW_DATE_FORMAT.parse(rawTime).getTime
    } catch {
      case _: Exception => 0L
    }
  }

  /**
   * 从 URL 解析 cmsType 和 cmsId
   * http://www.imooc.com/video/6689 => (video, 6689)
   */
  def parseCmsFromUrl(url: String): (String, Long) = {
    if (url == null || url.isEmpty) return ("", 0L)

    try {
      val domainIndex = url.indexOf(IMOOC_DOMAIN)
      if (domainIndex >= 0) {
        val cms = url.substring(domainIndex + IMOOC_DOMAIN.length)
        val cmsTypeId = cms.split("/")

        if (cmsTypeId.length > 1) {
          val cmsType = cmsTypeId(0)
          var cmsId = 0L

          if ("video".equals(cmsType) || "code".equals(cmsType) || "learn".equals(cmsType)) {
            try {
              // 处理可能带参数的情况: 6689?src=xxx
              val idStr = cmsTypeId(1).split("[?#]")(0)
              cmsId = idStr.toLong
            } catch {
              case _: Exception => cmsId = 0L
            }
          } else if ("article".equals(cmsType)) {
            // article 类型使用正则提取开头数字
            val number = RegexUtil.findStartNumber(cmsTypeId(1))
            if (StringUtils.isNotEmpty(number)) {
              cmsId = number.toLong
            }
          }

          return (cmsType, cmsId)
        } else if (cmsTypeId.length == 1) {
          return (cmsTypeId(0), 0L)
        }
      }
    } catch {
      case _: Exception =>
    }

    ("", 0L)
  }

  /**
   * 判断 IP 格式是否合法
   */
  def isValidIp(ip: String): Boolean = {
    if (ip == null || ip.isEmpty) return false

    val ipPattern = """^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""".r
    ip match {
      case ipPattern(a, b, c, d) =>
        try {
          val parts = Array(a.toInt, b.toInt, c.toInt, d.toInt)
          parts.forall(p => p >= 0 && p <= 255)
        } catch {
          case _: Exception => false
        }
      case _ => false
    }
  }

  /**
   * 获取日期分区字符串
   * 时间戳 => 20161110
   */
  def getDayPartition(timestamp: Long): String = {
    val sdf = new SimpleDateFormat("yyyyMMdd")
    sdf.format(new Date(timestamp))
  }
}
