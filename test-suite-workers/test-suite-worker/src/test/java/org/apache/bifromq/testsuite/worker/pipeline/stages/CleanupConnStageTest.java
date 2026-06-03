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
import org.apache.bifromq.testsuite.ConnMqttClientTask;
import org.apache.bifromq.testsuite.Constants;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.TaskEvent;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CleanupConnStageTest {

    private Vertx vertx;
    private StateMachine<TaskStage, TaskEvent> stateMachine;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        stateMachine = new StateMachine<>(TaskStage.INIT);
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    private TaskPipelineContext createTestContext() {
        return createTestContextWithClients(new ConcurrentHashMap<>());
    }

    private TaskPipelineContext createTestContextWithClients(ConcurrentHashMap<String, MqttClientTask> connClients) {
        return createTestContextWithClients(connClients, null);
    }

    private TaskPipelineContext createTestContextWithClients(ConcurrentHashMap<String, MqttClientTask> connClients,
                                                             TaskConfig taskConfig) {
        GuavaRateLimiter rateLimiter = new GuavaRateLimiter(1000);
        TaskConfig effectiveTaskConfig = taskConfig != null ? taskConfig : new TaskConfig();
        TaskExecutionContext ec = new TaskExecutionContext(
            "test-task", WorkerPlanSpecMapper.buildExecutionConfig(effectiveTaskConfig),
            10, 5, 30, 0, 0, 1, 0,
            rateLimiter, rateLimiter, QpsStrategy.fixed(1000), QpsStrategy.fixed(1000),
            rateLimiter, QpsStrategy.fixed(Integer.MAX_VALUE),
            QpsStrategy.fixed(1000),
            null, 0, 0, 0, 0,
            connClients, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
            Optional.empty());
        return new TaskPipelineContext(vertx, stateMachine, ec, result -> {
        });
    }

    @Nested
    class ConstructorTests {

        @Test
        void testConstructor_withValidParameters_shouldCreateStage() {

            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);
            assertThat(stage.getName()).isEqualTo("CleanupConn");
        }
    }

    @Nested
    class GetNameTests {

        @Test
        void testGetName_shouldReturnCorrectName() {

            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);
            String name = stage.getName();
            assertThat(name).isEqualTo("CleanupConn");
        }
    }

    @Nested
    class OnBeforeTests {

        @Test
        void testOnBefore_shouldNotThrow() {

            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            stage.onBefore(context);
        }
    }

    @Nested
    class OnAfterTests {

        @Test
        void testOnAfter_shouldNotThrow() {

            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            stage.onAfter(context, StageResult.success("Cleanup completed"));
        }
    }

    @Nested
    class OnErrorTests {

        @Test
        void testOnError_shouldNotThrow() {

            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            stage.onError(context, new RuntimeException("Test error"));
        }
    }

    @Nested
    class CanExecuteTests {

        @Test
        void testCanExecute_withActiveContext_shouldReturnTrue() {

            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            assertThat(stage.canExecute(context)).isTrue();
        }

        @Test
        void testCanExecute_withCancelledContext_shouldReturnFalse() {

            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            context.cancel();
            assertThat(stage.canExecute(context)).isFalse();
        }
    }

    @Nested
    class CancelTests {

        @Test
        void testCancel_withNoClients_shouldReturnCompletedFuture() {
            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();

            CompletableFuture<Void> cancelResult = stage.cancel(context);

            assertThat(cancelResult).isNotNull();
            assertThat(cancelResult.isDone()).isTrue();
        }

        @Test
        void testCancel_givenCloseFutureStuck_shouldWaitForBoundedCleanup() throws Exception {
            ConcurrentHashMap<String, MqttClientTask> clients = new ConcurrentHashMap<>();
            MqttClientTask client = mock(ConnMqttClientTask.class);
            when(client.close()).thenReturn(new CompletableFuture<>());
            when(client.getCId()).thenReturn("client1");
            clients.put("client1", client);
            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG, 20);
            TaskPipelineContext context = createTestContextWithClients(clients);

            CompletableFuture<Void> cancelResult = stage.cancel(context);

            cancelResult.get(1, TimeUnit.SECONDS);
            assertThat(clients).isEmpty();
        }
    }

    @Nested
    class ExecuteTests {

        @Test
        void testExecute_withClients_shouldClearClientsMap() throws Exception {

            ConcurrentHashMap<String, MqttClientTask> clients = new ConcurrentHashMap<>();
            clients.put("client1", mock(ConnMqttClientTask.class));
            clients.put("client2", mock(ConnMqttClientTask.class));
            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContextWithClients(clients);
            StageResult result = stage.execute(context).get();
            assertThat(result.isSuccess()).isTrue();
            assertThat(clients).isEmpty();
        }

        @Test
        void testExecute_withDisconnectProfileEndingAtZero_shouldClearClientsMap() throws Exception {
            TaskConfig taskConfig = new TaskConfig();
            taskConfig.setTaskId("test-task");
            taskConfig.setNodeId("node");
            taskConfig.setDisconnectProfileDataPoints(List.of(
                new long[] {0, 2_000},
                new long[] {1_000, 2_000},
                new long[] {1_001, 0}
            ));
            ConcurrentHashMap<String, MqttClientTask> clients = new ConcurrentHashMap<>();
            MqttClientTask client1 = mock(ConnMqttClientTask.class);
            MqttClientTask client2 = mock(ConnMqttClientTask.class);
            clients.put("client1", client1);
            clients.put("client2", client2);
            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContextWithClients(clients, taskConfig);

            StageResult result = stage.execute(context).get(2, TimeUnit.SECONDS);

            assertThat(result.isSuccess()).isTrue();
            assertThat(clients).isEmpty();
        }

        @Test
        void testExecute_withEmptyClients_shouldReturnSuccess() throws Exception {

            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            StageResult result = stage.execute(context).get();
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void testExecute_givenCloseFutureStuck_shouldCompleteAfterBoundedWait() throws Exception {
            ConcurrentHashMap<String, MqttClientTask> clients = new ConcurrentHashMap<>();
            MqttClientTask client = mock(ConnMqttClientTask.class);
            when(client.close()).thenReturn(new CompletableFuture<>());
            when(client.getCId()).thenReturn("client1");
            clients.put("client1", client);
            CleanupConnStage stage = new CleanupConnStage(Constants.CONN_CLIENT_TAG, 20);
            TaskPipelineContext context = createTestContextWithClients(clients);

            StageResult result = stage.execute(context).get(1, TimeUnit.SECONDS);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).contains("closeTimeout=1");
            assertThat(clients).isEmpty();
        }
    }
}
