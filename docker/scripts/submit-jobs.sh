#!/bin/bash
# 提交 Flink 作业到 Docker 集群

JAR_FILE="/opt/flink/usrlib/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar"

# 环境变量配置（Docker 内部地址）
ENV_VARS="-DKAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
          -DREDIS_HOST=redis \
          -DMYSQL_HOST=mysql"

echo "=========================================="
echo "  提交 Flink 作业"
echo "=========================================="

case "$1" in
    pvuv)
        echo "提交 PV/UV 统计作业..."
        docker exec flink-jobmanager flink run -d \
            $ENV_VARS \
            -c com.whirly.realtime.job.PvUvStatJob \
            "$JAR_FILE"
        ;;
    topn)
        echo "提交热门课程 TopN 作业..."
        docker exec flink-jobmanager flink run -d \
            $ENV_VARS \
            -c com.whirly.realtime.job.HotCourseTopNJob \
            "$JAR_FILE"
        ;;
    online)
        echo "提交在线人数统计作业..."
        docker exec flink-jobmanager flink run -d \
            $ENV_VARS \
            -c com.whirly.realtime.job.OnlineUserCountJob \
            "$JAR_FILE"
        ;;
    all)
        echo "提交所有作业..."
        for job in PvUvStatJob HotCourseTopNJob OnlineUserCountJob; do
            echo "  提交 $job..."
            docker exec flink-jobmanager flink run -d \
                $ENV_VARS \
                -c "com.whirly.realtime.job.$job" \
                "$JAR_FILE"
            sleep 2
        done
        echo "所有作业已提交!"
        ;;
    list)
        echo "当前运行的作业:"
        docker exec flink-jobmanager flink list
        ;;
    cancel)
        if [ -z "$2" ]; then
            echo "请提供作业 ID"
            echo "用法: $0 cancel <job-id>"
            exit 1
        fi
        echo "取消作业: $2"
        docker exec flink-jobmanager flink cancel "$2"
        ;;
    *)
        echo "用法: $0 {pvuv|topn|online|all|list|cancel <job-id>}"
        echo ""
        echo "  pvuv    - 提交 PV/UV 统计作业"
        echo "  topn    - 提交热门课程 TopN 作业"
        echo "  online  - 提交在线人数统计作业"
        echo "  all     - 提交所有作业"
        echo "  list    - 列出运行中的作业"
        echo "  cancel  - 取消指定作业"
        exit 1
        ;;
esac

echo ""
echo "Flink Web UI: http://localhost:8081"
