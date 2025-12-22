#!/bin/bash
# 数据生产者启动脚本

echo "=========================================="
echo "  Imooc 日志数据生产者"
echo "=========================================="
echo "Kafka Servers: $KAFKA_BOOTSTRAP_SERVERS"
echo "Kafka Topic: $KAFKA_TOPIC"
echo "Producer Mode: $PRODUCER_MODE"
echo "=========================================="

# 等待 Kafka 就绪
echo "等待 Kafka 就绪..."
sleep 10

# 根据模式启动
if [ "$PRODUCER_MODE" = "csv" ]; then
    echo "从 CSV 文件读取数据: $CSV_FILE"
    java -cp /app/app.jar \
        -DKAFKA_BOOTSTRAP_SERVERS=$KAFKA_BOOTSTRAP_SERVERS \
        com.whirly.realtime.producer.LogDataProducer csv "$CSV_FILE"
else
    echo "随机生成数据模式"
    java -cp /app/app.jar \
        -DKAFKA_BOOTSTRAP_SERVERS=$KAFKA_BOOTSTRAP_SERVERS \
        com.whirly.realtime.producer.LogDataProducer random
fi
