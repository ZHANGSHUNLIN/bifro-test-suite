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

package org.apache.bifromq.testsuite.worker.eventbus;

import static org.mockito.Mockito.verify;

import io.vertx.core.eventbus.EventBus;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.eventbus.EventBusAddresses;
import org.apache.bifromq.testsuite.pipeline.PipelineProgressEvent;
import org.apache.bifromq.testsuite.worker.pojo.TaskStateChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VertxTaskRuntimeEventBusTest {

    @Mock
    private EventBus eventBus;

    private VertxTaskRuntimeEventBus runtimeEventBus;

    @BeforeEach
    void setUp() {
        runtimeEventBus = new VertxTaskRuntimeEventBus(eventBus);
    }

    @Test
    void publishPipelineProgressShouldPublishToProgressAddress() {
        PipelineProgressEvent event = PipelineProgressEvent.builder()
            .taskId("task-a")
            .nodeId("node-a")
            .build();

        runtimeEventBus.publishPipelineProgress(event);

        verify(eventBus).publish(EventBusAddresses.PIPELINE_PROGRESS, event);
    }

    @Test
    void sendTaskStateChangedShouldSendToStateAddress() {
        TaskStateChangeEvent event = TaskStateChangeEvent.builder()
            .taskId("task-a")
            .nodeId("node-a")
            .fromStage(TaskStage.STARTING)
            .toStage(TaskStage.ONGOING)
            .triggerEvent(TaskEvent.ONGOING)
            .build();

        runtimeEventBus.sendTaskStateChanged(event);

        verify(eventBus).send(EventBusAddresses.TASK_STATE_CHANGE, event);
    }
}
