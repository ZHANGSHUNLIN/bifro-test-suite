#!/bin/bash
# JaCoCo 覆盖率报告生成脚本
# 用法: ./generate-report.sh [module]
# 示例: ./generate-report.sh          # 生成所有模块报告
#       ./generate-report.sh bifro-test-bed  # 只生成 bifro-test-bed 模块报告

set -e

MODULE=$1
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

cd "$PROJECT_DIR"

echo "========================================"
echo "  JaCoCo 覆盖率报告生成工具"
echo "========================================"
echo ""

# 清理并运行测试
if [ -z "$MODULE" ]; then
    echo "正在运行所有模块的测试..."
    mvn clean test
else
    echo "正在运行 $MODULE 模块的测试..."
    mvn clean test -pl "$MODULE" -am
fi

echo ""
echo "========================================"
echo "  报告生成完成"
echo "========================================"
echo ""

# 查找生成的报告
if [ -z "$MODULE" ]; then
    # 查找所有模块的报告
    REPORTS=$(find . -path "*/target/site/jacoco/index.html" 2>/dev/null)
    if [ -n "$REPORTS" ]; then
        echo "报告位置:"
        echo "$REPORTS" | while read -r report; do
            echo "  - $report"
        done
    fi
else
    if [ -f "$MODULE/target/site/jacoco/index.html" ]; then
        echo "报告位置: $MODULE/target/site/jacoco/index.html"
    fi
fi

echo ""
echo "提示: 使用 'open' 命令在浏览器中查看 HTML 报告"
echo "示例: open bifro-test-bed/target/site/jacoco/index.html"
