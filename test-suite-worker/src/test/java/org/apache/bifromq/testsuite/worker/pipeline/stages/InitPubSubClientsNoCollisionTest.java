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

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.WillConfig;
import org.apache.bifromq.testsuite.client.AuthType;
import org.apache.bifromq.testsuite.client.LocalAddressProvider;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.client.MqttClientConfigFactory;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.statemachine.StateMachine;
import org.apache.bifromq.testsuite.worker.TaskBroker;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionContext;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;
import org.apache.bifromq.testsuite.worker.ratelimit.GuavaRateLimiter;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpecMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class InitPubSubClientsNoCollisionTest {

    private static final String TASK_ID = "test-pubsub-task";
    private static final int PUB_COUNT = 5;
    private static final int SUB_COUNT = 5;

    @Mock
    private TaskConfig taskConfig;

    private Vertx vertx;
    private StateMachine<TaskStage, TaskEvent> stateMachine;
    private ConcurrentHashMap<String, MqttClientTask> pubClients;
    private ConcurrentHashMap<String, MqttClientTask> subClients;
    private MqttClientConfigFactory factory;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        stateMachine = new StateMachine<>(TaskStage.INIT);
        pubClients = new ConcurrentHashMap<>();
        subClients = new ConcurrentHashMap<>();

        when(taskConfig.getTaskId()).thenReturn(TASK_ID);
        when(taskConfig.getNodeId()).thenReturn("node-7504");
        when(taskConfig.getThingIdStartAt()).thenReturn(0);
        when(taskConfig.getTotalClientCount()).thenReturn(PUB_COUNT + SUB_COUNT);
        when(taskConfig.getMessageSize()).thenReturn(32);
        when(taskConfig.getPublishRate()).thenReturn(1.0);
        when(taskConfig.getStressDurationInSec()).thenReturn(60);
        when(taskConfig.isMqtt5()).thenReturn(false);
        when(taskConfig.isRetain()).thenReturn(false);
        when(taskConfig.getQos()).thenReturn(MqttQoS.AT_MOST_ONCE);
        when(taskConfig.isWildcard()).thenReturn(false);
        when(taskConfig.getFanOut()).thenReturn(1);
        when(taskConfig.getFanIn()).thenReturn(1);
        when(taskConfig.getTopicsPerClient()).thenReturn(1);
        when(taskConfig.getTemplate()).thenReturn(TaskTemplate.PUBSUB_STANDARD);
        when(taskConfig.getTopic()).thenReturn(TASK_ID + "/test");
        when(taskConfig.getBrokers()).thenReturn(Collections.singletonList(
            new TaskBroker("localhost", 1883)));

        factory = MqttClientConfigFactory.builder()
            .taskId(TASK_ID)
            .nodeId("node-7504")
            .brokers(Collections.singletonList(
                new MqttClientConfigFactory.TaskBrokerAddress("localhost", 1883)))
            .authType(AuthType.NONE)
            .willConfig(new WillConfig())
            .build();
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    private TaskPipelineContext createContext(int pubCount, int subCount) {
        GuavaRateLimiter rateLimiter = new GuavaRateLimiter(100);
        TaskExecutionContext ec = new TaskExecutionContext(
            TASK_ID, WorkerPlanSpecMapper.buildExecutionConfig(taskConfig),
            pubCount + subCount, 60, 30, 0, 0, 1, 1,
            rateLimiter, rateLimiter, QpsStrategy.fixed(100), QpsStrategy.fixed(100),
            rateLimiter, QpsStrategy.fixed(Integer.MAX_VALUE),
            QpsStrategy.fixed(100),
            factory, pubCount, subCount, pubCount, subCount,
            new ConcurrentHashMap<>(), pubClients, subClients,
            Optional.empty());
        return new TaskPipelineContext(vertx, stateMachine, ec, result -> {
        });
    }

    @Test
    void initPubAndSubClients_withStandardPubSub_clientIdsMustBeUnique() throws Exception {

        TaskPipelineContext context = createContext(PUB_COUNT, SUB_COUNT);

        InitPubClientsStage pubStage = new InitPubClientsStage();
        InitSubClientsStage subStage = new InitSubClientsStage();
        StageResult pubResult = pubStage.execute(context).get();
        StageResult subResult = subStage.execute(context).get();
        assertThat(pubResult.isSuccess())
            .as("InitPubClientsStage should succeed").isTrue();
        assertThat(subResult.isSuccess())
            .as("InitSubClientsStage should succeed").isTrue();
        assertThat(pubClients).hasSize(PUB_COUNT);
        assertThat(subClients).hasSize(SUB_COUNT);
        Set<String> pubClientIds = pubClients.keySet();
        Set<String> subClientIds = subClients.keySet();
        Set<String> intersection = new java.util.HashSet<>(pubClientIds);
        intersection.retainAll(subClientIds);

        assertThat(intersection)
            .as("pub and sub clients must have no clientId collision, but found: %s", intersection)
            .isEmpty();
    }

    @Test
    void initPubClients_clientIdsHavePubPrefix() throws Exception {

        TaskPipelineContext context = createContext(PUB_COUNT, SUB_COUNT);
        InitPubClientsStage pubStage = new InitPubClientsStage();
        pubStage.execute(context).get();
        for (int i = 0; i < PUB_COUNT; i++) {
            String expectedId = String.format("%s_node_p_%07d", TASK_ID, i);
            assertThat(pubClients).as("pub clientId at index %d should exist", i)
                .containsKey(expectedId);
        }
    }

    @Test
    void initSubClients_clientIdsHaveSubPrefixStartingAtIndex0() throws Exception {

        TaskPipelineContext context = createContext(PUB_COUNT, SUB_COUNT);
        InitSubClientsStage subStage = new InitSubClientsStage();
        subStage.execute(context).get();
        for (int i = 0; i < SUB_COUNT; i++) {
            String expectedId = String.format("%s_node_s_%07d", TASK_ID, i);
            assertThat(subClients).as("sub clientId at index %d should exist", i)
                .containsKey(expectedId);
        }

        for (int i = 0; i < PUB_COUNT; i++) {
            String pubId = String.format("%s_node_p_%07d", TASK_ID, i);
            assertThat(subClients).as("sub must not contain pub clientId %s", pubId)
                .doesNotContainKey(pubId);
        }
    }

    @Test
    void initClients_withTopicsPerClient_generatesMultipleMatchingTopics() throws Exception {
        when(taskConfig.getTopicsPerClient()).thenReturn(3);
        when(taskConfig.getTopic()).thenReturn(null);
        TaskPipelineContext context = createContext(PUB_COUNT, SUB_COUNT);

        new InitPubClientsStage().execute(context).get();
        new InitSubClientsStage().execute(context).get();

        MqttClientTask pubClient = pubClients.get(String.format("%s_node_p_%07d", TASK_ID, 0));
        MqttClientTask subClient = subClients.get(String.format("%s_node_s_%07d", TASK_ID, 0));
        assertThat(taskConfigOf(pubClient).getPubTopics())
            .containsExactly(TASK_ID + "/0/0", TASK_ID + "/0/1", TASK_ID + "/0/2");
        assertThat(taskConfigOf(subClient).getTopicFilters())
            .extracting(org.apache.bifromq.testsuite.models.TopicFilter::getName)
            .containsExactlyInAnyOrder(TASK_ID + "/0/0", TASK_ID + "/0/1", TASK_ID + "/0/2");
    }

    private ClientTaskConfig taskConfigOf(MqttClientTask clientTask) throws Exception {
        Field field = MqttClientTask.class.getDeclaredField("taskConfig");
        field.setAccessible(true);
        return (ClientTaskConfig) field.get(clientTask);
    }

    @Test
    void initClients_withLocalAddresses_shouldNotAssignLocalPortDuringInit() throws Exception {
        factory = MqttClientConfigFactory.builder()
            .taskId(TASK_ID)
            .nodeId("node-7504")
            .brokers(Collections.singletonList(
                new MqttClientConfigFactory.TaskBrokerAddress("localhost", 1883)))
            .authType(AuthType.NONE)
            .willConfig(new WillConfig())
            .localAddressProvider(LocalAddressProvider.of(java.util.List.of("127.0.0.1")))
            .build();
        TaskPipelineContext context = createContext(PUB_COUNT, SUB_COUNT);

        new InitPubClientsStage().execute(context).get();
        new InitSubClientsStage().execute(context).get();

        assertThat(pubClients.values())
            .allSatisfy(client -> {
                assertThat(client.getClientConfig().getLocalAddress()).isEqualTo("127.0.0.1");
                assertThat(client.getClientConfig().getLocalPort()).isZero();
            });
        assertThat(subClients.values())
            .allSatisfy(client -> {
                assertThat(client.getClientConfig().getLocalAddress()).isEqualTo("127.0.0.1");
                assertThat(client.getClientConfig().getLocalPort()).isZero();
            });
    }

    @Test
    void initClients_withLocalPortMode_shouldAssignFixedPortsWithoutPubSubCollision() throws Exception {
        factory = MqttClientConfigFactory.builder()
            .taskId(TASK_ID)
            .nodeId("node-7504")
            .brokers(Collections.singletonList(
                new MqttClientConfigFactory.TaskBrokerAddress("localhost", 1883)))
            .authType(AuthType.NONE)
            .willConfig(new WillConfig())
            .localAddressProvider(LocalAddressProvider.of(java.util.List.of("127.0.0.1")))
            .localPortRangeConfig(LocalPortRangeConfig.builder()
                .enabled(true)
                .startPort(10000)
                .endPort(65535)
                .build())
            .build();
        when(taskConfig.getNodePubCount()).thenReturn(PUB_COUNT);
        TaskPipelineContext context = createContext(PUB_COUNT, SUB_COUNT);

        new InitPubClientsStage().execute(context).get();
        new InitSubClientsStage().execute(context).get();

        assertThat(pubClients.values())
            .extracting(client -> client.getClientConfig().getLocalPort())
            .containsExactlyInAnyOrder(10000, 10001, 10002, 10003, 10004);
        assertThat(subClients.values())
            .extracting(client -> client.getClientConfig().getLocalPort())
            .containsExactlyInAnyOrder(10005, 10006, 10007, 10008, 10009);
    }

    @Test
    void initPubAndSubClients_withAsymmetricCounts_clientIdsMustBeUnique() throws Exception {

        int pubCount = 3;
        int subCount = 7;
        TaskPipelineContext context = createContext(pubCount, subCount);
        new InitPubClientsStage().execute(context).get();
        new InitSubClientsStage().execute(context).get();
        Set<String> intersection = new java.util.HashSet<>(pubClients.keySet());
        intersection.retainAll(subClients.keySet());
        assertThat(intersection)
            .as("no clientId collision for asymmetric pub=%d sub=%d", pubCount, subCount)
            .isEmpty();
        assertThat(pubClients).hasSize(pubCount);
        assertThat(subClients).hasSize(subCount);
    }
}
