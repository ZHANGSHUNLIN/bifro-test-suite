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
            local backup_log
            backup_log="${LOG_FILE}.$(date +%Y%m%d_%H%M%S).bak"
            mv "$LOG_FILE" "$backup_log"
            echo "Backed up log: $backup_log"
        fi

        find "$LOG_DIR" -name "*.bak" -mtime +7 -delete 2>/dev/null
    fi

    echo "Cleanup completed"
}
