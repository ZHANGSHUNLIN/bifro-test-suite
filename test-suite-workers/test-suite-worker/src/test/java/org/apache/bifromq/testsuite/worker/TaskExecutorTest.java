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

package org.apache.bifromq.testsuite.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.pipeline.TaskPipeline;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;
import org.apache.bifromq.testsuite.worker.type.ExecutionPlan;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpecMapper;
import org.apache.bifromq.testsuite.worker.type.impl.PubSubStandardTaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class TaskExecutorTest {

    private static final String TASK_ID = "exec-test-task";
    private static final String NODE_ID = "exec-test-node";
    @Mock
    private TaskConfig taskConfig;
    private Vertx vertx;
    private PubSubStandardTaskType taskType;
    private BaseTaskWorker worker;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        registry = new SimpleMeterRegistry();
        MetricsHelper.init(registry);
        lenient().when(taskConfig.getTaskId()).thenReturn(TASK_ID);
        lenient().when(taskConfig.getNodeId()).thenReturn(NODE_ID);
        lenient().when(taskConfig.getTemplate()).thenReturn(TaskTemplate.PUBSUB_STANDARD);
        lenient().when(taskConfig.getFanIn()).thenReturn(1);
        lenient().when(taskConfig.getFanOut()).thenReturn(1);
        lenient().when(taskConfig.getTotalClientCount()).thenReturn(2);
        lenient().when(taskConfig.getBrokers()).thenReturn(
            List.of(TaskBroker.builder().host("127.0.0.1").port(1884).build()));
        lenient().when(taskConfig.getQos()).thenReturn(MqttQoS.AT_MOST_ONCE);
        lenient().when(taskConfig.getMessageSize()).thenReturn(128);
        lenient().when(taskConfig.getPublishRate()).thenReturn(5.0);
        lenient().when(taskConfig.getStressDurationInSec()).thenReturn(30);
        lenient().when(taskConfig.getStageTimeoutInSec()).thenReturn(20);
        lenient().when(taskConfig.getReconnectMaxAttempts()).thenReturn(3);
        lenient().when(taskConfig.getReconnectIntervalInMs()).thenReturn(1000);
        lenient().when(taskConfig.getConnectTimeoutInMs()).thenReturn(5000);
        lenient().when(taskConfig.getKeepAliveInSec()).thenReturn(60);
        lenient().when(taskConfig.getAckTimeoutInSec()).thenReturn(5);
        lenient().when(taskConfig.getMaxInflightQueue()).thenReturn(100);
        lenient().when(taskConfig.getExpiryIntervalInSec()).thenReturn(0L);
        lenient().when(taskConfig.isEnableAutoMultiAddress()).thenReturn(false);
        lenient().when(taskConfig.getProtocol()).thenReturn("mqtt");
        lenient().when(taskConfig.isCleanSession()).thenReturn(true);
        lenient().when(taskConfig.isEmptyClientId()).thenReturn(false);
        lenient().when(taskConfig.getConnectRate()).thenReturn(500);
        lenient().when(taskConfig.getDisconnectRate()).thenReturn(500);
        lenient().when(taskConfig.getRateLimiterType())
            .thenReturn(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        lenient().when(taskConfig.getTaskType())
            .thenReturn(org.apache.bifromq.testsuite.worker.TaskConfig.TaskType.PUBSUB);

        taskType = new PubSubStandardTaskType();
        ExecutionPlan plan = taskType.buildPlan(
            WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);
        worker = new GenericTaskWorker(vertx, plan);
    }

    @AfterEach
    void tearDown() {
        vertx.close();
        MetricsHelper.init(new SimpleMeterRegistry());
    }

    @Test
    void execute_allClientsConnected_stateBecomesOngoing() {

        assertThat(worker.getTaskState())
            .as("SPEC-LC-05: Worker starts from ASSIGNED after node allocation")
            .isEqualTo(TaskStage.ASSIGNED);
        var sm = worker.getStateMachineForTest();
        sm.transition(TaskEvent.START_TASK, Map.of("taskId", TASK_ID));
        assertThat(worker.getTaskState())
            .as("SPEC-LC-05: state should be STARTING after START_TASK")
            .isEqualTo(TaskStage.STARTING);
        sm.transition(TaskEvent.ONGOING, Map.of());
        assertThat(worker.getTaskState())
            .as("SPEC-LC-05: state should be ONGOING after ONGOING event triggered in STARTING state")
            .isEqualTo(TaskStage.ONGOING);
    }

    @Test
    void stop_duringOngoing_stateBecomesShuttingThenStopped() throws Exception {

        var sm = worker.getStateMachineForTest();
        sm.transition(TaskEvent.START_TASK, Map.of("taskId", TASK_ID));
        transitionToOngoing(sm);
        assertThat(worker.getTaskState()).isEqualTo(TaskStage.ONGOING);
        recordTaskGauges();
        CompletableFuture<Void> cancelFuture = new CompletableFuture<>();
        worker.pipeline = TaskPipeline.<TaskPipelineContext>builder()
            .addStage(new ControlledCancelStage(cancelFuture))
            .build();

        CompletableFuture<Void> future = worker.stopTask();
        assertThat(worker.getTaskState())
            .as("SPEC-LC-06: state should stay SHUTTING while cancellation is still running")
            .isEqualTo(TaskStage.SHUTTING);
        assertThat(future).isNotDone();

        cancelFuture.complete(null);
        future.get(5, TimeUnit.SECONDS);
        assertThat(worker.getTaskState())
            .as("SPEC-LC-06: final state should be STOPPED after stopTask")
            .isEqualTo(TaskStage.STOPPED);
    }

    @Test
    void execute_durationElapsed_stateBecomesShutdown() {

        var sm = worker.getStateMachineForTest();
        sm.transition(TaskEvent.START_TASK, Map.of("taskId", TASK_ID));
        transitionToOngoing(sm);
        recordTaskGauges();
        sm.transition(TaskEvent.SHUTTING, Map.of());
        assertThat(worker.getTaskState())
            .as("SPEC-LC-07: state should be SHUTTING after SHUTTING triggered in ONGOING")
            .isEqualTo(TaskStage.SHUTTING);

        sm.transition(TaskEvent.SHUTDOWN, Map.of());
        assertThat(worker.getTaskState())
            .as("SPEC-LC-07: state should be SHUTDOWN after SHUTTING completes")
            .isEqualTo(TaskStage.SHUTDOWN);
        assertTaskGaugesRetained();
    }

    @Test
    void stop_duringShutting_isIdempotent() {

        var sm = worker.getStateMachineForTest();
        sm.transition(TaskEvent.START_TASK, Map.of("taskId", TASK_ID));
        transitionToOngoing(sm);
        sm.transition(TaskEvent.SHUTTING, Map.of());
        assertThat(worker.getTaskState()).isEqualTo(TaskStage.SHUTTING);
        CompletableFuture<Void> future = worker.stopTask();
        assertThat(future).isNotNull();
        assertThat(worker.getTaskState())
            .as("SPEC-LC-08: state should be SHUTTING or STOPPED when stop called again during SHUTTING (no exception)")
            .isIn(TaskStage.SHUTTING, TaskStage.STOPPED);
    }

    @Test
    void interrupt_shuttingStuck_forcesStoppedState() {

        var sm = worker.getStateMachineForTest();
        sm.transition(TaskEvent.START_TASK, Map.of("taskId", TASK_ID));
        transitionToOngoing(sm);
        sm.transition(TaskEvent.SHUTTING, Map.of());
        worker.interrupt();
        assertThat(worker.getTaskState())
            .as("SPEC-LC-09: state should be STOPPED after interrupt()")
            .isEqualTo(TaskStage.STOPPED);
    }

    @Test
    void execute_brokerRefused_stateBecomesFailedWithMessage() {

        var sm = worker.getStateMachineForTest();
        sm.transition(TaskEvent.START_TASK, Map.of("taskId", TASK_ID));
        assertThat(worker.getTaskState()).isEqualTo(TaskStage.STARTING);
        sm.transition(TaskEvent.FAILURE, Map.of("error", "Connection refused: 127.0.0.1:1884"));
        assertThat(worker.getTaskState())
            .as("SPEC-LC-10: state should be FAILED after connection failure")
            .isEqualTo(TaskStage.FAILED);
        assertThat(worker.terminalFuture()).isCompletedWithValue(TaskStage.FAILED);
    }

    @Test
    void execute_timeoutExceeded_stateBecomesTimeout() {

        var sm = worker.getStateMachineForTest();
        sm.transition(TaskEvent.START_TASK, Map.of("taskId", TASK_ID));
        transitionToOngoing(sm);
        sm.transition(TaskEvent.TIMEOUT, Map.of());
        assertThat(worker.getTaskState())
            .as("SPEC-LC-11: state should be TIMEOUT after timeout")
            .isEqualTo(TaskStage.TIMEOUT);
        assertThat(worker.terminalFuture()).isCompletedWithValue(TaskStage.TIMEOUT);
    }

    private void transitionToOngoing(org.apache.bifromq.testsuite.statemachine.StateMachine<TaskStage, TaskEvent> sm) {
        sm.transition(TaskEvent.ONGOING, Map.of());
    }

    private void recordTaskGauges() {
        MetricsHelper.gauge(BifroTaskMetric.CLIENT_PLANNED_GAUGE, 2,
            "taskId", TASK_ID, "nodeId", NODE_ID, "clientType", "conn");
        MetricsHelper.gauge(BifroTaskMetric.CLIENT_READY_GAUGE, 2,
            "taskId", TASK_ID, "nodeId", NODE_ID, "clientType", "conn");
        MetricsHelper.gauge(BifroTaskMetric.CLIENT_ACTIVE_GAUGE, 1,
            "taskId", TASK_ID, "nodeId", NODE_ID, "clientType", "conn");
    }

    private void assertTaskGaugesRetained() {
        assertThat(registry.find(BifroTaskMetric.CLIENT_PLANNED_GAUGE.getName())
            .tag("taskId", TASK_ID)
            .gauge()).isNotNull();
        assertThat(registry.find(BifroTaskMetric.CLIENT_READY_GAUGE.getName())
            .tag("taskId", TASK_ID)
            .gauge()).isNotNull();
        assertThat(registry.find(BifroTaskMetric.CLIENT_ACTIVE_GAUGE.getName())
            .tag("taskId", TASK_ID)
            .gauge()).isNotNull();
    }

    private static final class ControlledCancelStage implements PipelineStage<TaskPipelineContext> {
        private final CompletableFuture<Void> cancelFuture;

        private ControlledCancelStage(CompletableFuture<Void> cancelFuture) {
            this.cancelFuture = cancelFuture;
        }

        @Override
        public String getName() {
            return "controlled-cancel";
        }

        @Override
        public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
            return CompletableFuture.completedFuture(StageResult.success());
        }

        @Override
        public CompletableFuture<Void> cancel(TaskPipelineContext context) {
            return cancelFuture;
        }
    }
}
