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

status_app() {
    check_running
    if [ $? -eq 0 ]; then
        echo "$APP_NAME is running"
        echo "   PID:      $PID"
        echo "   Start time: $(ps -p "$PID" -o lstart= 2>/dev/null || echo "unavailable")"

        local mem_kb
        mem_kb=$(ps -p "$PID" -o rss= 2>/dev/null | awk '{print $1}')
        if [ -n "$mem_kb" ]; then
            echo "   Memory usage: $(echo "$mem_kb" | awk '{printf "%.1f MB", $1/1024}')"
        else
            echo "   Memory usage: unavailable"
        fi

        echo "   Log file: $LOG_FILE"

        if ps -p "$PID" -o args= 2>/dev/null | grep -q "spring.profiles.active"; then
            local active_profile
            active_profile=$(ps -p "$PID" -o args= 2>/dev/null | grep -o "spring.profiles.active=[^ ]*" | cut -d= -f2)
            if [ -n "$active_profile" ]; then
                echo "   Profile: $active_profile"
            fi
        fi
    else
        echo "$APP_NAME is not running"
    fi

    echo ""
    echo "Directory info:"
    echo "   Profiles: $(ls -1 "$CONFIG_DIR"/*.yml "$CONFIG_DIR"/*.properties 2>/dev/null | wc -l)"
    echo "   Dependency JARs: $(find "$LIB_DIR" -name "*.jar" 2>/dev/null | wc -l)"
    local log_size
    log_size=$(du -h "$LOG_FILE" 2>/dev/null | cut -f1)
    if [ -z "$log_size" ]; then
        log_size="N/A"
    fi
    echo "   Log size: $log_size"
}
