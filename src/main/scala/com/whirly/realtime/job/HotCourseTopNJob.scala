package com.whirly.realtime.job

import com.whirly.realtime.config.FlinkConfig
import com.whirly.realtime.entity.{AccessLog, HotCourseStat}
import com.whirly.realtime.util.{FlinkKafkaUtil, RedisUtil}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import scala.collection.JavaConverters._

/**
 * Job2: 实时热门课程 TopN
 * 使用滑动窗口（5分钟窗口，1分钟滑动步长）统计热门课程
 */
object HotCourseTopNJob {

  // 中间结果：课程访问计数
  case class CourseAccessCount(cmsType: String, cmsId: Long, windowEnd: Long, count: Long)

  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    // 从 Kafka 读取数据
    val accessLogStream = FlinkKafkaUtil.createKafkaStream(env)

    // 过滤有效日志
    val validLogStream = accessLogStream
      .filter(log => log.cmsId > 0 && log.cmsType.nonEmpty)

    // 按 (cmsType, cmsId) 分组，滑动窗口聚合
    val courseCountStream = validLogStream
      .keyBy(log => (log.cmsType, log.cmsId))
      .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.minutes(1)))
      .aggregate(new CountAggregator, new CourseCountWindowFunction)

    // 按窗口结束时间分组，收集所有课程访问量后计算 TopN
    val topNStream = courseCountStream
      .keyBy(_.windowEnd)
      .process(new TopNProcessFunction(FlinkConfig.TOP_N))

    // 输出结果
    topNStream.addSink { statList =>
      if (statList.nonEmpty) {
        println(s"\n========== TopN 热门课程 (窗口: ${formatTime(statList.head.windowEnd)}) ==========")
        statList.foreach { stat =>
          println(s"  Top ${stat.rank}: ${stat.cmsType}/${stat.cmsId} - 访问量: ${stat.accessCount}")
          // 保存到 Redis
          RedisUtil.saveTopN(stat.cmsType, stat.windowEnd, stat.cmsId, stat.accessCount)
        }
      }
    }

    env.execute("Hot Course TopN Job")
  }

  private def formatTime(timestamp: Long): String = {
    val sdf = new java.text.SimpleDateFormat("HH:mm:ss")
    sdf.format(new java.util.Date(timestamp))
  }
}

/**
 * 计数聚合器
 */
class CountAggregator extends AggregateFunction[AccessLog, Long, Long] {
  override def createAccumulator(): Long = 0L
  override def add(value: AccessLog, accumulator: Long): Long = accumulator + 1
  override def getResult(accumulator: Long): Long = accumulator
  override def merge(a: Long, b: Long): Long = a + b
}

/**
 * 窗口函数 - 添加窗口信息
 */
class CourseCountWindowFunction extends WindowFunction[Long, HotCourseTopNJob.CourseAccessCount, (String, Long), TimeWindow] {

  override def apply(key: (String, Long),
                     window: TimeWindow,
                     input: Iterable[Long],
                     out: Collector[HotCourseTopNJob.CourseAccessCount]): Unit = {
    out.collect(HotCourseTopNJob.CourseAccessCount(
      cmsType = key._1,
      cmsId = key._2,
      windowEnd = window.getEnd,
      count = input.head
    ))
  }
}

/**
 * TopN 处理函数
 * 收集同一窗口内的所有课程访问计数，然后排序取 TopN
 */
class TopNProcessFunction(topN: Int)
    extends KeyedProcessFunction[Long, HotCourseTopNJob.CourseAccessCount, List[HotCourseStat]] {

  private var courseListState: ListState[HotCourseTopNJob.CourseAccessCount] = _

  override def open(parameters: Configuration): Unit = {
    val descriptor = new ListStateDescriptor[HotCourseTopNJob.CourseAccessCount](
      "course-list",
      classOf[HotCourseTopNJob.CourseAccessCount]
    )
    courseListState = getRuntimeContext.getListState(descriptor)
  }

  override def processElement(value: HotCourseTopNJob.CourseAccessCount,
                              ctx: KeyedProcessFunction[Long, HotCourseTopNJob.CourseAccessCount, List[HotCourseStat]]#Context,
                              out: Collector[List[HotCourseStat]]): Unit = {
    // 将课程访问计数添加到状态
    courseListState.add(value)

    // 注册定时器，在窗口结束时间+1ms 触发
    ctx.timerService().registerEventTimeTimer(value.windowEnd + 1)
  }

  override def onTimer(timestamp: Long,
                       ctx: KeyedProcessFunction[Long, HotCourseTopNJob.CourseAccessCount, List[HotCourseStat]]#OnTimerContext,
                       out: Collector[List[HotCourseStat]]): Unit = {
    // 获取所有课程访问计数
    val allCourses = courseListState.get().asScala.toList

    // 按访问量降序排序，取 TopN
    val topNCourses = allCourses
      .sortBy(-_.count)
      .take(topN)
      .zipWithIndex
      .map { case (course, idx) =>
        HotCourseStat(
          windowStart = timestamp - 1 - 5 * 60 * 1000,  // 窗口开始时间
          windowEnd = timestamp - 1,
          cmsType = course.cmsType,
          cmsId = course.cmsId,
          accessCount = course.count,
          rank = idx + 1
        )
      }

    // 清空状态
    courseListState.clear()

    out.collect(topNCourses)
  }
}
