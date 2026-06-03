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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/lib/env.sh"
source "$SCRIPT_DIR/lib/process.sh"
source "$SCRIPT_DIR/commands/start.sh"
source "$SCRIPT_DIR/commands/stop.sh"
source "$SCRIPT_DIR/commands/restart.sh"
source "$SCRIPT_DIR/commands/status.sh"
source "$SCRIPT_DIR/commands/log.sh"
source "$SCRIPT_DIR/commands/clean.sh"
source "$SCRIPT_DIR/commands/config.sh"

show_usage() {
    echo "Spring Boot Application Launcher"
    echo ""
    echo "Usage: $0 {start|stop|restart|status|log|clean|config}"
    echo "       $0 start [profile] [jvm_options]"
    echo ""
    echo "Commands:"
    echo "  start [profile]    Start application, optional profile (dev, prod, test)"
    echo "  stop               Stop application"
    echo "  restart [profile]  Restart application"
    echo "  status             Show application status"
    echo "  log                Show live log"
    echo "  clean              Clean logs and PID file"
    echo "  config             Show runtime configuration"
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
    echo "  |-- conf/"
    echo "  |-- lib/"
    echo "  |-- logs/"
    echo "  |-- classes/"
    echo '  `-- bin/'
    exit 1
}

if [ $# -eq 0 ]; then
    show_usage
fi

print_debug_info

COMMAND="$1"
shift

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
