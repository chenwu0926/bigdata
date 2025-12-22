#!/bin/bash
# Kafka 停止脚本

KAFKA_HOME=${KAFKA_HOME:-/usr/local/kafka}

echo "停止 Kafka..."
$KAFKA_HOME/bin/kafka-server-stop.sh
sleep 3

echo "停止 Zookeeper..."
$KAFKA_HOME/bin/zookeeper-server-stop.sh

echo "Kafka 环境已停止"
