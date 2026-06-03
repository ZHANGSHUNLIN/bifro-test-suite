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

package org.apache.bifromq.testsuite.worker.type;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.qps.ProfileQpsSpec;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.junit.jupiter.api.Test;

class WorkerPlanSpecMapperTest {
    private TaskConfig config(TaskTemplate template, int total, int fanIn, int fanOut) {
        TaskConfig c = new TaskConfig();
        c.setTemplate(template);
        c.setTotalClientCount(total);
        c.setFanIn(fanIn);
        c.setFanOut(fanOut);

        return c;
    }

    private TaskConfig configWithCoordinatorCounts(int nodePubCount, int nodeSubCount) {
        TaskConfig c = new TaskConfig();
        c.setTemplate(TaskTemplate.PUBSUB_STANDARD);
        c.setTotalClientCount(4);
        c.setFanIn(2);
        c.setFanOut(2);
        c.setNodePubCount(nodePubCount);
        c.setNodeSubCount(nodeSubCount);
        return c;
    }

    private PubSubClientCountSpec countSpec(TaskConfig config) {
        return PubSubClientCountSpec.fromTaskConfig(config);
    }

    @Test
    void calculateExpectPubCount_pubOnlyTemplate_returnsTotal() {

        TaskConfig c = config(TaskTemplate.PUBSUB_PUB_ONLY, 10, 1, 1);

        assertThat(PubSubClientCountPlanner.expectedPubCount(countSpec(c))).isEqualTo(10);
    }

    @Test
    void calculateExpectSubCount_pubOnlyTemplate_returnsZero() {
        TaskConfig c = config(TaskTemplate.PUBSUB_PUB_ONLY, 10, 1, 1);
        assertThat(PubSubClientCountPlanner.expectedSubCount(countSpec(c))).isEqualTo(0);
    }

    @Test
    void calculateExpectSubCount_subOnlyTemplate_returnsTotal() {
        TaskConfig c = config(TaskTemplate.PUBSUB_SUB_ONLY, 10, 1, 1);
        assertThat(PubSubClientCountPlanner.expectedSubCount(countSpec(c))).isEqualTo(10);
    }

    @Test
    void calculateExpectPubCount_subOnlyTemplate_returnsZero() {
        TaskConfig c = config(TaskTemplate.PUBSUB_SUB_ONLY, 10, 1, 1);
        assertThat(PubSubClientCountPlanner.expectedPubCount(countSpec(c))).isEqualTo(0);
    }

    @Test
    void calculateExpectPubCount_pureFanOut_returnsOne() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 3, 1, 2);
        assertThat(PubSubClientCountPlanner.expectedPubCount(countSpec(c))).isEqualTo(1);
    }

    @Test
    void calculateExpectSubCount_pureFanOut_returnsFanOut() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 3, 1, 2);
        assertThat(PubSubClientCountPlanner.expectedSubCount(countSpec(c))).isEqualTo(2);
    }

    @Test
    void calculateExpectSubCount_pureFanOut3_returnsFanOut3() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 4, 1, 3);
        assertThat(PubSubClientCountPlanner.expectedSubCount(countSpec(c))).isEqualTo(3);
    }

    @Test
    void calculateExpectPubCount_pureFanIn_returnsFanIn() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 3, 2, 1);

        assertThat(PubSubClientCountPlanner.expectedPubCount(countSpec(c))).isEqualTo(2);
    }

    @Test
    void calculateExpectSubCount_pureFanIn_returnsOne() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 3, 2, 1);

        assertThat(PubSubClientCountPlanner.expectedSubCount(countSpec(c))).isEqualTo(1);
    }

    @Test
    void calculateExpectPubCount_pureFanIn3_returns3() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 4, 3, 1);

        assertThat(PubSubClientCountPlanner.expectedPubCount(countSpec(c))).isEqualTo(3);
    }

    @Test
    void calculateExpectPubCount_fiFoCross_returnsFanIn() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 4, 2, 2);
        assertThat(PubSubClientCountPlanner.expectedPubCount(countSpec(c))).isEqualTo(2);
    }

    @Test
    void calculateExpectSubCount_fiFoCross_returnsFanOut() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 4, 2, 2);
        assertThat(PubSubClientCountPlanner.expectedSubCount(countSpec(c))).isEqualTo(2);
    }

    @Test
    void calculateExpectPubCount_fiFoCross_fanIn3FanOut2_returns3() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 5, 3, 2);
        assertThat(PubSubClientCountPlanner.expectedPubCount(countSpec(c))).isEqualTo(3);
    }

    @Test
    void calculateExpectSubCount_fiFoCross_fanIn3FanOut2_returns2() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 5, 3, 2);
        assertThat(PubSubClientCountPlanner.expectedSubCount(countSpec(c))).isEqualTo(2);
    }

    @Test
    void calculateExpectPubCount_fiFoCross_fanIn2FanOut3_returns2() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 5, 2, 3);
        assertThat(PubSubClientCountPlanner.expectedPubCount(countSpec(c))).isEqualTo(2);
    }

    @Test
    void calculateExpectSubCount_fiFoCross_fanIn2FanOut3_returns3() {

        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 5, 2, 3);
        assertThat(PubSubClientCountPlanner.expectedSubCount(countSpec(c))).isEqualTo(3);
    }

    @Test
    void calculateExpectPubCount_coordinatorOverride_returnsNodePubCount() {

        TaskConfig c = configWithCoordinatorCounts(1, 1);
        assertThat(PubSubClientCountPlanner.expectedPubCount(countSpec(c))).isEqualTo(1);
    }

    @Test
    void calculateExpectSubCount_coordinatorOverride_returnsNodeSubCount() {

        TaskConfig c = configWithCoordinatorCounts(1, 1);
        assertThat(PubSubClientCountPlanner.expectedSubCount(countSpec(c))).isEqualTo(1);
    }

    @Test
    void calculateExpectPubCount_coordinatorOverrideZero_returnsZero() {

        TaskConfig c = configWithCoordinatorCounts(0, 2);
        assertThat(PubSubClientCountPlanner.expectedPubCount(countSpec(c))).isEqualTo(0);
    }

    @Test
    void calculateExpectSubCount_coordinatorOverrideZero_returnsZero() {

        TaskConfig c = configWithCoordinatorCounts(2, 0);
        assertThat(PubSubClientCountPlanner.expectedSubCount(countSpec(c))).isEqualTo(0);
    }

    @Test
    void publishQpsStrategy_withLoopProfile_restartsFromProfileBeginning() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setQpsMode(TaskConfig.QpsMode.DYNAMIC);
        c.setPublishProfileDataPoints(List.of(new long[] {0, 0}, new long[] {1_000, 100}, new long[] {2_000, 0}));
        c.setProfileConfig(TaskConfig.ProfileConfig.builder()
            .endBehavior(ProfileQpsSpec.EndBehavior.LOOP)
            .build());

        var strategy = WorkerPlanSpecMapper.publishQpsStrategy(c);

        assertThat(strategy.currentQps(2_500)).isEqualTo(50);
    }

    @Test
    void publishQpsStrategy_withHoldProfile_keepsLastPointAfterProfileEnd() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setQpsMode(TaskConfig.QpsMode.DYNAMIC);
        c.setPublishProfileDataPoints(List.of(new long[] {0, 0}, new long[] {1_000, 100}, new long[] {2_000, 20}));
        c.setProfileConfig(TaskConfig.ProfileConfig.builder()
            .endBehavior(ProfileQpsSpec.EndBehavior.HOLD)
            .build());

        var strategy = WorkerPlanSpecMapper.publishQpsStrategy(c);

        assertThat(strategy.currentQps(2_500)).isEqualTo(20);
    }

    @Test
    void publishQpsStrategy_withFixedPublishRate_usesConfiguredNodePublishQps() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 15, 1, 2);
        c.setQpsMode(TaskConfig.QpsMode.FIXED);
        c.setPublishRate(2.5);

        var strategy = WorkerPlanSpecMapper.publishQpsStrategy(c);

        assertThat(PubSubClientCountPlanner.expectedPubCount(countSpec(c))).isEqualTo(5);
        assertThat(strategy.isDynamic()).isFalse();
        assertThat(strategy.currentQpsValue(0)).isEqualTo(2.5);
    }

    @Test
    void publishQpsStrategy_withFixedLowPublishRate_keepsFractionalNodeRate() {
        TaskConfig c = config(TaskTemplate.PUBSUB_PUB_ONLY, 3, 1, 1);
        c.setQpsMode(TaskConfig.QpsMode.FIXED);
        c.setPublishRate(0.1);

        var strategy = WorkerPlanSpecMapper.publishQpsStrategy(c);

        assertThat(strategy.currentQpsValue(0)).isCloseTo(0.1, org.assertj.core.data.Offset.offset(1.0e-9));
    }

    @Test
    void publishQpsStrategy_withDynamicProfile_ignoresFixedPublishRate() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setQpsMode(TaskConfig.QpsMode.DYNAMIC);
        c.setPublishRate(999);
        c.setPublishProfileDataPoints(List.of(new long[] {0, 20}, new long[] {1_000, 40}));

        var strategy = WorkerPlanSpecMapper.publishQpsStrategy(c);

        assertThat(strategy.isDynamic()).isTrue();
        assertThat(strategy.currentQpsValue(1_000)).isEqualTo(40.0);
    }

    @Test
    void buildExecutionContext_withoutSubscribeRate_defaultsSubscribeRateToConnectRate() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(123);
        c.setDisconnectRate(456);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 1, 1);

        assertThat(context.subscribeRateLimiter().getPermitsPerSecond()).isEqualTo(123);
    }

    @Test
    void buildExecutionContext_withSubscribeRate_usesExplicitSubscribeRate() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(123);
        c.setDisconnectRate(456);
        c.setSubscribeRate(77);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 1, 1);

        assertThat(context.subscribeRateLimiter().getPermitsPerSecond()).isEqualTo(77);
    }

    @Test
    void buildExecutionContext_withTopicsPerClient_scalesSubscribeRateToClientCalls() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(123);
        c.setDisconnectRate(456);
        c.setSubscribeRate(3000);
        c.setTopicsPerClient(10);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 1, 1);

        assertThat(context.subscribeRateLimiter().getPermitsPerSecondValue()).isEqualTo(300.0);
    }

    @Test
    void buildExecutionContext_withLowSubscribeRate_keepsFractionalClientRate() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(123);
        c.setDisconnectRate(456);
        c.setSubscribeRate(1);
        c.setTopicsPerClient(10);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 1, 1);

        assertThat(context.subscribeRateLimiter().getPermitsPerSecondValue()).isEqualTo(0.1);
    }

    @Test
    void buildExecutionContext_shouldExposeCoordinatorNodeCounts() {
        TaskConfig c = configWithCoordinatorCounts(3, 7);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(123);
        c.setDisconnectRate(456);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 3, 7);

        assertThat(context.nodePubCount()).isEqualTo(3);
        assertThat(context.nodeSubCount()).isEqualTo(7);
        assertThat(context.expectedPubCount()).isEqualTo(3);
        assertThat(context.expectedSubCount()).isEqualTo(7);
    }

    @Test
    void buildExecutionContext_withEmptySubscribeProfile_defaultsSubscribeQpsToConnectRate() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(123);
        c.setDisconnectRate(456);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 1, 1);

        assertThat(context.subscribeQpsStrategy().currentQps(0)).isEqualTo(123);
    }

    @Test
    void buildExecutionContext_withTopicsPerClient_scalesSubscribeQpsStrategy() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(100);
        c.setDisconnectRate(456);
        c.setSubscribeRate(3000);
        c.setTopicsPerClient(10);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 1, 1);

        assertThat(context.subscribeQpsStrategy().currentQps(0)).isEqualTo(300);
    }

    @Test
    void executionContext_connectDispatchPlan_prefersProfileOverWaveSpec() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(123);
        c.setDisconnectRate(456);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));
        c.setConnectProfileDataPoints(List.of(new long[] {0, 0}, new long[] {1_000, 20}));
        c.setConnectWaveQpsSpec(WaveQpsSpec.builder()
            .baseQps(1)
            .totalDurationMs(5_000)
            .build());

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 1, 1);
        var plan = context.connectDispatchPlan(10);

        assertThat(plan.durationMs()).isEqualTo(1_000);
        assertThat(plan.plannedTotalCount()).isEqualTo(10);
    }

    @Test
    void executionContext_disconnectDispatchPlan_fallsBackToWaveSpec() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(123);
        c.setDisconnectRate(456);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));
        c.setDisconnectWaveQpsSpec(WaveQpsSpec.builder()
            .baseQps(10)
            .totalDurationMs(2_000)
            .build());

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 1, 1);
        var plan = context.disconnectDispatchPlan(10);

        assertThat(plan.durationMs()).isEqualTo(2_000);
        assertThat(plan.plannedTotalCount()).isEqualTo(10);
    }

    @Test
    void subscribeQpsStrategy_scalesProfileByTopicsPerClient() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(123);
        c.setDisconnectRate(456);
        c.setTopicsPerClient(10);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));
        c.setSubscribeProfileDataPoints(List.of(new long[] {0, 0}, new long[] {1_000, 3000}));

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 1, 1);

        assertThat(context.subscribeQpsStrategy().currentQpsValue(1_000)).isEqualTo(300.0);
    }

    @Test
    void subscribeQpsStrategy_keepsFractionalClientRate() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(123);
        c.setDisconnectRate(456);
        c.setTopicsPerClient(10);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));
        c.setSubscribeProfileDataPoints(List.of(new long[] {0, 1}, new long[] {10_000, 1}));

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 1, 1);

        assertThat(context.subscribeQpsStrategy().currentQpsValue(10_000)).isEqualTo(0.1);
    }

    @Test
    void buildExecutionContext_withLocalPortModeAndAutoMultiAddressDisabled_usesPrimaryAddress() {
        TaskConfig c = config(TaskTemplate.PUBSUB_STANDARD, 10, 1, 1);
        c.setTaskId("task");
        c.setNodeId("node");
        c.setConnectRate(123);
        c.setDisconnectRate(456);
        c.setRateLimiterType(org.apache.bifromq.testsuite.worker.RateLimiterType.GUAVA);
        c.setBrokers(List.of(new org.apache.bifromq.testsuite.worker.TaskBroker("localhost", 1883)));
        c.setEnableAutoMultiAddress(false);
        c.setLocalPortRangeConfig(LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(10010)
            .build());

        var context = WorkerPlanSpecMapper.buildExecutionContext(c, 1, 1);
        var subscribeCount = new java.util.concurrent.atomic.AtomicInteger();
        var firstClientConfig = context.mqttClientConfigFactory().create(0, subscribeCount);
        var secondClientConfig = context.mqttClientConfigFactory().create(1, subscribeCount);

        assertThat(c.isEnableAutoMultiAddress()).isFalse();
        assertThat(firstClientConfig.getLocalAddress()).isNotBlank();
        assertThat(secondClientConfig.getLocalAddress()).isEqualTo(firstClientConfig.getLocalAddress());
        assertThat(firstClientConfig.getLocalPort()).isEqualTo(10000);
        assertThat(secondClientConfig.getLocalPort()).isEqualTo(10001);
    }
}
