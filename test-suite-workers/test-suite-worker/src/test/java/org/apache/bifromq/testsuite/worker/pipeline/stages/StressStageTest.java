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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.vertx.core.Vertx;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.PubMqttClientTask;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class StressStageTest {

    @Mock
    private TaskConfig taskConfig;

    private Vertx vertx;
    private StateMachine<TaskStage, TaskEvent> stateMachine;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        stateMachine = new StateMachine<>(TaskStage.INIT);

        Mockito.when(taskConfig.getStressDurationInSec()).thenReturn(1);
        Mockito.when(taskConfig.getTaskId()).thenReturn("test-task");
        Mockito.when(taskConfig.getPublishRate()).thenReturn(1.0);
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    private TaskPipelineContext createTestContext() {
        GuavaRateLimiter rateLimiter = new GuavaRateLimiter(100);
        TaskExecutionContext ec = new TaskExecutionContext(
            "test-task", WorkerPlanSpecMapper.buildExecutionConfig(taskConfig),
            10, 1, 30, 0, 0, 1, 0,
            rateLimiter, rateLimiter, QpsStrategy.fixed(100), QpsStrategy.fixed(100),
            rateLimiter, QpsStrategy.fixed(Integer.MAX_VALUE),
            QpsStrategy.fixed(100),
            null, 10, 0, 10, 0,
            new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
            Optional.empty());
        return new TaskPipelineContext(vertx, stateMachine, ec, result -> {
        });
    }

    @Nested
    class ConstructorTests {

        @Test
        void testConstructor_withValidParameters_shouldCreateStage() {
            StressStage stage = new StressStage(vertx);
            assertThat(stage).isNotNull();
            assertThat(stage.getName()).isEqualTo("Stress");
        }

        @Test
        void testConstructor_withNullVertx_shouldNotThrow() {
            StressStage stage = new StressStage(vertx);
            assertThat(stage.getName()).isEqualTo("Stress");
        }
    }

    @Nested
    class GetNameTests {

        @Test
        void testGetName_shouldReturnCorrectName() {

            StressStage stage = new StressStage(vertx);
            String name = stage.getName();
            assertThat(name).isEqualTo("Stress");
        }
    }

    @Nested
    class ExecuteTests {

        @Test
        void testExecute_shouldWaitForStressDuration() throws Exception {

            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createTestContext();
            CompletableFuture<StageResult> result = stage.execute(context);
            assertThat(result.get().isSuccess()).isTrue();
        }

        @Test
        void testExecute_shouldReturnSuccessResult() throws Exception {

            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createTestContext();
            StageResult result = stage.execute(context).get();
            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    class OnBeforeTests {

        @Test
        void testOnBefore_shouldNotThrow() {

            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createTestContext();
            stage.onBefore(context);
            assertThat(context.isCancelled()).isFalse();
        }
    }

    @Nested
    class OnAfterTests {

        @Test
        void testOnAfter_shouldNotThrow() throws Exception {

            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createTestContext();
            stage.execute(context).get();
            stage.onAfter(context, StageResult.success());
            assertThat(context).isNotNull();
        }
    }

    @Nested
    class OnErrorTests {

        @Test
        void testOnError_shouldNotThrow() {

            StressStage stage = new StressStage(vertx);
            RuntimeException error = new RuntimeException("Test error");
            TaskPipelineContext context = createTestContext();
            stage.onError(context, error);
            assertThat(context).isNotNull();
        }
    }

    @Nested
    class CanExecuteTests {

        @Test
        void testCanExecute_withDefaultImplementation_shouldUseContextCancelled() {

            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createTestContext();
            boolean canExecute = stage.canExecute(context);
            assertThat(canExecute).isTrue();
        }

        @Test
        void testCanExecute_withCancelledContext_shouldReturnFalse() {

            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createTestContext();
            context.cancel();
            boolean canExecute = stage.canExecute(context);
            assertThat(canExecute).isFalse();
        }
    }

    @Nested
    class CancelTests {

        @Test
        void testCancel_shouldCompleteStageFuture() throws Exception {

            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createTestContext();
            CompletableFuture<Void> cancelResult = stage.cancel(context);
            assertThat(cancelResult).isNotNull();
            assertThat(cancelResult.isDone()).isTrue();
        }
    }

    @Nested
    class StopPubClientsTests {

        private TaskPipelineContext createContextWithPubClients(ConcurrentHashMap<String, MqttClientTask> pubClients) {
            GuavaRateLimiter rateLimiter = new GuavaRateLimiter(100);
            TaskExecutionContext ec = new TaskExecutionContext(
                "test-task", WorkerPlanSpecMapper.buildExecutionConfig(taskConfig),
                10, 1, 30, 0, 0, 1, 0,
                rateLimiter, rateLimiter, QpsStrategy.fixed(100), QpsStrategy.fixed(100),
                rateLimiter, QpsStrategy.fixed(Integer.MAX_VALUE),
                QpsStrategy.fixed(100),
                null, 10, 0, 10, 0,
                new ConcurrentHashMap<>(), pubClients, new ConcurrentHashMap<>(),
                Optional.empty());
            return new TaskPipelineContext(vertx, stateMachine, ec, result -> {
            });
        }

        @Test
        void testOnAfter_withPubClients_shouldStopPublishing() throws Exception {

            PubMqttClientTask pubClient = mock(PubMqttClientTask.class);
            ConcurrentHashMap<String, MqttClientTask> pubClients = new ConcurrentHashMap<>();
            pubClients.put("pub-0", pubClient);
            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createContextWithPubClients(pubClients);
            stage.execute(context).get();
            stage.onAfter(context, StageResult.success());
            verify(pubClient).stopPublishing();
        }

        @Test
        void testOnAfter_withEmptyPubClients_shouldNotThrow() throws Exception {

            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createContextWithPubClients(new ConcurrentHashMap<>());
            stage.execute(context).get();
            stage.onAfter(context, StageResult.success());
        }

        @Test
        void testOnError_withPubClients_shouldStopPublishing() {

            PubMqttClientTask pubClient = mock(PubMqttClientTask.class);
            ConcurrentHashMap<String, MqttClientTask> pubClients = new ConcurrentHashMap<>();
            pubClients.put("pub-0", pubClient);
            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createContextWithPubClients(pubClients);
            stage.onError(context, new RuntimeException("stress error"));
            verify(pubClient).stopPublishing();
        }

        @Test
        void testCancel_withPubClients_shouldStopPublishing() throws Exception {

            PubMqttClientTask pubClient = mock(PubMqttClientTask.class);
            ConcurrentHashMap<String, MqttClientTask> pubClients = new ConcurrentHashMap<>();
            pubClients.put("pub-0", pubClient);
            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createContextWithPubClients(pubClients);
            stage.cancel(context).get();
            verify(pubClient).stopPublishing();
        }

        @Test
        void testOnAfter_withNonPubClientTask_shouldNotCallStopPublishing() throws Exception {

            MqttClientTask otherClient = mock(MqttClientTask.class);
            ConcurrentHashMap<String, MqttClientTask> pubClients = new ConcurrentHashMap<>();
            pubClients.put("conn-0", otherClient);
            StressStage stage = new StressStage(vertx);
            TaskPipelineContext context = createContextWithPubClients(pubClients);
            stage.execute(context).get();
            stage.onAfter(context, StageResult.success());
            verify(otherClient, never()).close();
        }
    }
}
