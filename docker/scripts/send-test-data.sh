#!/bin/bash
# 发送测试数据到 Kafka

echo "=========================================="
echo "  发送测试数据到 Kafka"
echo "=========================================="

# 生成测试数据
generate_log() {
    local timestamp=$(date +%s)000
    local cms_types=("video" "code" "course" "article")
    local cities=("北京市" "上海市" "广东省" "浙江省" "江苏省")
    local cms_type=${cms_types[$RANDOM % ${#cms_types[@]}]}
    local cms_id=$((RANDOM % 1000 + 1))
    local city=${cities[$RANDOM % ${#cities[@]}]}
    local ip="192.168.$((RANDOM % 256)).$((RANDOM % 256))"
    local traffic=$((RANDOM % 10000 + 100))

    echo "{\"url\":\"http://www.imooc.com/$cms_type/$cms_id\",\"cmsType\":\"$cms_type\",\"cmsId\":$cms_id,\"traffic\":$traffic,\"ip\":\"$ip\",\"city\":\"$city\",\"time\":$timestamp}"
}

# 发送数据
count=${1:-100}
echo "发送 $count 条测试数据..."

for i in $(seq 1 $count); do
    log=$(generate_log)
    echo "$log" | docker exec -i kafka kafka-console-producer \
        --bootstrap-server localhost:9092 \
        --topic imooc-access-log

    if [ $((i % 10)) -eq 0 ]; then
        echo "已发送 $i 条数据..."
    fi
    sleep 0.1
done

echo ""
echo "测试数据发送完成!"
echo "查看 Kafka 消息: docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic imooc-access-log --from-beginning"
