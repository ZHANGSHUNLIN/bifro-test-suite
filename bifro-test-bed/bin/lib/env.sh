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

BIN_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_DIR="${SCRIPT_DIR:-$(cd "$BIN_LIB_DIR/.." && pwd)}"

if [ -d "$SCRIPT_DIR/../conf" ]; then
    APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"
elif [ -d "$SCRIPT_DIR/conf" ]; then
    APP_HOME="$SCRIPT_DIR"
else
    APP_HOME="${APP_HOME:-/home/work/bifro-test-suite}"
fi

APP_NAME="${APP_NAME:-bifro-test-suite}"
MAIN_CLASS="${MAIN_CLASS:-org.apache.bifromq.testsuite.app.App}"

CONFIG_DIR="$APP_HOME/conf"
LIB_DIR="$APP_HOME/lib"
LOG_DIR="$APP_HOME/logs"
PID_FILE="$APP_HOME/bin/pid"
LOG_FILE="$LOG_DIR/$APP_NAME.log"
STOP_TIMEOUT="${STOP_TIMEOUT:-150}"
START_TIMEOUT="${START_TIMEOUT:-60}"
START_STABLE_SECONDS="${START_STABLE_SECONDS:-3}"
if [ -z "${GC_LOG_OPTS+x}" ]; then
    GC_LOG_OPTS="-Xlog:gc*,safepoint:file=$LOG_DIR/gc-%t.log:time,uptime,level,tags:filecount=10,filesize=100m"
fi

cd "$APP_HOME" || {
    echo "Error: failed to enter application directory: $APP_HOME"
    exit 1
}

detect_os() {
    case "$(uname)" in
        Darwin)
            echo "macOS"
            ;;
        Linux)
            echo "Linux"
            ;;
        *)
            echo "Other Unix system"
            ;;
    esac
}

print_debug_info() {
    if [ "${DEBUG:-false}" = "true" ]; then
        echo "Debug info:"
        echo "  Script directory: $SCRIPT_DIR"
        echo "  Application directory: $APP_HOME"
        echo "  Config directory: $CONFIG_DIR"
        echo "  Library directory: $LIB_DIR"
        echo "  Operating system: $(detect_os)"
        echo ""
    fi
}

default_jvm_opts() {
    local total_mem_mb
    local jvm_mem_mb

    if [ "$(uname)" = "Darwin" ]; then
        local total_mem_bytes
        total_mem_bytes=$(sysctl hw.memsize | awk '{print $2}')
        total_mem_mb=$((total_mem_bytes / 1024 / 1024))
    else
        local total_mem_kb
        total_mem_kb=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}')
        if [ -n "$total_mem_kb" ]; then
            total_mem_mb=$((total_mem_kb / 1024))
        else
            total_mem_mb=4096
            echo "Warning: failed to read system memory, using default: ${total_mem_mb}MB"
        fi
    fi

    jvm_mem_mb=$((total_mem_mb * 75 / 100))
    if [ "$jvm_mem_mb" -lt 512 ]; then
        jvm_mem_mb=512
    fi

    echo "-Xms${jvm_mem_mb}m -Xmx${jvm_mem_mb}m -XX:+UseZGC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR -XX:ConcGCThreads=4"
}

initialize_jvm_opts() {
    if [ -z "$JVM_OPTS" ]; then
        JVM_OPTS="$(default_jvm_opts)"
    fi
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

    local jar_count
    jar_count=$(find "$LIB_DIR" -name "*.jar" 2>/dev/null | wc -l)
    if [ "$jar_count" -eq 0 ]; then
        echo "Warning: no JAR files found in library directory: $LIB_DIR"
        echo "Ensure dependency JAR files are in this directory"
    fi

    if [ -d "classes" ]; then
        local main_class_file
        main_class_file=$(echo "$MAIN_CLASS" | tr '.' '/').class
        if [ ! -f "classes/$main_class_file" ]; then
            echo "Warning: main class file not found: classes/$main_class_file"
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
        local jar
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
