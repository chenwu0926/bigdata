package com.whirly.realtime.job

import com.whirly.realtime.entity.{AccessLog, OnlineUserStat}
import com.whirly.realtime.util.{FlinkKafkaUtil, RedisUtil}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.ProcessAllWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.{SlidingEventTimeWindows, TumblingEventTimeWindows}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import scala.collection.mutable

/**
 * Job3: 实时在线人数估算
 * 使用滑动窗口估算当前在线人数（基于IP去重）
 * 假设：如果一个 IP 在最近 5 分钟内有访问记录，则视为在线
 */
object OnlineUserCountJob {

  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    // 从 Kafka 读取数据
    val accessLogStream = FlinkKafkaUtil.createKafkaStream(env)

    // 过滤有效日志
    val validLogStream = accessLogStream
      .filter(log => log.ip.nonEmpty)

    // ========== 方式1: 滑动窗口（5分钟窗口，1分钟滑动）统计在线人数 ==========
    val onlineUserStream = validLogStream
      .windowAll(SlidingEventTimeWindows.of(Time.minutes(5), Time.minutes(1)))
      .process(new OnlineUserProcessFunction)

    // ========== 方式2: 1分钟窗口统计活跃用户 ==========
    val activeUserStream = validLogStream
      .windowAll(TumblingEventTimeWindows.of(Time.minutes(1)))
      .process(new OnlineUserProcessFunction)

    // 输出滑动窗口结果（估算在线人数）
    onlineUserStream.addSink { stat =>
      val startTime = formatTime(stat.windowStart)
      val endTime = formatTime(stat.windowEnd)
      println(s"[在线人数] 窗口: $startTime - $endTime, 在线用户: ${stat.onlineCount}")
      RedisUtil.saveOnlineCount(stat.windowEnd, stat.onlineCount)
    }

    // 输出1分钟窗口结果（活跃用户）
    activeUserStream.addSink { stat =>
      val time = formatTime(stat.windowEnd)
      println(s"[活跃用户] 时间: $time, 活跃用户: ${stat.onlineCount}")
    }

    env.execute("Online User Count Job")
  }

  private def formatTime(timestamp: Long): String = {
    val sdf = new java.text.SimpleDateFormat("HH:mm:ss")
    sdf.format(new java.util.Date(timestamp))
  }
}

/**
 * 在线用户统计处理函数
 * 对窗口内的所有 IP 进行去重统计
 */
class OnlineUserProcessFunction extends ProcessAllWindowFunction[AccessLog, OnlineUserStat, TimeWindow] {

  override def process(context: Context,
                       elements: Iterable[AccessLog],
                       out: Collector[OnlineUserStat]): Unit = {
    // 使用 Set 对 IP 进行去重
    val uniqueIps = mutable.Set[String]()
    elements.foreach { log =>
      uniqueIps.add(log.ip)
    }

    out.collect(OnlineUserStat(
      windowStart = context.window.getStart,
      windowEnd = context.window.getEnd,
      onlineCount = uniqueIps.size.toLong
    ))
  }
}
