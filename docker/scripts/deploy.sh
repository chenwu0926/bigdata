#!/bin/bash
# Docker 一键部署脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$DOCKER_DIR/.." && pwd)"

echo "=========================================="
echo "  Imooc 日志分析 - Docker 部署脚本"
echo "=========================================="
echo ""

# 进入 docker 目录
cd "$DOCKER_DIR"

case "$1" in
    build)
        echo "[1/2] 编译 Maven 项目..."
        cd "$PROJECT_DIR"
        if command -v mvn &> /dev/null; then
            mvn clean package -DskipTests -q
        else
            echo "警告: Maven 未安装，请手动编译项目"
            echo "  mvn clean package -DskipTests"
        fi

        echo "[2/2] 复制 JAR 文件..."
        mkdir -p "$DOCKER_DIR/jars"
        cp "$PROJECT_DIR/target/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar" "$DOCKER_DIR/jars/" 2>/dev/null || \
            echo "警告: JAR 文件未找到，请先编译项目"
        echo "构建完成!"
        ;;

    start)
        echo "启动所有服务..."
        docker-compose up -d
        echo ""
        echo "服务启动中，请稍候..."
        sleep 10
        docker-compose ps
        echo ""
        echo "=========================================="
        echo "  服务访问地址:"
        echo "  - Kafka UI:    http://localhost:8080"
        echo "  - Flink UI:    http://localhost:8081"
        echo "  - MySQL:       localhost:3306"
        echo "  - Redis:       localhost:6379"
        echo "=========================================="
        ;;

    stop)
        echo "停止所有服务..."
        docker-compose down
        echo "服务已停止"
        ;;

    restart)
        echo "重启所有服务..."
        docker-compose restart
        ;;

    logs)
        service=${2:-""}
        if [ -n "$service" ]; then
            docker-compose logs -f "$service"
        else
            docker-compose logs -f
        fi
        ;;

    producer)
        echo "启动数据生产者..."
        docker-compose --profile producer up -d log-producer
        docker-compose logs -f log-producer
        ;;

    submit)
        # 提交 Flink 作业
        job=${2:-"all"}
        jar_file="/opt/flink/usrlib/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar"

        case "$job" in
            pvuv)
                echo "提交 PV/UV 统计作业..."
                docker exec flink-jobmanager flink run -d \
                    -c com.whirly.realtime.job.PvUvStatJob "$jar_file"
                ;;
            topn)
                echo "提交热门课程 TopN 作业..."
                docker exec flink-jobmanager flink run -d \
                    -c com.whirly.realtime.job.HotCourseTopNJob "$jar_file"
                ;;
            online)
                echo "提交在线人数统计作业..."
                docker exec flink-jobmanager flink run -d \
                    -c com.whirly.realtime.job.OnlineUserCountJob "$jar_file"
                ;;
            all)
                echo "提交所有作业..."
                docker exec flink-jobmanager flink run -d \
                    -c com.whirly.realtime.job.PvUvStatJob "$jar_file"
                docker exec flink-jobmanager flink run -d \
                    -c com.whirly.realtime.job.HotCourseTopNJob "$jar_file"
                docker exec flink-jobmanager flink run -d \
                    -c com.whirly.realtime.job.OnlineUserCountJob "$jar_file"
                echo "所有作业已提交"
                ;;
            *)
                echo "未知作业: $job"
                echo "可用作业: pvuv, topn, online, all"
                exit 1
                ;;
        esac
        echo ""
        echo "查看作业状态: http://localhost:8081"
        ;;

    create-topic)
        echo "创建 Kafka Topic..."
        docker exec kafka kafka-topics --create \
            --bootstrap-server localhost:9092 \
            --replication-factor 1 \
            --partitions 3 \
            --topic imooc-access-log \
            --if-not-exists
        echo "Topic 创建完成"
        ;;

    status)
        docker-compose ps
        ;;

    clean)
        echo "清理所有数据..."
        docker-compose down -v
        echo "数据已清理"
        ;;

    *)
        echo "用法: $0 {command} [options]"
        echo ""
        echo "命令:"
        echo "  build           - 编译项目并复制 JAR"
        echo "  start           - 启动所有服务"
        echo "  stop            - 停止所有服务"
        echo "  restart         - 重启所有服务"
        echo "  status          - 查看服务状态"
        echo "  logs [service]  - 查看日志"
        echo ""
        echo "Flink 作业:"
        echo "  submit all      - 提交所有 Flink 作业"
        echo "  submit pvuv     - 提交 PV/UV 统计作业"
        echo "  submit topn     - 提交热门课程 TopN 作业"
        echo "  submit online   - 提交在线人数统计作业"
        echo ""
        echo "数据:"
        echo "  create-topic    - 创建 Kafka Topic"
        echo "  producer        - 启动数据生产者"
        echo ""
        echo "维护:"
        echo "  clean           - 清理所有数据 (删除 volumes)"
        exit 1
        ;;
esac
