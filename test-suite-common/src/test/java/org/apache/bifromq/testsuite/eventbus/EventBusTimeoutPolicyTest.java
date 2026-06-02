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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class EventBusTimeoutPolicyTest {

    @Test
    void timeoutFor_shouldUseFiveSecondsForRequestReplyFlows() {
        EventBusTimeoutPolicy policy = new EventBusTimeoutPolicy();

        assertThat(policy.timeoutFor(EventBusRequestKind.NODE_METRICS)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.timeoutFor(EventBusRequestKind.CLIENT_QUERY)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.timeoutFor(EventBusRequestKind.LOCAL_PORT_CAPACITY)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy.timeoutFor(EventBusRequestKind.TASK_COMMAND)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void timeoutFor_givenCustomTimeouts_shouldUseTaskCommandOverride() {
        EventBusTimeoutPolicy policy = new EventBusTimeoutPolicy(Duration.ofSeconds(3), Duration.ofSeconds(7));

        assertThat(policy.timeoutFor(EventBusRequestKind.NODE_METRICS)).isEqualTo(Duration.ofSeconds(3));
        assertThat(policy.timeoutFor(EventBusRequestKind.TASK_COMMAND)).isEqualTo(Duration.ofSeconds(7));
    }
}
