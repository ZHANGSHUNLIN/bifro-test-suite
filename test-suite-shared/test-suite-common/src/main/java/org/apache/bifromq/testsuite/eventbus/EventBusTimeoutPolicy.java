/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.eventbus;

import java.time.Duration;

public class EventBusTimeoutPolicy {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private final Duration defaultRequestTimeout;
    private final Duration taskCommandTimeout;

    public EventBusTimeoutPolicy() {
        this(DEFAULT_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    public EventBusTimeoutPolicy(Duration defaultRequestTimeout, Duration taskCommandTimeout) {
        this.defaultRequestTimeout = normalize(defaultRequestTimeout);
        this.taskCommandTimeout = normalize(taskCommandTimeout);
    }

    public Duration timeoutFor(EventBusRequestKind kind) {
        if (kind == EventBusRequestKind.TASK_COMMAND) {
            return taskCommandTimeout;
        }
        return defaultRequestTimeout;
    }

    private Duration normalize(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return DEFAULT_REQUEST_TIMEOUT;
        }
        return timeout;
    }
}
