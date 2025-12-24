# 慕课网日志分析系统

基于 **Spark + Flink** 的 Lambda 架构日志分析系统，支持离线批处理和实时流处理双模式。

## 目录

- [项目概述](#项目概述)
- [技术架构](#技术架构)
- [功能模块](#功能模块)
- [数据仓库分层设计](#数据仓库分层设计)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [配置说明](#配置说明)
- [实时数据流详解](#实时数据流详解)
- [API 参考](#api-参考)
- [常见问题](#常见问题)

---

## 项目概述

### 业务背景

本项目是一个完整的日志分析解决方案，针对慕课网（imooc.com）的用户访问日志进行多维度分析，包括：

- **用户行为分析**：PV（页面浏览量）、UV（独立访客数）统计
- **内容热度分析**：热门课程、视频、文章排行
- **地域分布分析**：各省市访问量统计
- **流量分析**：带宽使用、高流量资源识别
- **实时监控**：在线人数、实时热榜、异常检测

### 数据规模

- **日志文件**：access.20161111.log
- **数据量**：一千多万条访问日志，5G+
- **日志格式**：Nginx Combined Log Format

```
60.165.39.1 - - [10/Nov/2016:00:01:53 +0800] "POST /course/ajaxmediauser HTTP/1.1" 200 54 "www.imooc.com" "http://www.imooc.com/code/1431" mid=1431&time=60 "Mozilla/5.0 ..." "-" 10.100.136.64:80 200 0.014 0.014
```

### 核心特性

| 特性 | 离线批处理 | 实时流处理 |
|-----|----------|----------|
| 引擎 | Spark SQL | Flink |
| 延迟 | T+1 (天级) | 秒级 |
| 数据源 | HDFS/本地文件 | Kafka |
| 存储 | MySQL/Parquet | Redis + MySQL |
| 场景 | 历史分析、报表 | 实时监控、告警 |

---

## 技术架构

### Lambda 架构总览

```
                    ┌─────────────────────────────────────────────────────────────┐
                    │                      数据采集层                               │
                    │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
                    │  │  Nginx   │  │  Tomcat  │  │   App    │  │  其他... │    │
                    │  │ 日志文件  │  │ 日志文件  │  │ 日志文件  │  │          │    │
                    │  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
                    └───────┼─────────────┼─────────────┼─────────────┼──────────┘
                            │             │             │             │
                            ▼             ▼             ▼             ▼
                    ┌─────────────────────────────────────────────────────────────┐
                    │                      日志收集层                               │
                    │            ┌─────────────────────────────┐                  │
                    │            │    Filebeat / Flume         │                  │
                    │            │    (日志采集、格式化)         │                  │
                    │            └─────────────┬───────────────┘                  │
                    └──────────────────────────┼──────────────────────────────────┘
                                               │
                                               ▼
                    ┌─────────────────────────────────────────────────────────────┐
                    │                      消息队列层                               │
                    │            ┌─────────────────────────────┐                  │
                    │            │         Kafka               │                  │
                    │            │  ┌───────────────────────┐  │                  │
                    │            │  │ raw-access-log (原始) │  │                  │
                    │            │  │ imooc-access-log(清洗)│  │                  │
                    │            │  └───────────────────────┘  │                  │
                    │            └──────────────┬──────────────┘                  │
                    └───────────────────────────┼─────────────────────────────────┘
                                                │
                       ┌────────────────────────┴────────────────────────┐
                       │                                                 │
                       ▼                                                 ▼
    ┌──────────────────────────────────────┐    ┌──────────────────────────────────────┐
    │           批处理层 (Batch Layer)       │    │          流处理层 (Speed Layer)        │
    │  ┌────────────────────────────────┐  │    │  ┌────────────────────────────────┐  │
    │  │          Spark SQL             │  │    │  │         Apache Flink           │  │
    │  │  ┌──────────────────────────┐  │  │    │  │  ┌──────────────────────────┐  │  │
    │  │  │ SparkStatFormatJob       │  │  │    │  │  │ DataCleanJob (数据清洗)  │  │  │
    │  │  │ (第一步清洗)              │  │  │    │  │  │ PvUvStatJob (PV/UV统计) │  │  │
    │  │  ├──────────────────────────┤  │  │    │  │  │ HotCourseTopNJob (TopN) │  │  │
    │  │  │ SparkStatCleanJob        │  │  │    │  │  │ OnlineUserCountJob      │  │  │
    │  │  │ (第二步清洗+分区)         │  │  │    │  │  └──────────────────────────┘  │  │
    │  │  ├──────────────────────────┤  │  │    │  └────────────────────────────────┘  │
    │  │  │ TopNStatJob              │  │  │    │                  │                    │
    │  │  │ (统计分析)               │  │  │    │                  ▼                    │
    │  │  └──────────────────────────┘  │  │    │  ┌────────────────────────────────┐  │
    │  └────────────────────────────────┘  │    │  │           Redis                │  │
    │                  │                    │    │  │  (实时结果缓存, 1分钟窗口)      │  │
    │                  ▼                    │    │  └────────────────────────────────┘  │
    │  ┌────────────────────────────────┐  │    └──────────────────────────────────────┘
    │  │     HDFS / Parquet 文件        │  │
    │  │     (按天分区存储)              │  │
    │  └────────────────────────────────┘  │
    └──────────────────────────────────────┘
                       │                                                 │
                       └────────────────────────┬────────────────────────┘
                                                │
                                                ▼
                    ┌─────────────────────────────────────────────────────────────┐
                    │                      服务层 (Serving Layer)                   │
                    │  ┌─────────────────────────┐  ┌─────────────────────────┐   │
                    │  │         MySQL           │  │      API / 可视化        │   │
                    │  │  (离线+实时统计结果)     │  │  (Zeppelin / Grafana)   │   │
                    │  └─────────────────────────┘  └─────────────────────────┘   │
                    └─────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层次 | 技术 | 版本 | 说明 |
|-----|-----|------|-----|
| 计算引擎 | Apache Spark | 2.3.1-cdh5.14.0 | 离线批处理 |
| 计算引擎 | Apache Flink | 1.14.6 | 实时流处理 |
| 消息队列 | Apache Kafka | 2.8.2 | 日志传输 |
| 分布式协调 | Zookeeper | 3.8 | Kafka 协调 |
| 缓存 | Redis | 7.0 | 实时结果存储 |
| 数据库 | MySQL | 8.0 | 持久化存储 |
| 日志采集 | Filebeat/Flume | - | 日志收集 |
| IP 解析 | ip2region | 2.x | IP 地址解析 |
| 开发语言 | Scala | 2.11.12 | 主开发语言 |
| 构建工具 | Maven | 3.x | 依赖管理 |
| 容器化 | Docker | 20.10+ | 部署环境 |

---

## 功能模块

### 离线批处理（Spark SQL）

#### 数据清洗流程

```
原始日志 → SparkStatFormatJob → SparkStatCleanJob → Parquet 文件
         (第一步: 解析字段)   (第二步: IP解析+分区)  (按天分区存储)
```

**清洗步骤**：

1. **SparkStatFormatJob** - 第一步清洗
   - 解析原始日志，提取 IP、时间、URL、流量
   - 过滤内网 IP (10.100.0.1)
   - 过滤无效 URL (-)
   - 时间格式转换

2. **SparkStatCleanJob** - 第二步清洗
   - 解析 URL 提取 cmsType 和 cmsId
   - IP 地址解析为城市
   - 数据按天分区
   - 输出为 Parquet 格式

**清洗结果示例**：

```
+--------------------------------------------+-------+-----+-------+---------------+------+-------------------+--------+
|url                                         |cmsType|cmsId|traffic|ip             |city  |time               |day     |
+--------------------------------------------+-------+-----+-------+---------------+------+-------------------+--------+
|http://www.imooc.com/code/1852              |code   |1852 |2345   |117.35.88.11   |陕西省 |2016-11-10 00:01:02|20161110|
|http://www.imooc.com/learn/85/?src=360onebox|learn  |85   |14531  |115.34.187.133 |北京市 |2016-11-10 00:01:27|20161110|
|http://www.imooc.com/video/6689             |video  |6689 |320    |14.153.236.58  |广东省 |2016-11-10 00:01:27|20161110|
+--------------------------------------------+-------+-----+-------+---------------+------+-------------------+--------+
```

#### 统计分析（TopNStatJob）

| 统计项 | 说明 | 结果表 |
|-------|-----|--------|
| 热门课程 TopN | 按访问次数排名 | day_video_access_topn_stat |
| 各城市 TopN | 各省市热门课程 | day_video_city_access_topn_stat |
| 流量 TopN | 按流量排名 | day_video_traffic_topn_stat |

### 实时流处理（Flink）

#### 实时作业列表

| 作业名称 | 类名 | 功能 | 窗口 |
|---------|-----|------|------|
| 数据清洗 | DataCleanJob | 实时清洗原始日志 | - |
| PV/UV 统计 | PvUvStatJob | 页面浏览量和独立访客 | 1分钟滚动窗口 |
| 热门课程 | HotCourseTopNJob | 实时热榜 Top 10 | 1分钟滚动窗口 |
| 在线人数 | OnlineUserCountJob | 实时在线用户数 | 1分钟滑动窗口 |

#### 实时数据清洗规则

与离线批处理保持一致：

- **过滤内网 IP**：10.x.x.x, 192.168.x.x, 172.16-31.x.x
- **过滤无效 URL**："-" 或空
- **URL 解析**：提取 cmsType（video/code/article 等）和 cmsId
- **IP 解析**：使用 ip2region 解析为城市
- **时间处理**：统一为毫秒时间戳

#### Redis 数据结构

```bash
# PV/UV 统计 (Hash)
realtime:pv:uv:{cmsType}:{cmsId}:{windowEnd}
  - pv: 页面访问次数
  - uv: 独立用户数

# 热门课程 TopN (Sorted Set)
realtime:topn:{cmsType}:{windowEnd}
  - member: cmsId
  - score: 访问次数

# 在线人数 (String)
realtime:online:{windowEnd}
  - value: 在线用户数
```

---

## 数据仓库分层设计

### 概念说明

本项目遵循经典的数据仓库分层模型，将数据处理划分为四个层次：

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            ADS (应用数据层)                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         │
│  │  热门课程 TopN   │  │  城市分布统计   │  │   实时大屏展示   │         │
│  │  day_video_*    │  │  city_access_*  │  │   Redis 缓存    │         │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘         │
│                               ▲                                         │
├───────────────────────────────┼─────────────────────────────────────────┤
│                            DWS (汇总数据层)                              │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │  PV/UV 统计、流量汇总、用户行为聚合                               │    │
│  │  按分钟/小时/天 聚合统计                                          │    │
│  └────────────────────────────────────────────────────────────────┘    │
│                               ▲                                         │
├───────────────────────────────┼─────────────────────────────────────────┤
│                            DWD (明细数据层)                              │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │  清洗后的访问日志明细                                             │    │
│  │  url, cmsType, cmsId, traffic, ip, city, time, day             │    │
│  │  存储: Parquet 文件 / Kafka (imooc-access-log)                  │    │
│  └────────────────────────────────────────────────────────────────┘    │
│                               ▲                                         │
├───────────────────────────────┼─────────────────────────────────────────┤
│                            ODS (原始数据层)                              │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │  原始 Nginx 日志                                                 │    │
│  │  60.165.39.1 - - [10/Nov/2016:00:01:53 +0800] "POST ..." ...   │    │
│  │  存储: 日志文件 / Kafka (raw-access-log)                         │    │
│  └────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

### 各层职责

| 层次 | 全称 | 职责 | 本项目对应 |
|-----|-----|------|-----------|
| ODS | Operational Data Store | 存储原始数据，不做处理 | raw-access-log Topic |
| DWD | Data Warehouse Detail | 数据清洗、标准化、脱敏 | imooc-access-log Topic / Parquet |
| DWS | Data Warehouse Summary | 按主题汇总、预聚合 | PV/UV 统计结果 |
| ADS | Application Data Store | 面向应用的数据集市 | TopN 排行、报表数据 |

---

## 快速开始

### 方式一：Docker 部署（推荐）

#### 前置条件

- Docker 20.10+
- Docker Compose 2.0+
- 8GB+ 可用内存

#### 1. 编译项目

```bash
# 进入项目目录
cd /path/to/bigdata

# 使用 Docker 编译（无需本地 Maven）
docker run --rm \
    -v "$(pwd)":/build \
    -v maven-repo:/root/.m2 \
    -w /build \
    maven:3.8-openjdk-8 \
    mvn clean package -DskipTests

# 复制 JAR 到 docker 目录
mkdir -p docker/jars
cp target/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar docker/jars/
```

#### 2. 启动服务

```bash
cd docker

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps
```

#### 3. 创建 Kafka Topic

```bash
# 创建原始日志 Topic
docker exec kafka kafka-topics --create \
    --bootstrap-server localhost:9092 \
    --topic raw-access-log \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists

# 创建清洗后日志 Topic
docker exec kafka kafka-topics --create \
    --bootstrap-server localhost:9092 \
    --topic imooc-access-log \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists
```

#### 4. 提交 Flink 作业

```bash
JAR="/opt/flink/usrlib/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar"

# 提交数据清洗作业
docker exec flink-jobmanager flink run -d \
    -c com.whirly.realtime.job.DataCleanJob $JAR

# 提交 PV/UV 统计作业
docker exec flink-jobmanager flink run -d \
    -c com.whirly.realtime.job.PvUvStatJob $JAR

# 提交热门课程 TopN 作业
docker exec flink-jobmanager flink run -d \
    -c com.whirly.realtime.job.HotCourseTopNJob $JAR

# 提交在线人数统计作业
docker exec flink-jobmanager flink run -d \
    -c com.whirly.realtime.job.OnlineUserCountJob $JAR
```

#### 5. 发送测试数据

```bash
# 方式 A：使用数据生产者（从 CSV 读取）
java -cp target/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar \
    com.whirly.realtime.producer.LogDataProducer test

# 方式 B：手动发送 JSON 消息
docker exec -it kafka kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic imooc-access-log

# 输入测试数据：
{"url":"http://www.imooc.com/video/123","cmsType":"video","cmsId":123,"traffic":1024,"ip":"60.165.39.1","city":"北京市","time":1703260800000}
```

#### 6. 查看结果

```bash
# 查看 Redis 数据
docker exec redis redis-cli KEYS 'realtime:*'

# 查看 PV/UV
docker exec redis redis-cli HGETALL realtime:pv:uv:video:123:1703260860000

# 访问 Flink Web UI
open http://localhost:8081

# 访问 Kafka UI
open http://localhost:8080
```

### 方式二：本地开发环境

#### 环境要求

- Java 1.8
- Scala 2.11.12
- Hadoop 2.6.0-cdh5.14.0
- Spark 2.3.1-cdh5.14.0
- MySQL 5.7+

#### 离线批处理

```bash
# 1. 安装 IP 解析库
./scripts/download-ip2region.sh

# 2. 编译项目
mvn clean package -DskipTests

# 3. 配置数据库（导入 SQL）
mysql -u root -p < sql/imooc_log.sql

# 4. 运行数据清洗
spark-submit --class com.whirly.offline.SparkStatFormatJob \
    target/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar

spark-submit --class com.whirly.offline.SparkStatCleanJob \
    target/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar

# 5. 运行统计分析
spark-submit --class com.whirly.offline.TopNStatJob \
    target/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## 项目结构

```
bigdata/
├── src/main/scala/com/whirly/
│   ├── offline/                      # 离线批处理模块
│   │   ├── SparkStatFormatJob.scala  # 第一步清洗
│   │   ├── SparkStatCleanJob.scala   # 第二步清洗
│   │   ├── TopNStatJob.scala         # TopN 统计
│   │   ├── AccessConvertUtil.scala   # 数据转换工具
│   │   └── DateUtils.scala           # 时间工具
│   │
│   └── realtime/                     # 实时流处理模块
│       ├── job/                      # Flink 作业
│       │   ├── DataCleanJob.scala    # 实时数据清洗
│       │   ├── PvUvStatJob.scala     # PV/UV 统计
│       │   ├── HotCourseTopNJob.scala # 热门课程
│       │   └── OnlineUserCountJob.scala # 在线人数
│       │
│       ├── source/                   # 数据源
│       │   ├── AccessLogDeserializer.scala  # JSON 反序列化
│       │   └── RawLogDeserializer.scala     # 原始日志反序列化
│       │
│       ├── entity/                   # 实体类
│       │   └── AccessLog.scala       # 访问日志对象
│       │
│       ├── util/                     # 工具类
│       │   ├── AccessLogCleanUtil.scala # 数据清洗工具
│       │   └── RedisUtil.scala       # Redis 工具
│       │
│       ├── config/                   # 配置
│       │   └── FlinkConfig.scala     # Flink 配置
│       │
│       └── producer/                 # 数据生产者
│           └── LogDataProducer.scala # 模拟数据生产
│
├── config/                           # 配置文件
│   ├── filebeat/
│   │   └── filebeat-imooc.yml        # Filebeat 配置
│   └── flume/
│       └── flume-kafka.conf          # Flume 配置
│
├── docker/                           # Docker 部署
│   ├── docker-compose.yml            # Docker Compose 配置
│   ├── jars/                         # Flink JAR 文件
│   ├── scripts/                      # 部署脚本
│   └── README.md                     # Docker 部署指南
│
├── sql/                              # SQL 脚本
│   └── imooc_log.sql                 # 数据库初始化
│
├── scripts/                          # 脚本
│   └── download-ip2region.sh         # 下载 IP 解析库
│
├── data/                             # 数据目录
│   └── imooc-log/
│       ├── raw/                      # 原始日志
│       └── cleanedCsv/               # 清洗后 CSV
│
├── pom.xml                           # Maven 配置
└── README.md                         # 项目说明
```

---

## 配置说明

### 环境变量（Docker）

| 变量 | 默认值 | 说明 |
|-----|-------|------|
| KAFKA_BOOTSTRAP_SERVERS | localhost:9092 | Kafka 地址 |
| REDIS_HOST | localhost | Redis 地址 |
| REDIS_PORT | 6379 | Redis 端口 |
| RAW_KAFKA_TOPIC | raw-access-log | 原始日志 Topic |

### FlinkConfig 配置

```scala
// src/main/scala/com/whirly/realtime/config/FlinkConfig.scala

// Kafka 配置
val KAFKA_BOOTSTRAP_SERVERS = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
val KAFKA_TOPIC = "imooc-access-log"
val KAFKA_GROUP_ID = "flink-consumer-group"

// Redis 配置
val REDIS_HOST = sys.env.getOrElse("REDIS_HOST", "localhost")
val REDIS_PORT = sys.env.getOrElse("REDIS_PORT", "6379").toInt
val REDIS_EXPIRE_SECONDS = 3600  // 1小时过期

// 窗口配置
val WINDOW_SIZE_MINUTES = 1
```

### docker-compose.yml 关键配置

```yaml
services:
  flink-jobmanager:
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - REDIS_HOST=redis
      - RAW_KAFKA_TOPIC=raw-access-log
```

---

## 实时数据流详解

### 完整数据流

```
1. 日志产生
   Nginx → access.log

2. 日志采集（可选）
   Filebeat/Flume → Kafka (raw-access-log)

3. 或者使用模拟数据
   LogDataProducer (csv/random/loop) → Kafka (raw-access-log 或 imooc-access-log)

4. 实时清洗（如果从原始日志）
   Kafka (raw-access-log) → DataCleanJob → Kafka (imooc-access-log)

5. 实时计算
   Kafka (imooc-access-log) → PvUvStatJob/HotCourseTopNJob/OnlineUserCountJob → Redis

6. 数据查询
   Redis → API/Dashboard
```

### LogDataProducer 使用方式

```bash
# 从 CSV 文件读取，发送一次
java -cp xxx.jar com.whirly.realtime.producer.LogDataProducer csv /path/to/data.csv

# 循环读取 CSV，持续发送
java -cp xxx.jar com.whirly.realtime.producer.LogDataProducer loop /path/to/data.csv

# 随机生成数据，持续发送
java -cp xxx.jar com.whirly.realtime.producer.LogDataProducer random

# 快速测试，发送 10 条
java -cp xxx.jar com.whirly.realtime.producer.LogDataProducer test
```

---

## API 参考

### Redis 数据查询

```bash
# 连接 Redis
redis-cli -h localhost -p 6379

# 查看所有实时数据 key
KEYS realtime:*

# 查看 PV/UV（Hash）
HGETALL realtime:pv:uv:video:123:1703260860000
# 返回: pv -> 100, uv -> 50

# 查看热门课程 TopN（Sorted Set）
ZREVRANGE realtime:topn:video:1703260860000 0 9 WITHSCORES
# 返回: cmsId1, score1, cmsId2, score2, ...

# 查看在线人数（String）
GET realtime:online:1703260860000
# 返回: 1234
```

### MySQL 查询

```sql
-- 查看离线统计结果
SELECT * FROM day_video_access_topn_stat WHERE day = '20161110';

-- 查看城市分布
SELECT * FROM day_video_city_access_topn_stat WHERE day = '20161110';
```

---

## 常见问题

### 1. Flink 作业提交失败

**问题**: `java.lang.ClassNotFoundException`

**解决**: 确保使用 `jar-with-dependencies.jar`

```bash
# 检查 JAR 是否包含依赖
jar tf target/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar | grep flink
```

### 2. Kafka 连接失败

**问题**: `UnknownTopicOrPartitionException`

**解决**: 确保 Topic 已创建

```bash
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

### 3. Redis 无数据

**问题**: 提交作业后 Redis 无数据

**排查步骤**:

```bash
# 1. 检查作业状态
docker exec flink-jobmanager flink list

# 2. 检查 Kafka 是否有消息
docker exec kafka kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic imooc-access-log \
    --from-beginning --max-messages 5

# 3. 查看 Flink 日志
docker-compose logs -f flink-taskmanager
```

### 4. 内存不足

**问题**: Docker 容器 OOM

**解决**: 调整 docker-compose.yml 中的内存配置

```yaml
flink-taskmanager:
  environment:
    - taskmanager.memory.process.size=1g  # 减少内存
```

### 5. IP 解析失败

**问题**: 城市显示为空或"未知"

**解决**: 确保 ip2region 数据库已下载

```bash
./scripts/download-ip2region.sh
```

---

## 可视化

### Zeppelin

导入 `最受欢迎的TopN课程.json` 查看离线统计结果。

![Zeppelin 统计结果](images/20181216_181326.png)

### Grafana（实时）

可配置 Grafana 连接 Redis，展示实时大屏：

- 实时 PV/UV 趋势图
- 热门课程排行榜
- 在线人数曲线
- 地域分布地图

---

## 相关链接

- **数据集下载**: [百度网盘](https://pan.baidu.com/s/1VfOG14mGW4P4kj20nzKx8g) 提取码: uwjg
- **ip2region**: https://github.com/lionsoul2014/ip2region
- **Apache Flink**: https://flink.apache.org/
- **Apache Spark**: https://spark.apache.org/

---

## License

MIT License
