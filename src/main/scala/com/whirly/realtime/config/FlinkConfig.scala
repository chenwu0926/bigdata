package com.whirly.realtime.config

/**
 * Flink 实时计算配置
 * 支持从环境变量读取配置，方便 Docker 部署
 */
object FlinkConfig {

  private def getEnv(key: String, default: String): String = {
    Option(System.getenv(key)).getOrElse(default)
  }

  private def getEnvInt(key: String, default: Int): Int = {
    try {
      Option(System.getenv(key)).map(_.toInt).getOrElse(default)
    } catch {
      case _: NumberFormatException => default
    }
  }

  // Kafka 配置
  val KAFKA_BOOTSTRAP_SERVERS: String = getEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  val KAFKA_GROUP_ID: String = getEnv("KAFKA_GROUP_ID", "imooc-log-consumer")
  val KAFKA_TOPIC: String = getEnv("KAFKA_TOPIC", "imooc-access-log")

  // Flink 配置
  val CHECKPOINT_INTERVAL: Long = 60000L  // 60秒
  val CHECKPOINT_DIR: String = getEnv("CHECKPOINT_DIR", "file:///tmp/flink/checkpoints")

  // 窗口配置
  val WINDOW_SIZE_1MIN: Long = 60L   // 1分钟窗口（秒）
  val WINDOW_SIZE_5MIN: Long = 300L  // 5分钟窗口（秒）

  // Redis 配置
  val REDIS_HOST: String = getEnv("REDIS_HOST", "localhost")
  val REDIS_PORT: Int = getEnvInt("REDIS_PORT", 6379)
  val REDIS_DB: Int = getEnvInt("REDIS_DB", 0)

  // MySQL 配置
  val MYSQL_HOST: String = getEnv("MYSQL_HOST", "localhost")
  val MYSQL_PORT: Int = getEnvInt("MYSQL_PORT", 3306)
  val MYSQL_DATABASE: String = getEnv("MYSQL_DATABASE", "imooc_log")
  val MYSQL_URL: String = getEnv("MYSQL_URL",
    s"jdbc:mysql://$MYSQL_HOST:$MYSQL_PORT/$MYSQL_DATABASE?useSSL=false&characterEncoding=UTF-8")
  val MYSQL_USER: String = getEnv("MYSQL_USER", "root")
  val MYSQL_PASSWORD: String = getEnv("MYSQL_PASSWORD", "123456")

  // TopN 配置
  val TOP_N: Int = getEnvInt("TOP_N", 10)

  def printConfig(): Unit = {
    println("========== Flink Config ==========")
    println(s"Kafka: $KAFKA_BOOTSTRAP_SERVERS")
    println(s"Kafka Topic: $KAFKA_TOPIC")
    println(s"Redis: $REDIS_HOST:$REDIS_PORT")
    println(s"MySQL: $MYSQL_HOST:$MYSQL_PORT/$MYSQL_DATABASE")
    println("==================================")
  }
}
