package com.whirly.realtime.job

import com.alibaba.fastjson.JSON
import com.whirly.realtime.config.FlinkConfig
import com.whirly.realtime.entity.AccessLog
import com.whirly.realtime.source.RawLogDeserializer
import com.whirly.realtime.util.AccessLogCleanUtil
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.connector.base.DeliveryGuarantee
import org.apache.flink.connector.kafka.sink.{KafkaRecordSerializationSchema, KafkaSink}
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.scala._

/**
 * 实时数据清洗作业
 *
 * 功能：
 * 1. 从 Kafka 读取原始日志数据
 * 2. 进行数据清洗（与离线批处理保持一致）
 * 3. 将清洗后的数据写入另一个 Kafka Topic
 *
 * 清洗规则：
 * - 过滤内网 IP (10.x.x.x, 192.168.x.x, 172.16-31.x.x)
 * - 过滤无效 URL (-)
 * - 解析 URL 提取 cmsType 和 cmsId
 * - IP 地址解析为城市
 * - 时间格式标准化
 * - 过滤异常数据
 *
 * 数据流：
 * raw-access-log (原始日志) -> DataCleanJob -> imooc-access-log (清洗后)
 */
object DataCleanJob {

  // 原始日志 Topic
  val RAW_TOPIC: String = sys.env.getOrElse("RAW_KAFKA_TOPIC", "raw-access-log")
  // 清洗后 Topic
  val CLEANED_TOPIC: String = FlinkConfig.KAFKA_TOPIC

  def main(args: Array[String]): Unit = {
    // 创建执行环境
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(2)

    // 打印配置
    println("========== Data Clean Job Config ==========")
    println(s"Kafka Servers: ${FlinkConfig.KAFKA_BOOTSTRAP_SERVERS}")
    println(s"Raw Topic: $RAW_TOPIC")
    println(s"Cleaned Topic: $CLEANED_TOPIC")
    println("============================================")

    // 创建 Kafka Source（读取原始日志）
    val kafkaSource = KafkaSource.builder[AccessLog]()
      .setBootstrapServers(FlinkConfig.KAFKA_BOOTSTRAP_SERVERS)
      .setTopics(RAW_TOPIC)
      .setGroupId(FlinkConfig.KAFKA_GROUP_ID + "-cleaner")
      .setStartingOffsets(OffsetsInitializer.latest())
      .setValueOnlyDeserializer(new RawLogDeserializer())
      .build()

    // 读取原始日志流
    val rawLogStream = env.fromSource(
      kafkaSource,
      WatermarkStrategy.noWatermarks(),
      "Raw Log Kafka Source"
    )

    // 数据清洗：过滤无效数据
    val cleanedStream = rawLogStream
      // 过滤空日志（解析失败的数据）
      .filter(log => log.url.nonEmpty && log.time > 0)
      // 过滤内网 IP
      .filter(log => !AccessLogCleanUtil.isInternalIp(log.ip))
      // 过滤无效 URL
      .filter(log => log.url != "-")
      // 验证 IP 格式
      .filter(log => AccessLogCleanUtil.isValidIp(log.ip))

    // 统计清洗结果
    cleanedStream.map(log => {
      println(s"[Cleaned] ${log.cmsType}/${log.cmsId} - ${log.ip} - ${log.city}")
      log
    })

    // 创建 Kafka Sink（输出清洗后的数据）
    val kafkaSink = KafkaSink.builder[String]()
      .setBootstrapServers(FlinkConfig.KAFKA_BOOTSTRAP_SERVERS)
      .setRecordSerializer(
        KafkaRecordSerializationSchema.builder()
          .setTopic(CLEANED_TOPIC)
          .setValueSerializationSchema(new SimpleStringSchema())
          .build()
      )
      .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
      .build()

    // 转换为 JSON 并写入 Kafka
    cleanedStream
      .map(log => accessLogToJson(log))
      .sinkTo(kafkaSink)

    // 同时输出清洗统计到控制台（可选）
    // printCleanStats(rawLogStream, cleanedStream)

    env.execute("Realtime Data Clean Job")
  }

  /**
   * AccessLog 转换为 JSON 字符串
   */
  private def accessLogToJson(log: AccessLog): String = {
    val json = new java.util.HashMap[String, Any]()
    json.put("url", log.url)
    json.put("cmsType", log.cmsType)
    json.put("cmsId", log.cmsId)
    json.put("traffic", log.traffic)
    json.put("ip", log.ip)
    json.put("city", log.city)
    json.put("time", log.time)
    JSON.toJSONString(json, false)
  }
}
