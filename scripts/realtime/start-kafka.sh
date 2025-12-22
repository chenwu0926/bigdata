#!/bin/bash
# Kafka 启动脚本

KAFKA_HOME=${KAFKA_HOME:-/usr/local/kafka}

echo "=========================================="
echo "  Imooc 日志分析 - Kafka 环境启动脚本"
echo "=========================================="

# 启动 Zookeeper
echo "[1/3] 启动 Zookeeper..."
$KAFKA_HOME/bin/zookeeper-server-start.sh -daemon $KAFKA_HOME/config/zookeeper.properties
sleep 5

# 启动 Kafka
echo "[2/3] 启动 Kafka..."
$KAFKA_HOME/bin/kafka-server-start.sh -daemon $KAFKA_HOME/config/server.properties
sleep 5

# 创建 Topic
echo "[3/3] 创建 Topic: imooc-access-log..."
$KAFKA_HOME/bin/kafka-topics.sh --create \
    --bootstrap-server localhost:9092 \
    --replication-factor 1 \
    --partitions 3 \
    --topic imooc-access-log \
    --if-not-exists

echo ""
echo "Kafka 环境启动完成！"
echo ""
echo "常用命令:"
echo "  查看 Topic 列表: $KAFKA_HOME/bin/kafka-topics.sh --list --bootstrap-server localhost:9092"
echo "  查看消费情况:   $KAFKA_HOME/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic imooc-access-log"
echo ""
