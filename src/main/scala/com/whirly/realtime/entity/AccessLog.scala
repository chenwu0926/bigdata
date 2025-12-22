package com.whirly.realtime.entity

/**
 * 访问日志实体类
 * 字段: url, cmsType, cmsId, traffic, ip, city, time
 */
case class AccessLog(
  url: String,        // 访问URL
  cmsType: String,    // 内容类型: video/code/course/ceping/article
  cmsId: Long,        // 内容ID
  traffic: Long,      // 流量(字节)
  ip: String,         // 访问IP
  city: String,       // 城市/省份
  time: Long          // 访问时间戳(毫秒)
)

/**
 * PV/UV 统计结果
 */
case class PvUvStat(
  windowStart: Long,   // 窗口开始时间
  windowEnd: Long,     // 窗口结束时间
  cmsType: String,     // 内容类型
  cmsId: Long,         // 内容ID
  pv: Long,            // 页面访问量
  uv: Long             // 独立访客数
)

/**
 * 热门课程统计结果
 */
case class HotCourseStat(
  windowStart: Long,   // 窗口开始时间
  windowEnd: Long,     // 窗口结束时间
  cmsType: String,     // 内容类型
  cmsId: Long,         // 内容ID
  accessCount: Long,   // 访问次数
  rank: Int            // 排名
)

/**
 * 在线人数统计结果
 */
case class OnlineUserStat(
  windowStart: Long,   // 窗口开始时间
  windowEnd: Long,     // 窗口结束时间
  onlineCount: Long    // 在线人数(去重IP)
)
