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
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.worker.RateLimiterType;
import org.apache.bifromq.testsuite.worker.type.ExecutionPlan;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpecMapper;
import org.apache.bifromq.testsuite.worker.type.impl.PubSubStandardTaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PubsubStandardWorkerCharacterizationTest {

    @Mock
    private TaskConfig taskConfig;

    private Vertx vertx;
    private PubSubStandardTaskType taskType;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        taskType = new PubSubStandardTaskType();
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    private void stubMinimalTaskConfig(TaskTemplate template) {
        when(taskConfig.getTaskId()).thenReturn("char-test-task");
        when(taskConfig.getNodeId()).thenReturn("char-test-node");
        when(taskConfig.getTemplate()).thenReturn(template);
        when(taskConfig.getTaskType()).thenReturn(TaskConfig.TaskType.PUBSUB);
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
    class StandardPubSub {

        @BeforeEach
        void stubConfig() {
            stubMinimalTaskConfig(TaskTemplate.PUBSUB_STANDARD);
        }

        @Test
        void characterize_standard_pipelineStageCount() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.stages()).hasSize(11);
        }

        @Test
        void characterize_standard_stageNamesInOrder() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);
            List<String> names = plan.stages().stream().map(s -> s.getName()).toList();

            assertThat(names).containsExactly(
                "InitPubClients",
                "InitSubClients",
                "StartConnClients-CONN_CLIENTS",
                "StartSubscribing",
                "WaitForStageTimeout",
                "StartPubSubClients",
                "Stress",
                "CleanupConn",
                "CleanupConn",
                "TaskFinishEvent",
                "ErrorHandling"
            );
        }

        @Test
        void characterize_standard_noStateTransitionStagePresent() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);
            List<String> names = plan.stages().stream().map(s -> s.getName()).toList();

            assertThat(names).noneMatch(n -> n.startsWith("StateTransition"));
        }

        @Test
        void characterize_standard_contextContainsBothClientMaps() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.executionContext().pubClients()).isNotNull();
            assertThat(plan.executionContext().subClients()).isNotNull();
            assertThat(plan.executionContext().pubClients())
                .isInstanceOf(java.util.concurrent.ConcurrentHashMap.class);
            assertThat(plan.executionContext().subClients())
                .isInstanceOf(java.util.concurrent.ConcurrentHashMap.class);
        }
    }

    @Nested
    class PubOnly {

        @BeforeEach
        void stubConfig() {
            stubMinimalTaskConfig(TaskTemplate.PUBSUB_PUB_ONLY);
        }

        @Test
        void characterize_pubOnly_hasSmallerPipelineThanStandard() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.stages().size()).isLessThan(12);
        }

        @Test
        void characterize_pubOnly_doesNotContainInitSubClients() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);
            List<String> names = plan.stages().stream().map(s -> s.getName()).toList();

            assertThat(names).doesNotContain("InitSubClients");
        }

        @Test
        void characterize_pubOnly_expectSubCountIsZero() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.executionContext().expectedSubCount()).isEqualTo(0);
        }

        @Test
        void characterize_pubOnly_expectPubCountEqualsTotalClientCount() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.executionContext().expectedPubCount()).isEqualTo(10);
        }
    }

    @Nested
    class SubOnly {

        @BeforeEach
        void stubConfig() {
            stubMinimalTaskConfig(TaskTemplate.PUBSUB_SUB_ONLY);
        }

        @Test
        void characterize_subOnly_hasSmallerPipelineThanStandard() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.stages().size()).isLessThan(12);
        }

        @Test
        void characterize_subOnly_doesNotContainInitPubClients() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);
            List<String> names = plan.stages().stream().map(s -> s.getName()).toList();

            assertThat(names).doesNotContain("InitPubClients");
        }

        @Test
        void characterize_subOnly_expectPubCountIsZero() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.executionContext().expectedPubCount()).isEqualTo(0);
        }

        @Test
        void characterize_subOnly_expectSubCountEqualsTotalClientCount() {
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);

            assertThat(plan.executionContext().expectedSubCount()).isEqualTo(10);
        }
    }

    @Nested
    class WaitForStageTimeout {

        @Test
        void characterize_standard_containsWaitForStageTimeoutStage() {
            stubMinimalTaskConfig(TaskTemplate.PUBSUB_STANDARD);
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);
            List<String> names = plan.stages().stream().map(s -> s.getName()).toList();

            assertThat(names).contains("WaitForStageTimeout");
        }

        @Test
        void characterize_standard_waitForStageTimeoutIsAfterInitStages() {
            stubMinimalTaskConfig(TaskTemplate.PUBSUB_STANDARD);
            ExecutionPlan plan = taskType.buildPlan(
                WorkerPlanSpecMapper.fromTaskConfig(taskConfig), vertx);
            List<String> names = plan.stages().stream().map(s -> s.getName()).toList();

            int waitIdx = names.indexOf("WaitForStageTimeout");
            int initPubIdx = names.indexOf("InitPubClients");
            int initSubIdx = names.indexOf("InitSubClients");
            int startSubscribeIdx = names.indexOf("StartSubscribing");

            assertThat(waitIdx).isGreaterThan(initPubIdx);
            assertThat(waitIdx).isGreaterThan(initSubIdx);
            assertThat(waitIdx).isGreaterThan(startSubscribeIdx);
        }
    }
}
