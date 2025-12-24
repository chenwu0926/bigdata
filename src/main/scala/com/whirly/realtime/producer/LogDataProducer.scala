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
 * 用于模拟实时数据流
 *
 * 三种模式:
 * 1. csv     - 从 CSV 文件读取历史数据，转换时间戳后发送（模拟回放）
 * 2. random  - 随机生成日志数据（模拟实时产生）
 * 3. loop    - 循环读取 CSV 文件，持续发送（持续模拟）
 *
 * 使用方式:
 *   java -cp xxx.jar com.whirly.realtime.producer.LogDataProducer csv /path/to/file.csv
 *   java -cp xxx.jar com.whirly.realtime.producer.LogDataProducer random
 *   java -cp xxx.jar com.whirly.realtime.producer.LogDataProducer loop /path/to/file.csv
 */
object LogDataProducer {

  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  private val random = new Random()

  // 模拟数据
  private val cmsTypes = Array("video", "code", "course", "article", "ceping")
  private val cities = Array("北京市", "上海市", "广东省", "浙江省", "江苏省", "四川省", "湖北省", "福建省", "山东省", "陕西省")

  // 默认 CSV 文件路径
  private val DEFAULT_CSV_PATH = "/Users/chenwu/code/bigdata/data/imooc-log/cleanedCsv/day=20161110/part-00000-286a6aa8-f497-44a6-b27a-60c9bf6d67d6.c000.csv"

  // 发送速度控制（每秒发送的消息数）
  private val MESSAGES_PER_SECOND = 50

  def main(args: Array[String]): Unit = {
    val mode = if (args.length > 0) args(0) else "random"
    val csvPath = if (args.length > 1) args(1) else DEFAULT_CSV_PATH

    println("=" * 60)
    println("  慕课网日志数据生产者")
    println("=" * 60)
    println(s"  模式: $mode")
    println(s"  Kafka: ${FlinkConfig.KAFKA_BOOTSTRAP_SERVERS}")
    println(s"  Topic: ${FlinkConfig.KAFKA_TOPIC}")
    if (mode != "random") {
      println(s"  CSV文件: $csvPath")
    }
    println("=" * 60)

    mode match {
      case "csv" =>
        // 从 CSV 文件读取并发送（单次）
        sendFromCsv(csvPath, loop = false)

      case "loop" =>
        // 循环读取 CSV 文件并持续发送
        sendFromCsv(csvPath, loop = true)

      case "random" =>
        // 随机生成数据
        sendRandomData()

      case "test" =>
        // 快速测试模式：发送少量数据
        sendTestData()

      case _ =>
        printUsage()
    }
  }

  private def printUsage(): Unit = {
    println(
      """
        |使用方式:
        |  csv    [path]  - 从 CSV 文件读取数据，发送一次后退出
        |  loop   [path]  - 循环读取 CSV 文件，持续发送
        |  random         - 随机生成数据，持续发送
        |  test           - 快速测试，发送10条数据后退出
        |
        |示例:
        |  LogDataProducer csv /path/to/data.csv
        |  LogDataProducer loop
        |  LogDataProducer random
        |  LogDataProducer test
        |""".stripMargin)
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
   * 时间戳会转换为当前时间，模拟实时效果
   *
   * @param csvPath CSV 文件路径
   * @param loop    是否循环发送
   */
  private def sendFromCsv(csvPath: String, loop: Boolean): Unit = {
    val producer = createProducer()
    var totalCount = 0L
    var loopCount = 0

    try {
      do {
        loopCount += 1
        if (loop) {
          println(s"\n========== 第 $loopCount 轮发送 ==========")
        }

        val source = Source.fromFile(csvPath)
        var count = 0
        val startTime = System.currentTimeMillis()

        for (line <- source.getLines()) {
          val message = parseCsvLine(line)
          if (message != null) {
            val record = new ProducerRecord[String, String](
              FlinkConfig.KAFKA_TOPIC,
              message.getString("ip"),  // 使用 IP 作为 key
              message.toJSONString
            )

            producer.send(record)
            count += 1
            totalCount += 1

            // 控制发送速度
            if (count % MESSAGES_PER_SECOND == 0) {
              val elapsed = System.currentTimeMillis() - startTime
              val expectedTime = (count / MESSAGES_PER_SECOND) * 1000
              if (elapsed < expectedTime) {
                Thread.sleep(expectedTime - elapsed)
              }
              println(s"[${dateFormat.format(System.currentTimeMillis())}] 已发送 $count 条 (总计: $totalCount)")
            }
          }
        }

        source.close()
        println(s"本轮发送完成: $count 条，总计: $totalCount 条")

        if (loop) {
          println("等待 5 秒后开始下一轮...")
          Thread.sleep(5000)
        }

      } while (loop)

      println(s"\n发送完成！共发送 $totalCount 条消息")

    } finally {
      producer.close()
    }
  }

  /**
   * 解析 CSV 行并转换为 JSON
   * CSV 格式: url,cmsType,cmsId,traffic,ip,city,time
   */
  private def parseCsvLine(line: String): JSONObject = {
    try {
      val parts = line.split(",", -1)
      if (parts.length >= 6) {
        val json = new JSONObject()
        json.put("url", parts(0).trim)
        json.put("cmsType", parts(1).trim)
        json.put("cmsId", try { parts(2).trim.toLong } catch { case _: Exception => 0L })
        json.put("traffic", try { parts(3).trim.toLong } catch { case _: Exception => 0L })
        json.put("ip", parts(4).trim)
        json.put("city", parts(5).trim)
        // 使用当前时间戳，模拟实时数据
        json.put("time", System.currentTimeMillis())
        json
      } else {
        null
      }
    } catch {
      case _: Exception => null
    }
  }

  /**
   * 随机生成日志数据并持续发送到 Kafka
   */
  private def sendRandomData(): Unit = {
    println("开始随机生成日志数据... (按 Ctrl+C 停止)")

    val producer = createProducer()
    var count = 0L

    // 添加关闭钩子
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        println(s"\n\n程序终止，共发送 $count 条消息")
        producer.close()
      }
    })

    try {
      while (true) {
        val json = generateRandomLog()
        val record = new ProducerRecord[String, String](
          FlinkConfig.KAFKA_TOPIC,
          json.getString("ip"),
          json.toJSONString
        )

        producer.send(record)
        count += 1

        if (count % 100 == 0) {
          println(s"[${dateFormat.format(System.currentTimeMillis())}] 已发送 $count 条消息")
        }

        // 控制发送速度：约每秒 50-100 条
        Thread.sleep(1000 / MESSAGES_PER_SECOND + random.nextInt(10))
      }
    } finally {
      producer.close()
    }
  }

  /**
   * 快速测试模式：发送少量数据
   */
  private def sendTestData(): Unit = {
    println("快速测试模式：发送 10 条数据")

    val producer = createProducer()

    try {
      for (i <- 1 to 10) {
        val json = generateRandomLog()
        val record = new ProducerRecord[String, String](
          FlinkConfig.KAFKA_TOPIC,
          json.getString("ip"),
          json.toJSONString
        )

        producer.send(record)
        println(s"[$i] 发送: ${json.getString("cmsType")}/${json.getLong("cmsId")} - ${json.getString("city")}")
        Thread.sleep(200)
      }

      println("\n测试数据发送完成！")
      println("请检查 Redis 中的数据：")
      println("  docker exec redis redis-cli KEYS '*'")

    } finally {
      producer.close()
    }
  }

  /**
   * 生成随机日志
   */
  private def generateRandomLog(): JSONObject = {
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
    json
  }

  /**
   * 生成随机 IP 地址
   * 使用有限的 IP 池，模拟真实的用户行为（同一用户多次访问）
   */
  private def generateRandomIp(): String = {
    val ipPool = 500  // 模拟 500 个用户
    val userIndex = random.nextInt(ipPool)
    // 使用公网 IP 格式，避免被内网过滤
    val first = 60 + random.nextInt(180)  // 60-239
    val second = random.nextInt(256)
    s"$first.$second.${userIndex / 256}.${userIndex % 256}"
  }
}
