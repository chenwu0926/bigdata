#!/bin/bash
# Flink Jobs 运行脚本

PROJECT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
JAR_FILE="$PROJECT_DIR/target/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar"

FLINK_HOME=${FLINK_HOME:-/usr/local/flink}

echo "=========================================="
echo "  Imooc 日志分析 - Flink 作业启动脚本"
echo "=========================================="
echo ""

# 检查 JAR 文件
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: JAR 文件不存在，请先编译项目"
    echo "  cd $PROJECT_DIR && mvn clean package -DskipTests"
    exit 1
fi

# 选择要运行的作业
case "$1" in
    pvuv)
        echo "启动 PV/UV 统计作业..."
        $FLINK_HOME/bin/flink run -c com.whirly.realtime.job.PvUvStatJob $JAR_FILE
        ;;
    topn)
        echo "启动热门课程 TopN 作业..."
        $FLINK_HOME/bin/flink run -c com.whirly.realtime.job.HotCourseTopNJob $JAR_FILE
        ;;
    online)
        echo "启动在线人数统计作业..."
        $FLINK_HOME/bin/flink run -c com.whirly.realtime.job.OnlineUserCountJob $JAR_FILE
        ;;
    all)
        echo "启动所有作业..."
        $FLINK_HOME/bin/flink run -d -c com.whirly.realtime.job.PvUvStatJob $JAR_FILE
        $FLINK_HOME/bin/flink run -d -c com.whirly.realtime.job.HotCourseTopNJob $JAR_FILE
        $FLINK_HOME/bin/flink run -d -c com.whirly.realtime.job.OnlineUserCountJob $JAR_FILE
        echo "所有作业已提交到 Flink 集群"
        ;;
    producer)
        echo "启动 Kafka 数据生产者..."
        if [ "$2" = "csv" ]; then
            java -cp $JAR_FILE com.whirly.realtime.producer.LogDataProducer csv "$3"
        else
            java -cp $JAR_FILE com.whirly.realtime.producer.LogDataProducer random
        fi
        ;;
    *)
        echo "用法: $0 {pvuv|topn|online|all|producer} [options]"
        echo ""
        echo "作业:"
        echo "  pvuv     - 启动 PV/UV 统计作业"
        echo "  topn     - 启动热门课程 TopN 作业"
        echo "  online   - 启动在线人数统计作业"
        echo "  all      - 启动所有作业（后台运行）"
        echo ""
        echo "数据生产者:"
        echo "  producer         - 随机生成数据"
        echo "  producer csv     - 从 CSV 文件读取数据"
        echo "  producer csv <path> - 从指定 CSV 文件读取"
        exit 1
        ;;
esac

echo ""
echo "Flink Web UI: http://localhost:8081"
