package com.whirly.realtime.job

import com.whirly.realtime.config.FlinkConfig
import com.whirly.realtime.entity.{AccessLog, PvUvStat}
import com.whirly.realtime.util.{FlinkKafkaUtil, RedisUtil}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import scala.collection.mutable

/**
 * Job1: 实时课程 PV/UV 统计
 * 按 1 分钟/5 分钟窗口聚合，统计每个课程的 PV（访问量）和 UV（独立访客）
 */
object PvUvStatJob {

  def main(args: Array[String]): Unit = {
    // 创建执行环境
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    // 从 Kafka 读取数据
    val accessLogStream = FlinkKafkaUtil.createKafkaStream(env)

    // 过滤有效日志（cmsId > 0 且 cmsType 非空）
    val validLogStream = accessLogStream
      .filter(log => log.cmsId > 0 && log.cmsType.nonEmpty)

    // ========== 1分钟窗口 PV/UV 统计 ==========
    val pvUv1MinStream = validLogStream
      .keyBy(log => (log.cmsType, log.cmsId))
      .window(TumblingEventTimeWindows.of(Time.minutes(1)))
      .aggregate(new PvUvAggregator, new PvUvProcessFunction)

    // ========== 5分钟窗口 PV/UV 统计 ==========
    val pvUv5MinStream = validLogStream
      .keyBy(log => (log.cmsType, log.cmsId))
      .window(TumblingEventTimeWindows.of(Time.minutes(5)))
      .aggregate(new PvUvAggregator, new PvUvProcessFunction)

    // 输出到 Redis
    pvUv1MinStream.addSink(stat => {
      RedisUtil.savePvUv(stat.cmsType, stat.cmsId, stat.windowEnd, stat.pv, stat.uv)
      println(s"[1min] ${stat.cmsType}/${stat.cmsId} - PV: ${stat.pv}, UV: ${stat.uv}")
    })

    pvUv5MinStream.addSink(stat => {
      RedisUtil.savePvUv(stat.cmsType, stat.cmsId, stat.windowEnd, stat.pv, stat.uv)
      println(s"[5min] ${stat.cmsType}/${stat.cmsId} - PV: ${stat.pv}, UV: ${stat.uv}")
    })

    env.execute("PV/UV Realtime Stat Job")
  }
}

/**
 * PV/UV 聚合器
 * 累加器: (PV计数, UV集合)
 */
class PvUvAggregator extends AggregateFunction[AccessLog, (Long, mutable.Set[String]), (Long, Long)] {

  override def createAccumulator(): (Long, mutable.Set[String]) = {
    (0L, mutable.Set[String]())
  }

  override def add(value: AccessLog, accumulator: (Long, mutable.Set[String])): (Long, mutable.Set[String]) = {
    accumulator._2.add(value.ip)  // UV: 使用 IP 去重
    (accumulator._1 + 1, accumulator._2)
  }

  override def getResult(accumulator: (Long, mutable.Set[String])): (Long, Long) = {
    (accumulator._1, accumulator._2.size.toLong)
  }

  override def merge(a: (Long, mutable.Set[String]), b: (Long, mutable.Set[String])): (Long, mutable.Set[String]) = {
    (a._1 + b._1, a._2 ++ b._2)
  }
}

/**
 * 窗口处理函数 - 添加窗口信息
 */
class PvUvProcessFunction extends ProcessWindowFunction[(Long, Long), PvUvStat, (String, Long), TimeWindow] {

  override def process(key: (String, Long),
                       context: Context,
                       elements: Iterable[(Long, Long)],
                       out: Collector[PvUvStat]): Unit = {
    val (pv, uv) = elements.head
    out.collect(PvUvStat(
      windowStart = context.window.getStart,
      windowEnd = context.window.getEnd,
      cmsType = key._1,
      cmsId = key._2,
      pv = pv,
      uv = uv
    ))
  }
}
