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

start_app() {
    local profile="$1"
    local extra_jvm_opts="$2"

    check_environment
    initialize_jvm_opts

    check_running
    if [ $? -eq 0 ]; then
        echo "Application is already running (PID: $PID)"
        return 1
    fi

    echo "Starting $APP_NAME ..."

    local classpath
    classpath=$(build_classpath)
    if [ -z "$classpath" ]; then
        echo "Error: failed to build classpath, check lib directory"
        return 1
    fi

    local spring_args=()
    if [ -n "$profile" ]; then
        spring_args+=("--spring.profiles.active=$profile")
        echo "Using profile: $profile"
        echo "Will load: conf/application.yml, conf/application-$profile.yml"
    else
        echo "Using default config: conf/application.yml"
    fi
    spring_args+=("--spring.config.location=conf/")

    local final_jvm_opts="$JVM_OPTS $GC_LOG_OPTS"
    if [ -n "$extra_jvm_opts" ]; then
        final_jvm_opts="$final_jvm_opts $extra_jvm_opts"
    fi

    local jvm_args=()
    if [ -n "$final_jvm_opts" ]; then
        # shellcheck disable=SC2206
        jvm_args=($final_jvm_opts)
    fi

    local cmd=(java "${jvm_args[@]}" -cp "$classpath" "$MAIN_CLASS" "${spring_args[@]}")

    echo "Start command:"
    printf "  "
    printf "%q " "${cmd[@]}"
    printf "\n"
    echo ""
    echo "Log file: $LOG_FILE"
    echo "PID file:  $PID_FILE"
    echo ""

    nohup "${cmd[@]}" > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"

    local elapsed=0
    while [ "$elapsed" -lt "$START_TIMEOUT" ]; do
        sleep 1
        elapsed=$((elapsed + 1))

        check_running
        if [ $? -ne 0 ]; then
            echo "Start failed: process exited before startup completed"
            echo "View logs for details:"
            echo "    tail -n 50 $LOG_FILE"
            if [ -f "$LOG_DIR/error.log" ]; then
                echo "    tail -n 50 $LOG_DIR/error.log"
            fi
            rm -f "$PID_FILE"
            return 1
        fi

        if grep -Eq "[[:space:]]$PID[[:space:]].*Started App" "$LOG_FILE" "$LOG_DIR/info.log" 2>/dev/null; then
            sleep "$START_STABLE_SECONDS"
            check_running
            if [ $? -ne 0 ]; then
                echo "Start failed: process exited immediately after startup"
                echo "View logs for details:"
                echo "    tail -n 50 $LOG_FILE"
                if [ -f "$LOG_DIR/error.log" ]; then
                    echo "    tail -n 50 $LOG_DIR/error.log"
                fi
                rm -f "$PID_FILE"
                return 1
            fi
            echo "Started successfully (PID: $PID)"
            echo "Use this command to view logs:"
            echo "    tail -f $LOG_FILE"
            return 0
        fi
    done

    check_running
    if [ $? -eq 0 ]; then
        echo "Application is running (PID: $PID), but startup was not confirmed within ${START_TIMEOUT}s"
        echo "Use this command to view logs:"
        echo "    tail -f $LOG_FILE"
        return 0
    fi

    echo "Start failed"
    echo "View logs for details:"
    echo "    tail -n 50 $LOG_FILE"
    rm -f "$PID_FILE"
    return 1
}
