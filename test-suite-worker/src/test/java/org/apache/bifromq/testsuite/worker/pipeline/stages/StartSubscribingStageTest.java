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

package org.apache.bifromq.testsuite.worker.pipeline.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.SubMqttClientTask;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.statemachine.StateMachine;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionContext;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;
import org.apache.bifromq.testsuite.worker.ratelimit.GuavaRateLimiter;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpecMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartSubscribingStageTest {

    private Vertx vertx;
    private TaskConfig taskConfig;
    private ConcurrentHashMap<String, MqttClientTask> subClients;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        taskConfig = new TaskConfig();
        taskConfig.setTaskId("task");
        taskConfig.setNodeId("node");
        subClients = new ConcurrentHashMap<>();
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    void execute_waitsForSubscriberAckBeforeCompleting() throws Exception {
        SubMqttClientTask subClient = mock(SubMqttClientTask.class);
        CompletableFuture<List<Integer>> subscribeFuture = new CompletableFuture<>();
        when(subClient.subscribe()).thenReturn(subscribeFuture);
        when(subClient.getCId()).thenReturn("sub-1");
        subClients.put("sub-1", subClient);
        StartSubscribingStage stage = new StartSubscribingStage();

        CompletableFuture<StageResult> result = stage.execute(context());

        assertThat(result.isDone()).isFalse();
        subscribeFuture.complete(List.of(0));

        assertThat(result.get(1, TimeUnit.SECONDS).isSuccess()).isTrue();
    }

    @Test
    void execute_allFailedSubscribers_returnsFailure() throws Exception {
        SubMqttClientTask subClient = mock(SubMqttClientTask.class);
        when(subClient.subscribe()).thenReturn(CompletableFuture.failedFuture(new RuntimeException("failed")));
        when(subClient.getCId()).thenReturn("sub-1");
        subClients.put("sub-1", subClient);
        StartSubscribingStage stage = new StartSubscribingStage();

        StageResult result = stage.execute(context()).get(1, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withSubscribeProfileEndingAtZero_shouldDispatchAllSubscribers() throws Exception {
        taskConfig.setSubscribeProfileDataPoints(List.of(
            new long[] {0, 2_000},
            new long[] {1_000, 2_000},
            new long[] {1_001, 0}
        ));
        SubMqttClientTask subClient1 = mock(SubMqttClientTask.class);
        SubMqttClientTask subClient2 = mock(SubMqttClientTask.class);
        when(subClient1.subscribe()).thenReturn(CompletableFuture.completedFuture(List.of(0)));
        when(subClient2.subscribe()).thenReturn(CompletableFuture.completedFuture(List.of(0)));
        when(subClient1.getCId()).thenReturn("sub-1");
        when(subClient2.getCId()).thenReturn("sub-2");
        subClients.put("sub-1", subClient1);
        subClients.put("sub-2", subClient2);
        StartSubscribingStage stage = new StartSubscribingStage();

        StageResult result = stage.execute(context()).get(2, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("success=2");
    }

    private TaskPipelineContext context() {
        GuavaRateLimiter limiter = new GuavaRateLimiter(1000);
        TaskExecutionContext executionContext = new TaskExecutionContext(
            "task",
            WorkerPlanSpecMapper.buildExecutionConfig(taskConfig),
            1,
            60,
            30,
            0,
            0,
            1,
            1,
            limiter,
            limiter,
            QpsStrategy.fixed(1000),
            QpsStrategy.fixed(1000),
            limiter,
            QpsStrategy.fixed(1000),
            QpsStrategy.fixed(1000),
            null,
            0,
            1,
            0,
            1,
            new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>(),
            subClients,
            Optional.empty());
        return new TaskPipelineContext(vertx, new StateMachine<>(TaskStage.INIT), executionContext, result -> {
        });
    }
}
