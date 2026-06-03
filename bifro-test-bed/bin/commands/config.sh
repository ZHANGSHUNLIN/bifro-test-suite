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

show_config() {
    initialize_jvm_opts

    echo "=== Application configuration ==="
    echo "Application name: $APP_NAME"
    echo "Main class:       $MAIN_CLASS"
    echo "Config directory: $CONFIG_DIR"
    echo "Library directory: $LIB_DIR"
    echo "Log directory:    $LOG_DIR"
    echo "JVM options:      $JVM_OPTS"
    echo "GC log options:   $GC_LOG_OPTS"
    echo "Start timeout:    ${START_TIMEOUT}s"
    echo "Start stable:     ${START_STABLE_SECONDS}s"
    echo "Stop timeout:     ${STOP_TIMEOUT}s"
    echo ""

    if [ -d "$CONFIG_DIR" ]; then
        echo "Profile list:"
        ls -la "$CONFIG_DIR"/*.yml "$CONFIG_DIR"/*.properties 2>/dev/null | while read -r line; do
            echo "  $line"
        done || echo "  No profiles found"
    fi

    if [ -d "$LIB_DIR" ]; then
        echo ""
        echo "Dependency JAR count: $(find "$LIB_DIR" -name "*.jar" 2>/dev/null | wc -l)"
        echo "First 5 dependencies:"
        ls -1 "$LIB_DIR"/*.jar 2>/dev/null | head -5 | while read -r jar; do
            echo "  $(basename "$jar")"
        done
    fi
}
