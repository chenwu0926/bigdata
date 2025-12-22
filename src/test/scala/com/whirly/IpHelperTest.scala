package com.whirly

import com.whirly.util.IpUtil

object IpHelperTest {
  def main(args: Array[String]): Unit = {
    // 测试 IP 解析
    val testIps = Array(
      "10.100.0.1",       // 内网
      "183.240.130.23",   // 广东
      "119.130.229.90",   // 广东
      "113.45.39.241",    // 北京
      "39.188.192.245",   // 浙江
      "218.26.76.143"     // 山西
    )

    println("IP 地址解析测试:")
    println("=" * 40)
    testIps.foreach { ip =>
      val region = IpUtil.findRegionByIp(ip)
      val fullRegion = IpUtil.getFullRegion(ip)
      println(s"$ip => $region")
      println(s"  完整信息: $fullRegion")
    }
  }
}
