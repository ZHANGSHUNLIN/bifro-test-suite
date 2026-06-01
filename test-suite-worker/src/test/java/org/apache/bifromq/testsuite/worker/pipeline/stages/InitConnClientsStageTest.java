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
import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class InitConnClientsStageTest {

    @Mock
    private TaskConfig taskConfig;

    private Vertx vertx;
    private StateMachine<TaskStage, TaskEvent> stateMachine;
    private ConcurrentHashMap<String, MqttClientTask> connClients;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        stateMachine = new StateMachine<>(TaskStage.INIT);
        connClients = new ConcurrentHashMap<>();

        when(taskConfig.getTotalClientCount()).thenReturn(5);
        when(taskConfig.getThingIdStartAt()).thenReturn(0);
        when(taskConfig.getTaskId()).thenReturn("test-task");
        when(taskConfig.getPublishRate()).thenReturn(1.0);
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
            5, 5, 30, 0, 0, 1, 0,
            rateLimiter, rateLimiter, QpsStrategy.fixed(100), QpsStrategy.fixed(100),
            rateLimiter, QpsStrategy.fixed(Integer.MAX_VALUE),
            QpsStrategy.fixed(100),
            null, 5, 0, 5, 0,
            connClients, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
            Optional.empty());
        return new TaskPipelineContext(vertx, stateMachine, ec, result -> {
        });
    }

    @Nested
    class ConstructorTests {

        @Test
        void testConstructor_withValidParameters_shouldCreateStage() {
            InitConnClientsStage stage = new InitConnClientsStage();
            assertThat(stage.getName()).isEqualTo("InitConnClients");
        }

        @Test
        void testConstructor_withDifferentClientsMap_shouldCreateStage() {

            InitConnClientsStage stage = new InitConnClientsStage();
            assertThat(stage.getName()).isEqualTo("InitConnClients");
        }
    }

    @Nested
    class GetNameTests {

        @Test
        void testGetName_shouldReturnCorrectName() {

            InitConnClientsStage stage = new InitConnClientsStage();
            String name = stage.getName();
            assertThat(name).isEqualTo("InitConnClients");
        }
    }

    @Nested
    class OnBeforeTests {

        @Test
        void testOnBefore_shouldNotThrow() {

            InitConnClientsStage stage = new InitConnClientsStage();
            TaskPipelineContext context = createTestContext();
            stage.onBefore(context);
            assertThat(context.isCancelled()).isFalse();
        }
    }

    @Nested
    class OnAfterTests {

        @Test
        void testOnAfter_shouldNotThrow() throws Exception {

            InitConnClientsStage stage = new InitConnClientsStage();
            StageResult successResult = StageResult.success("All clients initialized");
            TaskPipelineContext context = createTestContext();
            stage.onAfter(context, successResult);
            assertThat(context).isNotNull();
        }
    }

    @Nested
    class OnErrorTests {

        @Test
        void testOnError_shouldNotThrow() {

            InitConnClientsStage stage = new InitConnClientsStage();
            RuntimeException error = new RuntimeException("Test error");
            TaskPipelineContext context = createTestContext();
            stage.onError(context, error);
            assertThat(context).isNotNull();
        }
    }
}
