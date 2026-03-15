#!/bin/bash

# =============================================
# 自动检测应用目录
# =============================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 方式1：如果脚本在应用目录的bin下
if [ -d "$SCRIPT_DIR/../conf" ] && [ -d "$SCRIPT_DIR/../lib" ]; then
    APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"

# 方式2：如果脚本在应用目录下
elif [ -d "$SCRIPT_DIR/conf" ] && [ -d "$SCRIPT_DIR/lib" ]; then
    APP_HOME="$SCRIPT_DIR"

else
    APP_HOME="/home/work/bifro-test-suite"  # 修改为你的应用实际路径
fi

# =============================================
# 配置区域
# =============================================
APP_NAME="bifro-test-suite"
MAIN_CLASS="com.baidu.duhome.App"

# 基于APP_HOME的路径
CONFIG_DIR="$APP_HOME/conf"
LIB_DIR="$APP_HOME/lib"
LOG_DIR="$APP_HOME/logs"
PID_FILE="$APP_HOME/bin/pid"
LOG_FILE="$LOG_DIR/$APP_NAME.log"

# =============================================

# 进入应用目录
cd "$APP_HOME" || {
    echo "错误: 无法进入应用目录: $APP_HOME"
    exit 1
}

# 显示目录信息（调试用）
if [ "${DEBUG:-false}" = "true" ]; then
    echo "调试信息:"
    echo "  脚本目录: $SCRIPT_DIR"
    echo "  应用目录: $APP_HOME"
    echo "  配置目录: $CONFIG_DIR"
    echo "  依赖目录: $LIB_DIR"
    echo ""
fi

# --- 修改：JVM参数支持环境变量，若未设置则默认计算 75% 内存 （兼容macOS和Linux）---
if [ -z "$JVM_OPTS" ]; then
    # 检测操作系统并获取内存
    if [ "$(uname)" = "Darwin" ]; then
        # macOS系统
        TOTAL_MEM_BYTES=$(sysctl hw.memsize | awk '{print $2}')
        TOTAL_MEM_MB=$((TOTAL_MEM_BYTES / 1024 / 1024))
    else
        # Linux系统
        TOTAL_MEM_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}')
        if [ -n "$TOTAL_MEM_KB" ]; then
            TOTAL_MEM_MB=$((TOTAL_MEM_KB / 1024))
        else
            # 如果无法获取系统内存，使用默认值
            TOTAL_MEM_MB=4096
            echo "警告: 无法获取系统内存信息，使用默认值: ${TOTAL_MEM_MB}MB"
        fi
    fi

    # 计算 75% 并确保最小值
    JVM_MEM_MB=$((TOTAL_MEM_MB * 75 / 100))

    # 设置最小内存为512MB，最大不超过系统内存的75%
    if [ $JVM_MEM_MB -lt 512 ]; then
        JVM_MEM_MB=512
    fi

    # 设置默认 JVM 参数
    JVM_OPTS="-Xms${JVM_MEM_MB}m -Xmx${JVM_MEM_MB}m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError"
fi
# ---------------------------------------------------------

# =============================================
# 函数定义
# =============================================

# 显示使用说明
show_usage() {
    echo "Spring Boot Application Launcher"
    echo ""
    echo "Usage: $0 {start|stop|restart|status|log|clean}"
    echo "       $0 start [profile] [jvm_options]"
    echo ""
    echo "Commands:"
    echo "  start [profile]    启动应用，可选profile (dev, prod, test)"
    echo "  stop               停止应用"
    echo "  restart [profile]  重启应用"
    echo "  status             查看应用状态"
    echo "  log                查看实时日志"
    echo "  clean              清理日志和PID文件"
    echo ""
    echo "Examples:"
    echo "  $0 start            # 默认启动"
    echo "  $0 start prod       # 使用prod profile启动"
    echo "  $0 start dev \"-Xmx2048m\"  # 使用dev profile并指定JVM内存"
    echo "  $0 stop"
    echo "  $0 restart test"
    echo ""
    echo "Directory structure:"
    echo "  ./                  # 应用根目录"
    echo "  ├── conf/           # 配置文件目录 (application.yml)"
    echo "  ├── lib/            # 依赖JAR目录"
    echo "  ├── logs/           # 日志目录"
    echo "  └── classes/        # 编译后的class文件 (可选)"
    exit 1
}

# 检查环境
check_environment() {
    # 检查配置文件目录
    if [ ! -d "$CONFIG_DIR" ]; then
        echo "错误: 配置文件目录不存在: $CONFIG_DIR"
        echo "请创建目录并放置配置文件: application.yml"
        exit 1
    fi

    # 检查依赖目录
    if [ ! -d "$LIB_DIR" ]; then
        echo "错误: 依赖库目录不存在: $LIB_DIR"
        echo "请将依赖JAR文件放入该目录"
        exit 1
    fi

    # 检查是否有JAR文件
    JAR_COUNT=$(find "$LIB_DIR" -name "*.jar" 2>/dev/null | wc -l)
    if [ "$JAR_COUNT" -eq 0 ]; then
        echo "警告: 依赖库目录中没有找到JAR文件: $LIB_DIR"
        echo "请确保已将依赖JAR放入该目录"
    fi

    # 检查主类是否存在（在classes目录中）
    if [ -d "classes" ]; then
        MAIN_CLASS_FILE=$(echo "$MAIN_CLASS" | tr '.' '/').class
        if [ ! -f "classes/$MAIN_CLASS_FILE" ]; then
            echo "警告: 主类文件未找到: classes/$MAIN_CLASS_FILE"
            echo "请检查MAIN_CLASS配置或编译项目"
        fi
    fi

    # 创建日志目录
    mkdir -p "$LOG_DIR"
}

# 构建类路径
build_classpath() {
    local classpath=""

    # 1. 添加classes目录（如果有）
    if [ -d "classes" ]; then
        classpath="classes"
    fi

    # 2. 添加lib目录下所有JAR
    if [ -d "$LIB_DIR" ]; then
        for jar in "$LIB_DIR"/*.jar; do
            if [ -f "$jar" ]; then
                if [ -z "$classpath" ]; then
                    classpath="$jar"
                else
                    classpath="$classpath:$jar"
                fi
            fi
        done
    fi

    echo "$classpath"
}

# 检查应用是否运行
check_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
            return 0  # 正在运行
        else
            # PID文件存在但进程不存在，删除无效的PID文件
            rm -f "$PID_FILE"
            return 1
        fi
    else
        return 1  # 没有运行
    fi
}

# 启动应用
start_app() {
    local profile="$1"
    local extra_jvm_opts="$2"

    check_running
    if [ $? -eq 0 ]; then
        echo "应用已经在运行 (PID: $PID)"
        return 1
    fi

    echo "正在启动 $APP_NAME ..."

    # 构建完整的类路径
    CLASSPATH=$(build_classpath)
    if [ -z "$CLASSPATH" ]; then
        echo "错误: 无法构建类路径，请检查lib目录"
        return 1
    fi

    # 构建Spring Boot参数
    SPRING_OPTS=""
    if [ -n "$profile" ]; then
        SPRING_OPTS="--spring.profiles.active=$profile"
        echo "使用配置文件: $profile"
        echo "将加载: conf/application.yml, conf/application-$profile.yml"
    else
        echo "使用默认配置文件: conf/application.yml"
    fi

    # 添加配置文件目录参数
    SPRING_OPTS="$SPRING_OPTS --spring.config.location=conf/"

    # 组合JVM参数
    FINAL_JVM_OPTS="$JVM_OPTS"
    if [ -n "$extra_jvm_opts" ]; then
        FINAL_JVM_OPTS="$FINAL_JVM_OPTS $extra_jvm_opts"
    fi

    # 构建启动命令
    CMD="java $FINAL_JVM_OPTS -cp \"$CLASSPATH\" $MAIN_CLASS $SPRING_OPTS"

    echo "启动命令:"
    echo "  $CMD"
    echo ""
    echo "日志文件: $LOG_FILE"
    echo "PID文件:  $PID_FILE"
    echo ""

    # 使用nohup在后台启动
    nohup $CMD > "$LOG_FILE" 2>&1 &

    # 保存PID
    echo $! > "$PID_FILE"

    # 等待几秒检查是否启动成功
    sleep 3

    check_running
    if [ $? -eq 0 ]; then
        echo "✅ 启动成功! (PID: $PID)"
        echo "📋 使用以下命令查看日志:"
        echo "    tail -f $LOG_FILE"
        return 0
    else
        echo "❌ 启动失败!"
        echo "查看日志获取详细信息:"
        echo "    tail -n 50 $LOG_FILE"
        rm -f "$PID_FILE"
        return 1
    fi
}

# 停止应用
stop_app() {
    check_running
    if [ $? -eq 0 ]; then
        echo "正在停止 $APP_NAME (PID: $PID) ..."

        # 优雅关闭
        kill $PID

        # 等待最多30秒
        local timeout=30
        while [ $timeout -gt 0 ]; do
            check_running
            if [ $? -eq 0 ]; then
                sleep 1
                timeout=$((timeout - 1))
                echo -n "."
            else
                break
            fi
        done

        if [ $timeout -eq 0 ]; then
            echo ""
            echo "强制停止..."
            kill -9 $PID
            sleep 2
        fi

        rm -f "$PID_FILE"
        echo "✅ 应用已停止"
        return 0
    else
        echo "应用未运行"
        return 1
    fi
}

# 重启应用
restart_app() {
    local profile="$1"
    local extra_jvm_opts="$2"

    echo "重启 $APP_NAME ..."
    stop_app
    sleep 2
    start_app "$profile" "$extra_jvm_opts"
}

# 查看状态
status_app() {
    check_running
    if [ $? -eq 0 ]; then
        echo "✅ $APP_NAME 正在运行"
        echo "   PID:      $PID"

        # 兼容不同系统的ps命令
        if [ "$(uname)" = "Darwin" ]; then
            # macOS系统
            echo "   启动时间: $(ps -p $PID -o lstart= 2>/dev/null || echo "无法获取")"
            MEM_KB=$(ps -p $PID -o rss= 2>/dev/null | awk '{print $1}')
            if [ -n "$MEM_KB" ]; then
                echo "   内存使用: $(echo "$MEM_KB" | awk '{printf "%.1f MB", $1/1024}')"
            else
                echo "   内存使用: 无法获取"
            fi
        else
            # Linux系统
            echo "   启动时间: $(ps -p $PID -o lstart= 2>/dev/null || echo "无法获取")"
            echo "   内存使用: $(ps -p $PID -o rss= 2>/dev/null | awk '{printf "%.1f MB", $1/1024}' || echo "无法获取")"
        fi

        echo "   日志文件: $LOG_FILE"

        # 显示使用的配置文件（兼容不同系统）
        if ps -p $PID -o args= 2>/dev/null | grep -q "spring.profiles.active"; then
            local active_profile=$(ps -p $PID -o args= 2>/dev/null | grep -o "spring.profiles.active=[^ ]*" | cut -d= -f2)
            if [ -n "$active_profile" ]; then
                echo "   配置文件: $active_profile"
            fi
        fi
    else
        echo "❌ $APP_NAME 未运行"
    fi

    # 显示目录信息
    echo ""
    echo "📁 目录信息:"
    echo "   配置文件: $(ls -1 $CONFIG_DIR/*.yml $CONFIG_DIR/*.properties 2>/dev/null | wc -l) 个"
    echo "   依赖JAR:  $(find $LIB_DIR -name "*.jar" 2>/dev/null | wc -l) 个"
    echo "   日志大小: $(du -h "$LOG_FILE" 2>/dev/null | cut -f1 || echo "N/A")"
}

# 查看日志
tail_log() {
    if [ -f "$LOG_FILE" ]; then
        echo "正在查看日志: $LOG_FILE"
        echo "按 Ctrl+C 退出"
        echo "----------------------------------------"
        tail -f "$LOG_FILE"
    else
        echo "日志文件不存在: $LOG_FILE"
        echo "应用可能从未启动过"
    fi
}

# 清理日志和PID
cleanup() {
    echo "正在清理..."

    # 停止应用（如果正在运行）
    check_running
    if [ $? -eq 0 ]; then
        echo "应用正在运行，先停止..."
        stop_app
    fi

    # 删除PID文件
    if [ -f "$PID_FILE" ]; then
        rm -f "$PID_FILE"
        echo "已删除PID文件: $PID_FILE"
    fi

    # 清理日志（可选，保留最近7天的日志）
    if [ -d "$LOG_DIR" ]; then
        # 备份当前日志
        if [ -f "$LOG_FILE" ]; then
            BACKUP_LOG="${LOG_FILE}.$(date +%Y%m%d_%H%M%S).bak"
            mv "$LOG_FILE" "$BACKUP_LOG"
            echo "已备份日志: $BACKUP_LOG"
        fi

        # 清理旧日志备份（保留7天）
        find "$LOG_DIR" -name "*.bak" -mtime +7 -delete 2>/dev/null
    fi

    echo "✅ 清理完成"
}

# 显示配置信息
show_config() {
    echo "=== 应用配置信息 ==="
    echo "应用名称:    $APP_NAME"
    echo "主类:       $MAIN_CLASS"
    echo "配置目录:   $CONFIG_DIR"
    echo "依赖目录:   $LIB_DIR"
    echo "日志目录:   $LOG_DIR"
    echo "JVM参数:    $JVM_OPTS"
    echo ""

    # 显示配置文件
    if [ -d "$CONFIG_DIR" ]; then
        echo "配置文件列表:"
        ls -la "$CONFIG_DIR"/*.yml "$CONFIG_DIR"/*.properties 2>/dev/null | \
        while read line; do
            echo "  $line"
        done || echo "  未找到配置文件"
    fi

    # 显示依赖信息
    if [ -d "$LIB_DIR" ]; then
        echo ""
        echo "依赖JAR数量: $(find "$LIB_DIR" -name "*.jar" 2>/dev/null | wc -l)"
        echo "前5个依赖:"
        ls -1 "$LIB_DIR"/*.jar 2>/dev/null | head -5 | while read jar; do
            echo "  $(basename "$jar")"
        done
    fi
}

# =============================================
# 主程序
# =============================================

# 检查参数
if [ $# -eq 0 ]; then
    show_usage
fi

# 检查环境
check_environment

# 解析命令
COMMAND="$1"
shift

# =============================================
# 系统兼容性检测
# =============================================
detect_os() {
    case "$(uname)" in
        Darwin)
            echo " macOS"
            ;;
        Linux)
            echo " Linux"
            ;;
        *)
            echo " 其他 Unix 系统"
            ;;
    esac
}

# 显示系统信息（调试用）
if [ "${DEBUG:-false}" = "true" ]; then
    echo "系统信息:"
    echo "  操作系统:$(detect_os)"
    echo ""
fi

case "$COMMAND" in
    start)
        PROFILE="$1"
        EXTRA_JVM_OPTS="$2"
        start_app "$PROFILE" "$EXTRA_JVM_OPTS"
        ;;

    stop)
        stop_app
        ;;

    restart)
        PROFILE="$1"
        EXTRA_JVM_OPTS="$2"
        restart_app "$PROFILE" "$EXTRA_JVM_OPTS"
        ;;

    status)
        status_app
        ;;

    log)
        tail_log
        ;;

    clean|cleanup)
        cleanup
        ;;

    config)
        show_config
        ;;

    help|-h|--help)
        show_usage
        ;;

    *)
        echo "未知命令: $COMMAND"
        echo ""
        show_usage
        ;;
esac