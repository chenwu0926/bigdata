#!/bin/bash
# 使用 Docker 编译项目（无需本地安装 Maven）

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$DOCKER_DIR/.." && pwd)"

echo "=========================================="
echo "  使用 Docker 编译项目"
echo "=========================================="

cd "$PROJECT_DIR"

# 使用 Maven Docker 镜像编译
echo "[1/2] 编译项目..."
docker run --rm \
    -v "$PROJECT_DIR":/build \
    -v maven-repo:/root/.m2 \
    -w /build \
    maven:3.8-openjdk-8 \
    mvn clean package -DskipTests -q

echo "[2/2] 复制 JAR 文件..."
mkdir -p "$DOCKER_DIR/jars"
cp "$PROJECT_DIR/target/ImoocLogAnalysis-1.0-SNAPSHOT-jar-with-dependencies.jar" "$DOCKER_DIR/jars/"

echo ""
echo "编译完成！JAR 文件已复制到: docker/jars/"
echo ""
echo "下一步："
echo "  cd docker && docker-compose up -d"
