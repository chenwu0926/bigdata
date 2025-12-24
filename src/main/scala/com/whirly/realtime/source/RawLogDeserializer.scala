package com.whirly.realtime.source

import com.whirly.realtime.entity.AccessLog
import com.whirly.realtime.util.AccessLogCleanUtil
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation

import java.nio.charset.StandardCharsets

/**
 * 原始 Nginx 日志反序列化器（带清洗）
 *
 * 支持三种日志格式，自动识别并清洗：
 * 1. 原始 Nginx 日志: 183.162.52.7 - - [10/Nov/2016:00:01:02 +0800] "POST /api HTTP/1.1" 200 813 "www.imooc.com" ...
 * 2. 格式化日志: 2016-11-10 00:01:27\thttp://www.imooc.com/video/6689\t320\t14.153.236.58
 * 3. CSV 格式: url,cmsType,cmsId,traffic,ip,city,time
 */
class RawLogDeserializer extends DeserializationSchema[AccessLog] {

  override def deserialize(message: Array[Byte]): AccessLog = {
    val line = new String(message, StandardCharsets.UTF_8).trim

    if (line.isEmpty) {
      return createEmptyLog()
    }

    // 根据日志格式自动选择解析方式
    val result: Option[AccessLog] = detectAndParse(line)

    result.getOrElse(createEmptyLog())
  }

  /**
   * 自动检测日志格式并解析
   */
  private def detectAndParse(line: String): Option[AccessLog] = {
    if (line.contains(" - - [") && line.contains("HTTP/")) {
      // 原始 Nginx 日志格式
      AccessLogCleanUtil.cleanRawNginxLog(line)
    } else if (line.contains("\t") && line.split("\t").length >= 4) {
      // Tab 分隔的格式化日志
      AccessLogCleanUtil.cleanFormattedLog(line)
    } else if (line.contains(",") && line.split(",").length >= 7) {
      // CSV 格式
      AccessLogCleanUtil.cleanCsvLog(line)
    } else if (line.startsWith("{")) {
      // JSON 格式，使用原有解析器逻辑
      parseJson(line)
    } else {
      // 尝试作为简单 CSV 解析
      AccessLogCleanUtil.cleanCsvLog(line)
    }
  }

  /**
   * 解析 JSON 格式日志
   */
  private def parseJson(line: String): Option[AccessLog] = {
    try {
      import com.alibaba.fastjson.JSON
      val json = JSON.parseObject(line)

      val log = AccessLog(
        url = json.getString("url"),
        cmsType = json.getString("cmsType"),
        cmsId = json.getLongValue("cmsId"),
        traffic = json.getLongValue("traffic"),
        ip = json.getString("ip"),
        city = json.getString("city"),
        time = json.getLongValue("time")
      )

      // 验证并清洗
      AccessLogCleanUtil.validateAndClean(log)
    } catch {
      case _: Exception => None
    }
  }

  /**
   * 创建空日志对象（用于过滤）
   */
  private def createEmptyLog(): AccessLog = {
    AccessLog("", "", 0L, 0L, "", "", 0L)
  }

  override def isEndOfStream(nextElement: AccessLog): Boolean = false

  override def getProducedType: TypeInformation[AccessLog] = {
    TypeInformation.of(classOf[AccessLog])
  }
}
