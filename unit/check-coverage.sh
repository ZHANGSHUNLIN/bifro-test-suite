#!/bin/bash
# JaCoCo 覆盖率检查脚本
# 用法: ./check-coverage.sh [module] [rules-file]
# 示例: ./check-coverage.sh                          # 检查所有模块，使用默认规则
#       ./check-coverage.sh bifro-test-bed           # 检查特定模块
#       ./check-coverage.sh bifro-test-bed custom-rules.xml  # 使用自定义规则

set -e

MODULE=$1
RULES_FILE=$2
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# 默认规则文件
DEFAULT_RULES="$PROJECT_DIR/unit/jacoco-rules.xml"
if [ -z "$RULES_FILE" ]; then
    RULES_FILE="$DEFAULT_RULES"
fi

cd "$PROJECT_DIR"

# 如果规则文件不存在，使用默认值
if [ ! -f "$RULES_FILE" ]; then
    echo "警告: 规则文件 $RULES_FILE 不存在，使用内置默认值"
    INSTRUCTION_MIN=50
    BRANCH_MIN=50
    LINE_MIN=50
    CLASS_MIN=50
else
    # 解析规则文件
    echo "使用规则文件: $RULES_FILE"
fi

echo "========================================"
echo "  JaCoCo 覆盖率检查工具"
echo "========================================"
echo ""

# 运行测试并生成报告
if [ -z "$MODULE" ]; then
    echo "正在运行所有模块的测试..."
    mvn clean test
    MODULE="bifro-test-bed"  # 默认检查第一个有报告的模块
else
    echo "正在运行 $MODULE 模块的测试..."
    mvn clean test -pl "$MODULE" -am
fi

# 检查报告文件
REPORT_FILE="$MODULE/target/site/jacoco/jacoco.xml"
if [ ! -f "$REPORT_FILE" ]; then
    echo "错误: 报告文件不存在: $REPORT_FILE"
    exit 1
fi

# 解析 XML 获取覆盖率数据
echo ""
echo "正在分析覆盖率数据..."
echo ""

# 使用 Python 解析 XML
python3 << 'EOF'
import xml.etree.ElementTree as ET
import sys
import os

# 默认阈值
INSTRUCTION_MIN = 50
BRANCH_MIN = 50
LINE_MIN = 50
CLASS_MIN = 50

# 检查是否有自定义规则文件
rules_file = os.environ.get('RULES_FILE', '')
if rules_file and os.path.exists(rules_file):
    try:
        tree = ET.parse(rules_file)
        root = tree.getroot()
        for rule in root.findall('.//rule'):
            for limit in rule.findall('.//limit'):
                counter = limit.get('counter', '')
                minimum = limit.get('minimum', '')
                if counter == 'INSTRUCTION':
                    INSTRUCTION_MIN = float(minimum) * 100 if minimum else 50
                elif counter == 'BRANCH':
                    BRANCH_MIN = float(minimum) * 100 if minimum else 50
                elif counter == 'LINE':
                    LINE_MIN = float(minimum) * 100 if minimum else 50
                elif counter == 'CLASS':
                    CLASS_MIN = float(minimum) * 100 if minimum else 50
    except Exception as e:
        print(f"警告: 无法解析规则文件: {e}", file=sys.stderr)

report_file = os.environ.get('REPORT_FILE', 'bifro-test-bed/target/site/jacoco/jacoco.xml')
module = os.environ.get('MODULE', 'bifro-test-bed')

tree = ET.parse(report_file)
root = tree.getroot()

# 汇总数据
total_instruction_missed = 0
total_instruction_covered = 0
total_branch_missed = 0
total_branch_covered = 0
total_line_missed = 0
total_line_covered = 0
total_class_missed = 0
total_class_covered = 0
total_method_missed = 0
total_method_covered = 0

for counter in root.findall('.//counter'):
    ctype = counter.get('type')
    missed = int(counter.get('missed', 0))
    covered = int(counter.get('covered', 0))

    if ctype == 'INSTRUCTION':
        total_instruction_missed += missed
        total_instruction_covered += covered
    elif ctype == 'BRANCH':
        total_branch_missed += missed
        total_branch_covered += covered
    elif ctype == 'LINE':
        total_line_missed += missed
        total_line_covered += covered
    elif ctype == 'CLASS':
        total_class_missed += missed
        total_class_covered += covered
    elif ctype == 'METHOD':
        total_method_missed += missed
        total_method_covered += covered

# 计算覆盖率
def calc_percent(covered, total):
    if total == 0:
        return 0.0
    return (covered * 100.0) / total

instruction_percent = calc_percent(total_instruction_covered, total_instruction_missed + total_instruction_covered)
branch_percent = calc_percent(total_branch_covered, total_branch_missed + total_branch_covered)
line_percent = calc_percent(total_line_covered, total_line_missed + total_line_covered)
class_percent = calc_percent(total_class_covered, total_class_missed + total_class_covered)
method_percent = calc_percent(total_method_covered, total_method_missed + total_method_covered)

print("========================================")
print(f"  模块: {module}")
print("========================================")
print("")
print(f"  类型          已覆盖    未覆盖    覆盖率      最低要求    状态")
print(f"  ----------------------------------------------------------------")
print(f"  INSTRUCTION  {total_instruction_covered:>8}  {total_instruction_missed:>8}  {instruction_percent:>6.2f}%    {INSTRUCTION_MIN:>6.2f}%    {'✓ PASS' if instruction_percent >= INSTRUCTION_MIN else '✗ FAIL'}")
print(f"  BRANCH       {total_branch_covered:>8}  {total_branch_missed:>8}  {branch_percent:>6.2f}%    {BRANCH_MIN:>6.2f}%    {'✓ PASS' if branch_percent >= BRANCH_MIN else '✗ FAIL'}")
print(f"  LINE         {total_line_covered:>8}  {total_line_missed:>8}  {line_percent:>6.2f}%    {LINE_MIN:>6.2f}%    {'✓ PASS' if line_percent >= LINE_MIN else '✗ FAIL'}")
print(f"  CLASS        {total_class_covered:>8}  {total_class_missed:>8}  {class_percent:>6.2f}%    {CLASS_MIN:>6.2f}%    {'✓ PASS' if class_percent >= CLASS_MIN else '✗ FAIL'}")
print(f"  METHOD       {total_method_covered:>8}  {total_method_missed:>8}  {method_percent:>6.2f}%")
print("")

# 判断是否通过
passed = (instruction_percent >= INSTRUCTION_MIN and
          branch_percent >= BRANCH_MIN and
          line_percent >= LINE_MIN and
          class_percent >= CLASS_MIN)

if passed:
    print("  ✓ 所有覆盖率要求均已满足!")
    sys.exit(0)
else:
    print("  ✗ 覆盖率未达到最低要求，请增加测试!")
    sys.exit(1)
EOF

exit_code=$?

echo ""
exit $exit_code
