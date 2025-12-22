#!/bin/bash
# 下载 ip2region.xdb 数据库文件

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESOURCES_DIR="$PROJECT_DIR/src/main/resources"

echo "=========================================="
echo "  下载 ip2region.xdb 数据库"
echo "=========================================="

mkdir -p "$RESOURCES_DIR"

# 从 GitHub 下载
URL="https://raw.githubusercontent.com/lionsoul2014/ip2region/master/data/ip2region.xdb"

echo "下载地址: $URL"
echo "保存位置: $RESOURCES_DIR/ip2region.xdb"
echo ""

# 尝试使用 curl 或 wget
if command -v curl &> /dev/null; then
    curl -L -o "$RESOURCES_DIR/ip2region.xdb" "$URL"
elif command -v wget &> /dev/null; then
    wget -O "$RESOURCES_DIR/ip2region.xdb" "$URL"
else
    echo "错误: 需要 curl 或 wget"
    exit 1
fi

if [ -f "$RESOURCES_DIR/ip2region.xdb" ]; then
    SIZE=$(ls -lh "$RESOURCES_DIR/ip2region.xdb" | awk '{print $5}')
    echo ""
    echo "下载完成！文件大小: $SIZE"
else
    echo "下载失败，请手动下载:"
    echo "  $URL"
    echo "并保存到:"
    echo "  $RESOURCES_DIR/ip2region.xdb"
fi
