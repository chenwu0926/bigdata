package com.whirly.realtime.producer

import com.alibaba.fastjson.JSONObject
import com.whirly.realtime.config.FlinkConfig
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer

import java.text.SimpleDateFormat
import java.util.Properties
import scala.io.Source
import scala.util.Random

/**
 * Kafka 日志数据生产者
 * 有两种模式:
 * 1. 从 CSV 文件读取历史数据并发送（模拟回放）
 * 2. 随机生成日志数据（模拟实时产生）
 */
object LogDataProducer {

  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  private val random = new Random()

  // 模拟数据
  private val cmsTypes = Array("video", "code", "course", "article", "ceping")
  private val cities = Array("北京市", "上海市", "广东省", "浙江省", "江苏省", "四川省", "湖北省", "福建省", "山东省", "陕西省")

  def main(args: Array[String]): Unit = {
    val mode = if (args.length > 0) args(0) else "random"

    mode match {
      case "csv" =>
        // 从 CSV 文件读取并发送
        val csvPath = if (args.length > 1) args(1)
          else "/Users/chenwu/code/bigdata/data/imooc-log/cleanedCsv/day=20161110/part-00000-286a6aa8-f497-44a6-b27a-60c9bf6d67d6.c000.csv"
        sendFromCsv(csvPath)

      case "random" =>
        // 随机生成数据
        sendRandomData()

      case _ =>
        println("Usage: LogDataProducer [csv|random] [csvFilePath]")
    }
  }

  /**
   * 创建 Kafka Producer
   */
  private def createProducer(): KafkaProducer[String, String] = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, FlinkConfig.KAFKA_BOOTSTRAP_SERVERS)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
    props.put(ProducerConfig.ACKS_CONFIG, "1")
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384")
    props.put(ProducerConfig.LINGER_MS_CONFIG, "10")
    new KafkaProducer[String, String](props)
  }

  /**
   * 从 CSV 文件读取数据并发送到 Kafka
   * 会将历史时间戳转换为当前时间
   */
  private def sendFromCsv(csvPath: String): Unit = {
    println(s"从 CSV 文件读取数据: $csvPath")

    val producer = createProducer()
    val source = Source.fromFile(csvPath)
    var count = 0

    try {
      for (line <- source.getLines()) {
        val parts = line.split(",", -1)
        if (parts.length >= 7) {
          // 构造 JSON 消息，使用当前时间戳
          val json = new JSONObject()
          json.put("url", parts(0).trim)
          json.put("cmsType", parts(1).trim)
          json.put("cmsId", try { parts(2).trim.toLong } catch { case _: Exception => 0L })
          json.put("traffic", try { parts(3).trim.toLong } catch { case _: Exception => 0L })
          json.put("ip", parts(4).trim)
          json.put("city", parts(5).trim)
          json.put("time", System.currentTimeMillis())  // 使用当前时间

          val record = new ProducerRecord[String, String](
            FlinkConfig.KAFKA_TOPIC,
            parts(4).trim,  // 使用 IP 作为 key，保证同一 IP 的日志顺序
            json.toJSONString
          )

          producer.send(record)
          count += 1

          if (count % 100 == 0) {
            println(s"已发送 $count 条消息")
            Thread.sleep(100)  // 控制发送速度
          }
        }
      }
      println(s"CSV 数据发送完成，共 $count 条消息")
    } finally {
      source.close()
      producer.close()
    }
  }

  /**
   * 随机生成日志数据并持续发送到 Kafka
   */
  private def sendRandomData(): Unit = {
    println("开始随机生成日志数据...")

    val producer = createProducer()
    var count = 0L

    try {
      while (true) {
        // 生成随机日志
        val json = new JSONObject()
        val cmsType = cmsTypes(random.nextInt(cmsTypes.length))
        val cmsId = random.nextInt(10000) + 1
        val ip = generateRandomIp()

        json.put("url", s"http://www.imooc.com/$cmsType/$cmsId")
        json.put("cmsType", cmsType)
        json.put("cmsId", cmsId.toLong)
        json.put("traffic", (random.nextInt(10000) + 50).toLong)
        json.put("ip", ip)
        json.put("city", cities(random.nextInt(cities.length)))
        json.put("time", System.currentTimeMillis())

        val record = new ProducerRecord[String, String](
          FlinkConfig.KAFKA_TOPIC,
          ip,
          json.toJSONString
        )

        producer.send(record)
        count += 1

        if (count % 100 == 0) {
          println(s"[${dateFormat.format(System.currentTimeMillis())}] 已发送 $count 条消息")
        }

        // 控制发送速度：每秒约 50-100 条
        Thread.sleep(10 + random.nextInt(10))
      }
    } finally {
      producer.close()
    }
  }

  /**
   * 生成随机 IP 地址
   */
  private def generateRandomIp(): String = {
    // 生成有限的 IP 池，模拟真实的用户行为（同一用户多次访问）
    val ipPool = 500  // 模拟 500 个用户
    val userIndex = random.nextInt(ipPool)
    s"192.168.${userIndex / 256}.${userIndex % 256}"
  }
}
