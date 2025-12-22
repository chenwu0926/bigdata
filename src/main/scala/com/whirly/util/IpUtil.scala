package com.whirly.util

import org.lionsoul.ip2region.xdb.Searcher

/**
 * IP 地址解析工具类
 * 使用 ip2region 库进行离线 IP 解析
 * 数据格式: 国家|区域|省份|城市|ISP
 */
object IpUtil {

  // 使用内存缓存加载整个 xdb 文件，提高查询性能
  @volatile private var searcher: Searcher = _
  @volatile private var cBuffer: Array[Byte] = _

  /**
   * 初始化 Searcher（懒加载）
   */
  private def getSearcher: Searcher = {
    if (searcher == null) {
      synchronized {
        if (searcher == null) {
          try {
            // 从 classpath 加载 ip2region.xdb
            val inputStream = getClass.getClassLoader.getResourceAsStream("ip2region.xdb")
            if (inputStream != null) {
              cBuffer = new Array[Byte](inputStream.available())
              inputStream.read(cBuffer)
              inputStream.close()
              searcher = Searcher.newWithBuffer(cBuffer)
            } else {
              // 如果 classpath 没有，尝试从默认路径加载
              val dbPath = "/data/ip2region.xdb"
              cBuffer = Searcher.loadContentFromFile(dbPath)
              searcher = Searcher.newWithBuffer(cBuffer)
            }
          } catch {
            case e: Exception =>
              println(s"[IpUtil] 加载 ip2region.xdb 失败: ${e.getMessage}")
              null
          }
        }
      }
    }
    searcher
  }

  /**
   * 根据 IP 地址查询地区信息
   * @param ip IP 地址
   * @return 省份/城市名称，解析失败返回 "未知"
   */
  def findRegionByIp(ip: String): String = {
    if (ip == null || ip.isEmpty) return "未知"

    try {
      val s = getSearcher
      if (s == null) return "未知"

      val region = s.search(ip)
      // region 格式: 中国|0|北京|北京市|联通
      if (region != null && region.nonEmpty) {
        val parts = region.split("\\|")
        if (parts.length >= 4) {
          val province = parts(2)
          val city = parts(3)
          // 优先返回省份，如果省份为 0 则返回城市
          if (province != "0" && province.nonEmpty) {
            province
          } else if (city != "0" && city.nonEmpty) {
            city
          } else {
            parts(0)  // 返回国家
          }
        } else {
          "未知"
        }
      } else {
        "未知"
      }
    } catch {
      case e: Exception =>
        "未知"
    }
  }

  /**
   * 获取完整地区信息
   * @return 格式: 国家|区域|省份|城市|ISP
   */
  def getFullRegion(ip: String): String = {
    if (ip == null || ip.isEmpty) return ""

    try {
      val s = getSearcher
      if (s == null) return ""
      s.search(ip)
    } catch {
      case e: Exception => ""
    }
  }
}
