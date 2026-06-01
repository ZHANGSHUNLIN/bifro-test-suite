#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# =============================================
# =============================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -d "$SCRIPT_DIR/../conf" ] && [ -d "$SCRIPT_DIR/../lib" ]; then
    APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"

elif [ -d "$SCRIPT_DIR/conf" ] && [ -d "$SCRIPT_DIR/lib" ]; then
    APP_HOME="$SCRIPT_DIR"

else
    APP_HOME="/home/work/bifro-test-suite"
fi

# =============================================
# =============================================
APP_NAME="bifro-test-suite"
MAIN_CLASS="org.apache.bifromq.testsuite.app.App"

CONFIG_DIR="$APP_HOME/conf"
LIB_DIR="$APP_HOME/lib"
LOG_DIR="$APP_HOME/logs"
PID_FILE="$APP_HOME/bin/pid"
LOG_FILE="$LOG_DIR/$APP_NAME.log"
GC_LOG_OPTS="${GC_LOG_OPTS:--Xlog:gc*,safepoint:file=$LOG_DIR/gc-%t.log:time,uptime,level,tags:filecount=10,filesize=100m}"

# =============================================

cd "$APP_HOME" || {
    echo "Error: failed to enter application directory: $APP_HOME"
    exit 1
}

if [ "${DEBUG:-false}" = "true" ]; then
    echo "Debug info:"
    echo "  Script directory: $SCRIPT_DIR"
    echo "  Application directory: $APP_HOME"
    echo "  Config directory: $CONFIG_DIR"
    echo "  Library directory: $LIB_DIR"
    echo ""
fi

if [ -z "$JVM_OPTS" ]; then
    if [ "$(uname)" = "Darwin" ]; then
        TOTAL_MEM_BYTES=$(sysctl hw.memsize | awk '{print $2}')
        TOTAL_MEM_MB=$((TOTAL_MEM_BYTES / 1024 / 1024))
    else
        TOTAL_MEM_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}')
        if [ -n "$TOTAL_MEM_KB" ]; then
            TOTAL_MEM_MB=$((TOTAL_MEM_KB / 1024))
        else
            TOTAL_MEM_MB=4096
            echo "Warning: failed to read system memory, using default: ${TOTAL_MEM_MB}MB"
        fi
    fi

    JVM_MEM_MB=$((TOTAL_MEM_MB * 75 / 100))

    if [ $JVM_MEM_MB -lt 512 ]; then
        JVM_MEM_MB=512
    fi

    JVM_OPTS="-Xms${JVM_MEM_MB}m -Xmx${JVM_MEM_MB}m -XX:+UseZGC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR \
-XX:ConcGCThreads=4"
fi
# ---------------------------------------------------------

# =============================================
# =============================================

show_usage() {
    echo "Spring Boot Application Launcher"
    echo ""
    echo "Usage: $0 {start|stop|restart|status|log|clean}"
    echo "       $0 start [profile] [jvm_options]"
    echo ""
    echo "Commands:"
    echo "  start [profile]    Start application, optional profile (dev, prod, test)"
    echo "  stop               Stop application"
    echo "  restart [profile]  Restart application"
    echo "  status             Show application status"
    echo "  log                Show live log"
    echo "  clean              Clean logs and PID file"
    echo ""
    echo "Examples:"
    echo "  $0 start"
    echo "  $0 start prod"
    echo "  $0 start dev \"-Xmx2048m\""
    echo "  $0 stop"
    echo "  $0 restart test"
    echo ""
    echo "Directory structure:"
    echo "  ./"
    echo "  ├── conf/"
    echo "  ├── lib/"
    echo "  ├── logs/"
    echo "  └── classes/"
    exit 1
}

check_environment() {
    if [ ! -d "$CONFIG_DIR" ]; then
        echo "Error: config directory does not exist: $CONFIG_DIR"
        echo "Create the directory and add configuration file: application.yml"
        exit 1
    fi

    if [ ! -d "$LIB_DIR" ]; then
        echo "Error: library directory does not exist: $LIB_DIR"
        echo "Add dependency JAR files to this directory"
        exit 1
    fi

    JAR_COUNT=$(find "$LIB_DIR" -name "*.jar" 2>/dev/null | wc -l)
    if [ "$JAR_COUNT" -eq 0 ]; then
        echo "Warning: no JAR files found in library directory: $LIB_DIR"
        echo "Ensure dependency JAR files are in this directory"
    fi

    if [ -d "classes" ]; then
        MAIN_CLASS_FILE=$(echo "$MAIN_CLASS" | tr '.' '/').class
        if [ ! -f "classes/$MAIN_CLASS_FILE" ]; then
            echo "Warning: main class file not found: classes/$MAIN_CLASS_FILE"
            echo "Check MAIN_CLASS configuration or build the project"
        fi
    fi

    mkdir -p "$LOG_DIR"
}

build_classpath() {
    local classpath=""

    if [ -d "classes" ]; then
        classpath="classes"
    fi

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

check_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
            return 0
        else
            rm -f "$PID_FILE"
            return 1
        fi
    else
        return 1
    fi
}

start_app() {
    local profile="$1"
    local extra_jvm_opts="$2"

    check_running
    if [ $? -eq 0 ]; then
        echo "Application is already running (PID: $PID)"
        return 1
    fi

    echo "Starting $APP_NAME ..."

    CLASSPATH=$(build_classpath)
    if [ -z "$CLASSPATH" ]; then
        echo "Error: failed to build classpath, check lib directory"
        return 1
    fi

    SPRING_OPTS=""
    SPRING_ARGS=()
    if [ -n "$profile" ]; then
        SPRING_OPTS="--spring.profiles.active=$profile"
        SPRING_ARGS+=("--spring.profiles.active=$profile")
        echo "Using profile: $profile"
        echo "Will load: conf/application.yml, conf/application-$profile.yml"
    else
        echo "Using default config: conf/application.yml"
    fi

    SPRING_OPTS="$SPRING_OPTS --spring.config.location=conf/"
    SPRING_ARGS+=("--spring.config.location=conf/")

    FINAL_JVM_OPTS="$JVM_OPTS $GC_LOG_OPTS"
    if [ -n "$extra_jvm_opts" ]; then
        FINAL_JVM_OPTS="$FINAL_JVM_OPTS $extra_jvm_opts"
    fi

    JVM_ARGS=()
    if [ -n "$FINAL_JVM_OPTS" ]; then
        # shellcheck disable=SC2206
        JVM_ARGS=($FINAL_JVM_OPTS)
    fi
    CMD=(java "${JVM_ARGS[@]}" -cp "$CLASSPATH" "$MAIN_CLASS" "${SPRING_ARGS[@]}")

    echo "Start command:"
    printf "  "
    printf "%q " "${CMD[@]}"
    printf "\n"
    echo ""
    echo "Log file: $LOG_FILE"
    echo "PID file:  $PID_FILE"
    echo ""

    nohup "${CMD[@]}" > "$LOG_FILE" 2>&1 &

    echo $! > "$PID_FILE"

    sleep 3

    check_running
    if [ $? -eq 0 ]; then
        echo "✅ Started successfully! (PID: $PID)"
        echo "📋 Use this command to view logs:"
        echo "    tail -f $LOG_FILE"
        return 0
    else
        echo "❌ Start failed!"
        echo "View logs for details:"
        echo "    tail -n 50 $LOG_FILE"
        rm -f "$PID_FILE"
        return 1
    fi
}

stop_app() {
    check_running
    if [ $? -eq 0 ]; then
        echo "Stopping $APP_NAME (PID: $PID) ..."

        kill $PID

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
            echo "Force stopping..."
            kill -9 $PID
            sleep 2
        fi

        rm -f "$PID_FILE"
        echo "✅ Application stopped"
        return 0
    else
        echo "Application is not running"
        return 1
    fi
}

restart_app() {
    local profile="$1"
    local extra_jvm_opts="$2"

    echo "Restarting $APP_NAME ..."
    stop_app
    sleep 2
    start_app "$profile" "$extra_jvm_opts"
}

status_app() {
    check_running
    if [ $? -eq 0 ]; then
        echo "✅ $APP_NAME is running"
        echo "   PID:      $PID"

        if [ "$(uname)" = "Darwin" ]; then
            echo "   Start time: $(ps -p $PID -o lstart= 2>/dev/null || echo "unavailable")"
            MEM_KB=$(ps -p $PID -o rss= 2>/dev/null | awk '{print $1}')
            if [ -n "$MEM_KB" ]; then
                echo "   Memory usage: $(echo "$MEM_KB" | awk '{printf "%.1f MB", $1/1024}')"
            else
                echo "   Memory usage: unavailable"
            fi
        else
            echo "   Start time: $(ps -p $PID -o lstart= 2>/dev/null || echo "unavailable")"
            echo "   Memory usage: $(ps -p $PID -o rss= 2>/dev/null | awk '{printf "%.1f MB", $1/1024}' || echo "unavailable")"
        fi

        echo "   Log file: $LOG_FILE"

        if ps -p $PID -o args= 2>/dev/null | grep -q "spring.profiles.active"; then
            local active_profile=$(ps -p $PID -o args= 2>/dev/null | grep -o "spring.profiles.active=[^ ]*" | cut -d= -f2)
            if [ -n "$active_profile" ]; then
                echo "   Profile: $active_profile"
            fi
        fi
    else
        echo "❌ $APP_NAME is not running"
    fi

    echo ""
    echo "📁 Directory info:"
    echo "   Profile: $(ls -1 $CONFIG_DIR/*.yml $CONFIG_DIR/*.properties 2>/dev/null | wc -l) "
    echo "   Dependency JARs:  $(find $LIB_DIR -name "*.jar" 2>/dev/null | wc -l) "
    echo "   Log size: $(du -h "$LOG_FILE" 2>/dev/null | cut -f1 || echo "N/A")"
}

tail_log() {
    if [ -f "$LOG_FILE" ]; then
        echo "Viewing log: $LOG_FILE"
        echo "Press Ctrl+C to exit"
        echo "----------------------------------------"
        tail -f "$LOG_FILE"
    else
        echo "Log file does not exist: $LOG_FILE"
        echo "Application may have never started"
    fi
}

cleanup() {
    echo "Cleaning..."

    check_running
    if [ $? -eq 0 ]; then
        echo "Application is running, stopping first..."
        stop_app
    fi

    if [ -f "$PID_FILE" ]; then
        rm -f "$PID_FILE"
        echo "Deleted PID file: $PID_FILE"
    fi

    if [ -d "$LOG_DIR" ]; then
        if [ -f "$LOG_FILE" ]; then
            BACKUP_LOG="${LOG_FILE}.$(date +%Y%m%d_%H%M%S).bak"
            mv "$LOG_FILE" "$BACKUP_LOG"
            echo "Backed up log: $BACKUP_LOG"
        fi

        find "$LOG_DIR" -name "*.bak" -mtime +7 -delete 2>/dev/null
    fi

    echo "✅ Cleanup completed"
}

show_config() {
    echo "=== Application configuration ==="
    echo "Application name:    $APP_NAME"
    echo "Main class:       $MAIN_CLASS"
    echo "Config directory:   $CONFIG_DIR"
    echo "Library directory:   $LIB_DIR"
    echo "Log directory:   $LOG_DIR"
    echo "JVM options:    $JVM_OPTS"
    echo ""

    if [ -d "$CONFIG_DIR" ]; then
        echo "Profile list:"
        ls -la "$CONFIG_DIR"/*.yml "$CONFIG_DIR"/*.properties 2>/dev/null | \
        while read line; do
            echo "  $line"
        done || echo "  No profiles found"
    fi

    if [ -d "$LIB_DIR" ]; then
        echo ""
        echo "Dependency JAR count: $(find "$LIB_DIR" -name "*.jar" 2>/dev/null | wc -l)"
        echo "First 5 dependencies:"
        ls -1 "$LIB_DIR"/*.jar 2>/dev/null | head -5 | while read jar; do
            echo "  $(basename "$jar")"
        done
    fi
}

# =============================================
# =============================================

if [ $# -eq 0 ]; then
    show_usage
fi

check_environment

COMMAND="$1"
shift

# =============================================
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
            echo " Other Unix system"
            ;;
    esac
}

if [ "${DEBUG:-false}" = "true" ]; then
    echo "System info:"
    echo "  Operating system:$(detect_os)"
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
        echo "Unknown command: $COMMAND"
        echo ""
        show_usage
        ;;
esac
