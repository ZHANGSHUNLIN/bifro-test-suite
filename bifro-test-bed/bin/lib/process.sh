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

check_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
            return 0
        fi
        rm -f "$PID_FILE"
    fi
    return 1
}

wait_for_exit() {
    local timeout="$1"
    while [ "$timeout" -gt 0 ]; do
        check_running
        if [ $? -eq 0 ]; then
            sleep 1
            timeout=$((timeout - 1))
            echo -n "."
        else
            return 0
        fi
    done
    return 1
}

terminate_process() {
    local timeout="${1:-$STOP_TIMEOUT}"

    check_running
    if [ $? -ne 0 ]; then
        echo "Application is not running"
        return 1
    fi

    echo "Stopping $APP_NAME (PID: $PID) ..."
    kill "$PID"

    wait_for_exit "$timeout"
    if [ $? -ne 0 ]; then
        echo ""
        echo "Force stopping..."
        kill -9 "$PID" 2>/dev/null || true
        sleep 2
    fi

    rm -f "$PID_FILE"
    echo "Application stopped"
    return 0
}
