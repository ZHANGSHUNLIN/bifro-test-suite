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

import org.junit.jupiter.api.Test;

class EventBusAddressesTest {

    @Test
    void addresses_shouldMatchClusterContracts() {
        assertThat(EventBusAddresses.CLUSTER_TASK_COMMAND).isEqualTo("cluster.task.message");
        assertThat(EventBusAddresses.TASK_STATE_CHANGE).isEqualTo("task.state.change");
        assertThat(EventBusAddresses.PIPELINE_PROGRESS).isEqualTo("task.pipeline.progress");
        assertThat(EventBusAddresses.nodeMetrics("node-a")).isEqualTo("node.node-a.metrics");
        assertThat(EventBusAddresses.nodeClients("node-a")).isEqualTo("node.node-a.clients");
        assertThat(EventBusAddresses.localPortCapacity("node-a")).isEqualTo("node.node-a.local-port-capacity");
    }
}
