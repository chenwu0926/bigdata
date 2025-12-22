# Docker 部署指南

## 架构图

```
┌────────────────────────────────────────────────────────────────┐
│                        Docker 环境                              │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐    │
│  │Zookeeper │   │  Kafka   │   │  Redis   │   │  MySQL   │    │
│  │  :2181   │──▶│  :9092   │   │  :6379   │   │  :3306   │    │
│  └──────────┘   └────┬─────┘   └────▲─────┘   └────▲─────┘    │
│                      │              │              │           │
│                      ▼              │              │           │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                    Flink Cluster                         │  │
│  │  ┌─────────────┐          ┌─────────────────────────┐   │  │
│  │  │ JobManager  │          │     TaskManager         │   │  │
│  │  │   :8081     │◀────────▶│  (运行 Flink 作业)       │   │  │
│  │  └─────────────┘          └─────────────────────────┘   │  │
│  └─────────────────────────────────────────────────────────┘  │
│                      ▲                                         │
│                      │                                         │
│  ┌──────────────────────────────┐   ┌───────────────────────┐ │
│  │       Kafka UI (:8080)       │   │   数据生产者 (可选)    │ │
│  └──────────────────────────────┘   └───────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
```

## 前置条件

- Docker 已安装 (版本 20.10+)
- Docker Compose 已安装 (版本 2.0+)
- 至少 8GB 可用内存

```bash
# 检查 Docker 版本
docker --version
docker-compose --version
```

---

## 第一步：编译项目

### 方式 A：使用 Docker 编译（无需安装 Maven）

```bash
# 进入项目根目录
cd /path/to/bigdata

# 使用 Docker 内的 Maven 编译
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

### 方式 B：本地 Maven 编译

```bash
# 安装 Maven（如果没有）
# Ubuntu: sudo apt install maven
# Mac: brew install maven

# 编译
mvn clean package -DskipTests

# 复制 JAR
mkdir -p docker/jars
cp target/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar docker/jars/
```

---

## 第二步：启动 Docker 服务

```bash
# 进入 docker 目录
cd docker

# 启动所有服务（后台运行）
docker-compose up -d

# 查看服务状态
docker-compose ps
```

**预期输出：**
```
NAME                IMAGE                            STATUS
zookeeper           confluentinc/cp-zookeeper:7.4.0  Up (healthy)
kafka               confluentinc/cp-kafka:7.4.0      Up (healthy)
kafka-ui            provectuslabs/kafka-ui:latest    Up
redis               redis:7-alpine                   Up (healthy)
mysql               mysql:8.0                        Up (healthy)
flink-jobmanager    flink:1.14.6-scala_2.11-java8    Up
flink-taskmanager   flink:1.14.6-scala_2.11-java8    Up
```

**等待所有服务启动（约 30-60 秒）：**
```bash
# 查看日志，确认服务正常
docker-compose logs -f
# 按 Ctrl+C 退出日志查看
```

---

## 第三步：创建 Kafka Topic

```bash
# 创建日志主题
docker exec kafka kafka-topics --create \
    --bootstrap-server localhost:9092 \
    --topic imooc-access-log \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists

# 验证 Topic 已创建
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

---

## 第四步：提交 Flink 作业

```bash
# 定义 JAR 路径
JAR="/opt/flink/usrlib/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar"

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

**或使用脚本一键提交：**
```bash
./scripts/submit-jobs.sh all
```

**验证作业已提交：**
```bash
# 查看运行中的作业
docker exec flink-jobmanager flink list
```

---

## 第五步：发送测试数据

### 方式 A：手动发送测试消息

```bash
# 进入 Kafka 容器发送消息
docker exec -it kafka kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic imooc-access-log

# 输入以下 JSON 数据（每行一条），按回车发送：
{"url":"http://www.imooc.com/video/123","cmsType":"video","cmsId":123,"traffic":1024,"ip":"192.168.1.1","city":"北京市","time":1703260800000}
{"url":"http://www.imooc.com/video/456","cmsType":"video","cmsId":456,"traffic":2048,"ip":"192.168.1.2","city":"上海市","time":1703260801000}
{"url":"http://www.imooc.com/code/789","cmsType":"code","cmsId":789,"traffic":512,"ip":"192.168.1.1","city":"北京市","time":1703260802000}

# 按 Ctrl+C 退出
```

### 方式 B：使用脚本批量发送

```bash
# 发送 100 条随机测试数据
./scripts/send-test-data.sh 100
```

### 方式 C：从 CSV 文件读取发送

```bash
# 将 CSV 数据发送到 Kafka（需要数据生产者容器）
docker-compose --profile producer up -d log-producer
```

---

## 第六步：查看结果

### 1. Flink Web UI（作业监控）

打开浏览器访问：**http://localhost:8081**

可以看到：
- 运行中的作业
- 每个作业的数据吞吐量
- Checkpoint 状态

### 2. Kafka UI（消息监控）

打开浏览器访问：**http://localhost:8080**

可以看到：
- Topic 列表
- 消息内容
- 消费者组状态

### 3. Redis（统计结果）

```bash
# 进入 Redis 容器
docker exec -it redis redis-cli

# 查看所有 key
KEYS realtime:*

# 查看 PV/UV 统计
HGETALL realtime:pv:uv:video:123:1703260860000

# 查看热门课程
ZREVRANGE realtime:topn:video:1703260860000 0 9 WITHSCORES

# 查看在线人数
GET realtime:online:1703260860000

# 退出
exit
```

### 4. MySQL（持久化数据）

```bash
# 进入 MySQL 容器
docker exec -it mysql mysql -uroot -p123456 imooc_log

# 查看表
SHOW TABLES;

# 查看实时 PV/UV 统计
SELECT * FROM realtime_pv_uv_stat ORDER BY window_end DESC LIMIT 10;

# 查看热门课程
SELECT * FROM realtime_hot_course_stat ORDER BY window_end DESC, rank_num LIMIT 20;

# 退出
exit
```

---

## 常用运维命令

### 服务管理

```bash
# 查看服务状态
docker-compose ps

# 查看所有日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f flink-jobmanager
docker-compose logs -f kafka

# 重启服务
docker-compose restart

# 停止所有服务
docker-compose down

# 停止并删除数据
docker-compose down -v
```

### Flink 作业管理

```bash
# 列出所有作业
docker exec flink-jobmanager flink list

# 取消作业
docker exec flink-jobmanager flink cancel <job-id>

# 查看作业详情
docker exec flink-jobmanager flink info $JAR
```

### Kafka 操作

```bash
# 查看 Topic 列表
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# 查看 Topic 详情
docker exec kafka kafka-topics --describe \
    --bootstrap-server localhost:9092 \
    --topic imooc-access-log

# 消费消息（查看）
docker exec kafka kafka-console-consumer \
    --bootstrap-server localhost:9092 \
    --topic imooc-access-log \
    --from-beginning

# 查看消费者组
docker exec kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --list
```

---

## 端口说明

| 端口 | 服务 | 说明 |
|-----|------|------|
| 2181 | Zookeeper | Kafka 协调服务 |
| 9092 | Kafka | 消息队列（外部访问） |
| 29092 | Kafka | 消息队列（容器内部） |
| 8080 | Kafka UI | Kafka 可视化管理 |
| 8081 | Flink | Flink Web UI |
| 6379 | Redis | 缓存 |
| 3306 | MySQL | 数据库 |

---

## 故障排查

### 1. 服务启动失败

```bash
# 查看详细日志
docker-compose logs <service-name>

# 检查端口占用
lsof -i :8081
lsof -i :9092
```

### 2. Flink 作业提交失败

```bash
# 检查 JAR 文件是否存在
docker exec flink-jobmanager ls -la /opt/flink/usrlib/

# 查看 JobManager 日志
docker-compose logs flink-jobmanager
```

### 3. Kafka 连接失败

```bash
# 检查 Kafka 是否就绪
docker exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# 检查 Topic 是否存在
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

### 4. 内存不足

```bash
# 检查 Docker 资源使用
docker stats

# 减少 TaskManager 内存（修改 docker-compose.yml）
# taskmanager.memory.process.size: 1g
```

---

## 快速命令汇总

```bash
# 一键启动
cd docker && docker-compose up -d

# 创建 Topic
docker exec kafka kafka-topics --create --bootstrap-server localhost:9092 --topic imooc-access-log --partitions 3 --replication-factor 1 --if-not-exists

# 提交所有作业
./scripts/submit-jobs.sh all

# 发送测试数据
./scripts/send-test-data.sh 100

# 查看作业
docker exec flink-jobmanager flink list

# 停止服务
docker-compose down
```
