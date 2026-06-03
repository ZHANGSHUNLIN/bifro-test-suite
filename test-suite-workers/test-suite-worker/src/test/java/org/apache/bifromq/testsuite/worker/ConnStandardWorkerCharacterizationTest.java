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
import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import java.util.List;
import org.apache.bifromq.testsuite.Constants;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.worker.RateLimiterType;
import org.apache.bifromq.testsuite.worker.type.ExecutionPlan;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpecMapper;
import org.apache.bifromq.testsuite.worker.type.impl.ConnStandardTaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ConnStandardWorkerCharacterizationTest {

    @Mock
    private TaskConfig taskConfig;

    private Vertx vertx;
    private ConnStandardTaskType taskType;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        taskType = new ConnStandardTaskType();
        stubMinimalTaskConfig();
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    private void stubMinimalTaskConfig() {
        when(taskConfig.getTaskId()).thenReturn("char-test-task");
        when(taskConfig.getNodeId()).thenReturn("char-test-node");
        when(taskConfig.getTemplate()).thenReturn(TaskTemplate.CONN_STANDARD);
        when(taskConfig.getTaskType()).thenReturn(TaskConfig.TaskType.CONN);
        when(taskConfig.getTotalClientCount()).thenReturn(10);
        when(taskConfig.getConnectRate()).thenReturn(100);
        when(taskConfig.getDisconnectRate()).thenReturn(100);
        when(taskConfig.getPublishRate()).thenReturn(1.0);
        when(taskConfig.getStressDurationInSec()).thenReturn(5);
        when(taskConfig.getStageTimeoutInSec()).thenReturn(30);
        when(taskConfig.getDelayAfterStageInSec()).thenReturn(0);
        when(taskConfig.getThingIdStartAt()).thenReturn(0);
        when(taskConfig.getFanOut()).thenReturn(1);
        when(taskConfig.getFanIn()).thenReturn(0);
        when(taskConfig.getRateLimiterType()).thenReturn(RateLimiterType.GUAVA);
        when(taskConfig.getBrokers()).thenReturn(List.of());
        when(taskConfig.isEnableAutoMultiAddress()).thenReturn(false);
        when(taskConfig.getLocalAddresses()).thenReturn(List.of());

        when(taskConfig.getNodePubCount()).thenReturn(-1);
        when(taskConfig.getNodeSubCount()).thenReturn(-1);
    }

    @Nested
    class PipelineStructure {

        @Test
        void characterize_connStandard_pipelineHas6Stages() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);
            assertThat(plan.stages()).hasSize(6);
        }

        @Test
        void characterize_connStandard_firstStageIsInitConnClients() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.stages().get(0).getName()).isEqualTo("InitConnClients");
        }

        @Test
        void characterize_connStandard_stageNamesInOrder() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            List<String> names = plan.stages().stream()
                .map(s -> s.getName())
                .toList();

            assertThat(names).containsExactly(
                "InitConnClients",
                "StartConnClients-" + Constants.CONN_CLIENT_TAG,
                "Stress",
                "CleanupConn",
                "TaskFinishEvent",
                "ErrorHandling"
            );
        }

        @Test
        void characterize_connStandard_noStateTransitionStagePresent() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);
            List<String> names = plan.stages().stream().map(s -> s.getName()).toList();

            assertThat(names).noneMatch(n -> n.startsWith("StateTransition"));
        }
    }

    @Nested
    class StateMachineStructure {

        @Test
        void characterize_connStandard_initialStateIsInit() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.stateMachine().getCurrentState()).isEqualTo(TaskStage.ASSIGNED);
        }

        @Test
        void characterize_connStandard_stateMachineHasStartTaskTransition() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.stateMachine().canTransition(TaskEvent.START_TASK)).isTrue();
        }

        @Test
        void characterize_connStandard_stateMachineHasOngoingTransitionFromStarting() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);
            plan.stateMachine().transition(TaskEvent.START_TASK).join();

            assertThat(plan.stateMachine().canTransition(TaskEvent.ONGOING)).isTrue();
        }
    }

    @Nested
    class ExecutionContextFields {

        @Test
        void characterize_connStandard_contextContainsConnClientMap() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.executionContext().connClients()).isNotNull();
            assertThat(plan.executionContext().connClients())
                .isInstanceOf(java.util.concurrent.ConcurrentHashMap.class);
        }

        @Test
        void characterize_connStandard_contextContainsRateLimiters() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.executionContext().connectRateLimiter()).isNotNull();
            assertThat(plan.executionContext().disconnectRateLimiter()).isNotNull();
        }

        @Test
        void characterize_connStandard_contextContainsTotalClientCount() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.executionContext().totalClientCount()).isEqualTo(10);
        }

        @Test
        void characterize_connStandard_contextContainsExpectCounts() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.executionContext().expectedPubCount()).isGreaterThanOrEqualTo(0);
            assertThat(plan.executionContext().expectedSubCount()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void characterize_connStandard_contextHasNoChaosPolicy() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.executionContext().chaosPolicy()).isEmpty();
        }

        @Test
        void characterize_connStandard_contextHasFixedQpsStrategy() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.executionContext().connectQpsStrategy()).isNotNull();
            assertThat(plan.executionContext().connectQpsStrategy().currentQps(0)).isEqualTo(100);
        }
    }
}
