package com.whirly.realtime.source

import com.alibaba.fastjson.JSON
import com.whirly.realtime.entity.AccessLog
import com.whirly.realtime.util.AccessLogCleanUtil
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat

/**
 * Kafka 消息反序列化器（带数据清洗）
 * 支持两种格式:
 * 1. CSV 格式: url,cmsType,cmsId,traffic,ip,city,time
 * 2. JSON 格式
 *
 * 清洗规则（与离线批处理保持一致）：
 * - 过滤内网 IP
 * - 过滤无效 URL
 * - 验证数据完整性
 */
class AccessLogDeserializer extends DeserializationSchema[AccessLog] {

  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  override def deserialize(message: Array[Byte]): AccessLog = {
    val line = new String(message, StandardCharsets.UTF_8).trim

    if (line.isEmpty) {
      return createEmptyLog()
    }

    try {
      val log = if (line.startsWith("{")) {
        // JSON 格式
        parseJson(line)
      } else {
        // CSV 格式
        parseCsv(line)
      }

      // 数据清洗验证
      AccessLogCleanUtil.validateAndClean(log).getOrElse(createEmptyLog())
    } catch {
      case e: Exception =>
        // 解析失败返回空日志
        createEmptyLog()
    }
  }

  private def parseCsv(line: String): AccessLog = {
    val parts = line.split(",", -1)
    if (parts.length >= 7) {
      val url = parts(0).trim
      val cmsType = parts(1).trim
      val cmsId = try { parts(2).trim.toLong } catch { case _: Exception => 0L }
      val traffic = try { parts(3).trim.toLong } catch { case _: Exception => 0L }
      val ip = parts(4).trim
      val city = parts(5).trim
      val timeStr = parts(6).trim

      // 支持时间戳和日期字符串两种格式
      val time = try {
        if (timeStr.contains("-")) {
          dateFormat.parse(timeStr).getTime
        } else {
          timeStr.toLong
        }
      } catch {
        case _: Exception => System.currentTimeMillis()
      }

      AccessLog(url, cmsType, cmsId, traffic, ip, city, time)
    } else {
      createEmptyLog()
    }
  }

  private def parseJson(line: String): AccessLog = {
    val json = JSON.parseObject(line)
    AccessLog(
      url = Option(json.getString("url")).getOrElse(""),
      cmsType = Option(json.getString("cmsType")).getOrElse(""),
      cmsId = json.getLongValue("cmsId"),
      traffic = json.getLongValue("traffic"),
      ip = Option(json.getString("ip")).getOrElse(""),
      city = Option(json.getString("city")).getOrElse(""),
      time = json.getLongValue("time")
    )
  }

  private def createEmptyLog(): AccessLog = {
    AccessLog("", "", 0L, 0L, "", "", 0L)
  }

  override def isEndOfStream(nextElement: AccessLog): Boolean = false

  override def getProducedType: TypeInformation[AccessLog] = {
    TypeInformation.of(classOf[AccessLog])
  }
}
