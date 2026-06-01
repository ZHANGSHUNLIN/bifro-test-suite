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

package org.apache.bifromq.testsuite.app.config.vertx.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.bifromq.testsuite.TaskSchedule;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.apache.bifromq.testsuite.metric.NodeMetricsRequest;
import org.apache.bifromq.testsuite.metric.NodeMetricsResponse;
import org.apache.bifromq.testsuite.pipeline.PipelineProgressEvent;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskCommand;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryRequest;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryResponse;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckRequest;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckResponse;
import org.apache.bifromq.testsuite.worker.pojo.TaskStateChangeEvent;
import org.junit.jupiter.api.Test;

class EventBusCodecRegistryTest {

    @Test
    void codecTypesShouldOnlyContainTopLevelEventBusPayloads() {
        Set<Class<?>> actualClasses = Arrays.stream(CodecType.values())
            .map(CodecType::getMessageClass)
            .collect(Collectors.toSet());

        assertThat(actualClasses).containsExactlyInAnyOrder(
            TaskSchedule.class,
            NodeMetricsRequest.class,
            NodeMetricsResponse.class,
            TaskStateChangeEvent.class,
            ClientQueryRequest.class,
            ClientQueryResponse.class,
            PipelineProgressEvent.class,
            LocalPortCapacityCheckRequest.class,
            LocalPortCapacityCheckResponse.class);
    }

    @Test
    void codecRegistryShouldNotContainHazelcastOnlyPayloads() {
        assertThat(EventBusCodecRegistry.findByClass(NodeInfo.class)).isNull();
        assertThat(EventBusCodecRegistry.findByClass(TaskConfig.class)).isNull();
        assertThat(EventBusCodecRegistry.findByClass(WorkerTaskCommand.class)).isNull();
    }
}
