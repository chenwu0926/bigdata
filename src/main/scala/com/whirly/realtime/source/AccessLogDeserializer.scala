package com.whirly.realtime.source

import com.alibaba.fastjson.JSON
import com.whirly.realtime.entity.AccessLog
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat

/**
 * Kafka 消息反序列化器
 * 支持两种格式:
 * 1. CSV 格式: url,cmsType,cmsId,traffic,ip,city,time
 * 2. JSON 格式
 */
class AccessLogDeserializer extends DeserializationSchema[AccessLog] {

  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  override def deserialize(message: Array[Byte]): AccessLog = {
    val line = new String(message, StandardCharsets.UTF_8)

    try {
      if (line.startsWith("{")) {
        // JSON 格式
        parseJson(line)
      } else {
        // CSV 格式
        parseCsv(line)
      }
    } catch {
      case e: Exception =>
        // 解析失败返回空日志
        AccessLog("", "", 0L, 0L, "", "", 0L)
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
      val time = try {
        dateFormat.parse(timeStr).getTime
      } catch {
        case _: Exception => System.currentTimeMillis()
      }

      AccessLog(url, cmsType, cmsId, traffic, ip, city, time)
    } else {
      AccessLog("", "", 0L, 0L, "", "", 0L)
    }
  }

  private def parseJson(line: String): AccessLog = {
    val json = JSON.parseObject(line)
    AccessLog(
      url = json.getString("url"),
      cmsType = json.getString("cmsType"),
      cmsId = json.getLongValue("cmsId"),
      traffic = json.getLongValue("traffic"),
      ip = json.getString("ip"),
      city = json.getString("city"),
      time = json.getLongValue("time")
    )
  }

  override def isEndOfStream(nextElement: AccessLog): Boolean = false

  override def getProducedType: TypeInformation[AccessLog] = {
    TypeInformation.of(classOf[AccessLog])
  }
}
