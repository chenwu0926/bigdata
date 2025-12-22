package com.whirly.realtime.util

import com.whirly.realtime.config.FlinkConfig
import com.whirly.realtime.entity.AccessLog
import com.whirly.realtime.source.AccessLogDeserializer
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.scala._

import java.time.Duration

/**
 * Flink Kafka 工具类
 */
object FlinkKafkaUtil {

  /**
   * 创建 Kafka 数据源
   */
  def createKafkaSource(topic: String = FlinkConfig.KAFKA_TOPIC,
                        groupId: String = FlinkConfig.KAFKA_GROUP_ID,
                        servers: String = FlinkConfig.KAFKA_BOOTSTRAP_SERVERS): KafkaSource[AccessLog] = {
    KafkaSource.builder[AccessLog]()
      .setBootstrapServers(servers)
      .setTopics(topic)
      .setGroupId(groupId)
      .setStartingOffsets(OffsetsInitializer.latest())
      .setValueOnlyDeserializer(new AccessLogDeserializer())
      .build()
  }

  /**
   * 创建带水位线的 Kafka 数据流
   */
  def createKafkaStream(env: StreamExecutionEnvironment,
                        topic: String = FlinkConfig.KAFKA_TOPIC): DataStream[AccessLog] = {
    val kafkaSource = createKafkaSource(topic)

    // 水位线策略: 允许5秒乱序
    val watermarkStrategy = WatermarkStrategy
      .forBoundedOutOfOrderness[AccessLog](Duration.ofSeconds(5))
      .withTimestampAssigner(new SerializableTimestampAssigner[AccessLog] {
        override def extractTimestamp(element: AccessLog, recordTimestamp: Long): Long = {
          element.time
        }
      })
      .withIdleness(Duration.ofMinutes(1))  // 1分钟无数据时推进水位线

    env.fromSource(kafkaSource, watermarkStrategy, "Kafka Source")
  }
}
