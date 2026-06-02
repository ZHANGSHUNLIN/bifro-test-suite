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

package org.apache.bifromq.testsuite.worker.pipeline.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.PubMqttClientTask;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.statemachine.StateMachine;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskSpec;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionContext;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;
import org.apache.bifromq.testsuite.worker.pipeline.stages.StartPubSubClientsStage;
import org.apache.bifromq.testsuite.worker.ratelimit.GuavaRateLimiter;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpecMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StartPubSubClientsStageTest {

    private Vertx vertx;
    private ConcurrentHashMap<String, MqttClientTask> pubClients;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        pubClients = new ConcurrentHashMap<>();
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    void execute_withPlannedStartAtMs_usesPlannedTimeAsPublishSchedulerOrigin() throws Exception {
        long plannedStartAtMs = System.currentTimeMillis() + 60_000L;
        TaskConfig taskConfig = dynamicPublishConfig();
        PubMqttClientTask pubClient = pubClient("pub-1");
        pubClients.put("pub-1", pubClient);

        TaskPipelineContext context = context(WorkerTaskSpec.fromTaskConfig(taskConfig).toBuilder()
            .plannedStartAtMs(plannedStartAtMs)
            .build());
        StageResult result = new StartPubSubClientsStage(vertx).execute(context).get(2, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
        NodePublishScheduler scheduler = context.getNodePublishScheduler().get();
        assertThat(scheduler).isNotNull();
        assertThat(scheduler.timeOriginMs()).isEqualTo(plannedStartAtMs);
        verifyNoInteractions(pubClient);
        context.stopNodePublishScheduler();
    }

    @Test
    void execute_withoutPlannedStartAtMs_fallsBackToLocalStageStartTime() throws Exception {
        TaskConfig taskConfig = dynamicPublishConfig();
        PubMqttClientTask pubClient = pubClient("pub-1");
        pubClients.put("pub-1", pubClient);

        long beforeExecuteMs = System.currentTimeMillis();
        TaskPipelineContext context = context(taskConfig);
        StageResult result = new StartPubSubClientsStage(vertx).execute(context).get(2, TimeUnit.SECONDS);
        long afterExecuteMs = System.currentTimeMillis();

        assertThat(result.isSuccess()).isTrue();
        NodePublishScheduler scheduler = context.getNodePublishScheduler().get();
        assertThat(scheduler).isNotNull();
        assertThat(scheduler.timeOriginMs()).isBetween(beforeExecuteMs, afterExecuteMs);
        context.stopNodePublishScheduler();
    }

    @Test
    void execute_withFixedPublishQps_usesNodePublishScheduler() throws Exception {
        TaskConfig taskConfig = fixedPublishConfig();
        PubMqttClientTask pubClient = pubClient("pub-1");
        pubClients.put("pub-1", pubClient);

        TaskPipelineContext context = context(taskConfig);
        StageResult result = new StartPubSubClientsStage(vertx).execute(context).get(2, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
        assertThat(context.getNodePublishScheduler().get()).isNotNull();
        verify(pubClient, org.mockito.Mockito.never()).startPublishing();
        context.stopNodePublishScheduler();
    }

    private TaskConfig dynamicPublishConfig() {
        TaskConfig taskConfig = new TaskConfig();
        taskConfig.setTaskId("task");
        taskConfig.setNodeId("node");
        taskConfig.setQpsMode(TaskConfig.QpsMode.DYNAMIC);
        taskConfig.setPublishProfileDataPoints(List.of(new long[] {0L, 100L}, new long[] {1_000L, 100L}));
        return taskConfig;
    }

    private TaskConfig fixedPublishConfig() {
        TaskConfig taskConfig = new TaskConfig();
        taskConfig.setTaskId("task");
        taskConfig.setNodeId("node");
        taskConfig.setQpsMode(TaskConfig.QpsMode.FIXED);
        taskConfig.setPublishRate(100);
        return taskConfig;
    }

    private PubMqttClientTask pubClient(String clientId) {
        PubMqttClientTask pubClient = mock(PubMqttClientTask.class);
        when(pubClient.getCId()).thenReturn(clientId);
        when(pubClient.publishOnce(anyLong())).thenReturn(CompletableFuture.completedFuture(null));
        return pubClient;
    }

    private TaskPipelineContext context(TaskConfig taskConfig) {
        return context(WorkerTaskSpec.fromTaskConfig(taskConfig));
    }

    private TaskPipelineContext context(WorkerTaskSpec workerTaskSpec) {
        GuavaRateLimiter limiter = new GuavaRateLimiter(1000);
        TaskExecutionContext executionContext = new TaskExecutionContext(
            "task",
            WorkerPlanSpecMapper.buildExecutionConfig(workerTaskSpec),
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
            WorkerPlanSpecMapper.publishQpsStrategy(workerTaskSpec.toTaskConfig()),
            null,
            1,
            0,
            1,
            0,
            new ConcurrentHashMap<>(),
            pubClients,
            new ConcurrentHashMap<>(),
            Optional.empty());
        return new TaskPipelineContext(vertx, new StateMachine<>(TaskStage.INIT), executionContext, result -> {
        });
    }
}
