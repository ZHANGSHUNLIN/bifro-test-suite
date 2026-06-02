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

package org.apache.bifromq.testsuite.app.task.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.WillConfig;
import org.apache.bifromq.testsuite.client.AuthType;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.client.MqttClientImpl;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.qps.ProfileQpsSpec;
import org.apache.bifromq.testsuite.worker.TaskBroker;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.List;
import org.junit.jupiter.api.Test;

class NodeTaskConfigMapperTest {

    @Test
    void toNodeTaskConfigShouldCopyCommonFieldsAndApplyNodeExecutionFields() {
        WillConfig willConfig = new WillConfig(true, "will/topic", "bye", null, 1, false);
        LocalPortRangeConfig localPortRangeConfig = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(12000)
            .build();
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskWorkStage(TaskStage.ONGOING)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .taskId("task-1")
            .nodeId("main-node")
            .taskType(TaskConfig.TaskType.PUBSUB)
            .protocol("ssl")
            .localAddresses(List.of("127.0.0.1"))
            .brokers(List.of(TaskBroker.builder().host("localhost").port(1883).build()))
            .username("user")
            .password("pass")
            .thingIdStartAt(100)
            .cleanSession(true)
            .keepAliveInSec(60)
            .ackTimeoutInSec(30)
            .reconnectMaxAttempts(3)
            .reconnectIntervalInMs(1000)
            .connectTimeoutInMs(2000)
            .maxInflightQueue(10)
            .totalClientCount(100)
            .fanOut(2)
            .fanIn(3)
            .topic("topic/{{client_id}}")
            .qos(MqttQoS.AT_LEAST_ONCE)
            .fixedTopic(true)
            .isWildcard(true)
            .messageSize(128)
            .publishRate(1.0)
            .stressDurationInSec(90)
            .stageTimeoutInSec(20)
            .delayAfterStageInSec(10)
            .retain(true)
            .isMqtt5(true)
            .authType(AuthType.NORMAL)
            .isEmptyClientId(true)
            .expiryIntervalInSec(600)
            .connectRate(1000)
            .disconnectRate(200)
            .enableAutoMultiAddress(true)
            .localPortRangeConfig(localPortRangeConfig)
            .group("group-a")
            .willConfig(willConfig)
            .qpsMode(TaskConfig.QpsMode.DYNAMIC)
            .payloadMode(PayloadMode.TEMPLATE)
            .payloadTemplate("{\"id\":\"{{client_id}}\"}")
            .clientImpl(MqttClientImpl.HIVEMQ)
            .build();
        NodeExecutionConfig executionConfig = NodeExecutionConfig.builder()
            .nodeId("node-1")
            .nodeClientCount(25)
            .nodePubCount(15)
            .nodeSubCount(10)
            .mainTotalClientCount(100)
            .build();

        TaskConfig nodeTaskConfig = NodeTaskConfigMapper.toNodeTaskConfig(mainTaskConfig, executionConfig);

        assertThat(nodeTaskConfig.getTaskWorkStage()).isEqualTo(TaskStage.ONGOING);
        assertThat(nodeTaskConfig.getTemplate()).isEqualTo(TaskTemplate.PUBSUB_STANDARD);
        assertThat(nodeTaskConfig.getTaskId()).isEqualTo("task-1");
        assertThat(nodeTaskConfig.getNodeId()).isEqualTo("node-1");
        assertThat(nodeTaskConfig.getTaskType()).isEqualTo(TaskConfig.TaskType.PUBSUB);
        assertThat(nodeTaskConfig.getProtocol()).isEqualTo("ssl");
        assertThat(nodeTaskConfig.getLocalAddresses()).containsExactly("127.0.0.1");
        assertThat(nodeTaskConfig.getBrokers()).hasSize(1);
        assertThat(nodeTaskConfig.getUsername()).isEqualTo("user");
        assertThat(nodeTaskConfig.getPassword()).isEqualTo("pass");
        assertThat(nodeTaskConfig.getThingIdStartAt()).isEqualTo(100);
        assertThat(nodeTaskConfig.isCleanSession()).isTrue();
        assertThat(nodeTaskConfig.getKeepAliveInSec()).isEqualTo(60);
        assertThat(nodeTaskConfig.getAckTimeoutInSec()).isEqualTo(30);
        assertThat(nodeTaskConfig.getReconnectMaxAttempts()).isEqualTo(3);
        assertThat(nodeTaskConfig.getReconnectIntervalInMs()).isEqualTo(1000);
        assertThat(nodeTaskConfig.getConnectTimeoutInMs()).isEqualTo(2000);
        assertThat(nodeTaskConfig.getMaxInflightQueue()).isEqualTo(10);
        assertThat(nodeTaskConfig.getTotalClientCount()).isEqualTo(25);
        assertThat(nodeTaskConfig.getFanOut()).isEqualTo(2);
        assertThat(nodeTaskConfig.getFanIn()).isEqualTo(3);
        assertThat(nodeTaskConfig.getNodePubCount()).isEqualTo(15);
        assertThat(nodeTaskConfig.getNodeSubCount()).isEqualTo(10);
        assertThat(nodeTaskConfig.getTopic()).isEqualTo("topic/{{client_id}}");
        assertThat(nodeTaskConfig.getQos()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(nodeTaskConfig.isFixedTopic()).isTrue();
        assertThat(nodeTaskConfig.isWildcard()).isTrue();
        assertThat(nodeTaskConfig.getMessageSize()).isEqualTo(128);
        assertThat(nodeTaskConfig.getPublishRate()).isEqualTo(1.0);
        assertThat(nodeTaskConfig.getStressDurationInSec()).isEqualTo(90);
        assertThat(nodeTaskConfig.getStageTimeoutInSec()).isEqualTo(20);
        assertThat(nodeTaskConfig.getDelayAfterStageInSec()).isEqualTo(10);
        assertThat(nodeTaskConfig.isRetain()).isTrue();
        assertThat(nodeTaskConfig.isMqtt5()).isTrue();
        assertThat(nodeTaskConfig.getAuthType()).isEqualTo(AuthType.NORMAL);
        assertThat(nodeTaskConfig.isEmptyClientId()).isTrue();
        assertThat(nodeTaskConfig.getExpiryIntervalInSec()).isEqualTo(600);
        assertThat(nodeTaskConfig.getConnectRate()).isEqualTo(250);
        assertThat(nodeTaskConfig.getDisconnectRate()).isEqualTo(50);
        assertThat(nodeTaskConfig.isEnableAutoMultiAddress()).isTrue();
        assertThat(nodeTaskConfig.getLocalPortRangeConfig()).isSameAs(localPortRangeConfig);
        assertThat(nodeTaskConfig.getGroup()).isEqualTo("group-a");
        assertThat(nodeTaskConfig.getWillConfig()).isSameAs(willConfig);
        assertThat(nodeTaskConfig.getQpsMode()).isEqualTo(TaskConfig.QpsMode.DYNAMIC);
        assertThat(nodeTaskConfig.getPayloadMode()).isEqualTo(PayloadMode.TEMPLATE);
        assertThat(nodeTaskConfig.getPayloadTemplate()).isEqualTo("{\"id\":\"{{client_id}}\"}");
        assertThat(nodeTaskConfig.getClientImpl()).isEqualTo(MqttClientImpl.HIVEMQ);
    }

    @Test
    void toNodeTaskConfigShouldScaleProfileDataPointsWhenPreScaledPointsAreAbsent() {
        TaskConfig.ProfileConfig profileConfig = TaskConfig.ProfileConfig.builder()
            .dataPoints(List.of(new long[] {0, 100}, new long[] {1000, 40}))
            .totalDurationMs(1000)
            .endBehavior(ProfileQpsSpec.EndBehavior.HOLD)
            .build();
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .template(TaskTemplate.PUBSUB_PUB_ONLY)
            .totalClientCount(100)
            .connectProfileDataPoints(List.of(new long[] {0, 100}, new long[] {1000, 40}))
            .disconnectProfileDataPoints(List.of(new long[] {0, 20}))
            .subscribeProfileDataPoints(List.of(new long[] {0, 10}))
            .qpsMode(TaskConfig.QpsMode.DYNAMIC)
            .profileConfig(profileConfig)
            .connectRate(100)
            .disconnectRate(20)
            .subscribeRate(10)
            .build();
        NodeExecutionConfig executionConfig = NodeExecutionConfig.builder()
            .nodeId("node-1")
            .nodeClientCount(25)
            .nodePubCount(25)
            .mainTotalClientCount(100)
            .build();

        TaskConfig nodeTaskConfig = NodeTaskConfigMapper.toNodeTaskConfig(mainTaskConfig, executionConfig);

        assertThat(valuesOf(nodeTaskConfig.getConnectProfileDataPoints())).containsExactly(25L, 10L);
        assertThat(valuesOf(nodeTaskConfig.getDisconnectProfileDataPoints())).containsExactly(5L);
        assertThat(valuesOf(nodeTaskConfig.getSubscribeProfileDataPoints())).containsExactly(3L);
        assertThat(valuesOf(nodeTaskConfig.getPublishProfileDataPoints())).containsExactly(25L, 10L);
        assertThat(nodeTaskConfig.getConnectRate()).isEqualTo(25);
        assertThat(nodeTaskConfig.getDisconnectRate()).isEqualTo(5);
        assertThat(nodeTaskConfig.getSubscribeRate()).isEqualTo(3);
    }

    @Test
    void toNodeTaskConfigShouldScaleFixedPublishRateByPublisherRatio() {
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(100)
            .fanIn(1)
            .fanOut(1)
            .publishRate(10.0)
            .build();
        NodeExecutionConfig executionConfig = NodeExecutionConfig.builder()
            .nodeId("node-1")
            .nodeClientCount(25)
            .nodePubCount(10)
            .nodeSubCount(15)
            .mainTotalClientCount(100)
            .build();

        TaskConfig nodeTaskConfig = NodeTaskConfigMapper.toNodeTaskConfig(mainTaskConfig, executionConfig);

        assertThat(nodeTaskConfig.getPublishRate()).isEqualTo(2.0);
    }

    @Test
    void toNodeTaskConfigShouldScalePublishProfileByPublisherRatioWhenPreScaledPointsAreAbsent() {
        TaskConfig.ProfileConfig profileConfig = TaskConfig.ProfileConfig.builder()
            .dataPoints(List.of(new long[] {0, 100}, new long[] {1000, 40}))
            .totalDurationMs(1000)
            .endBehavior(ProfileQpsSpec.EndBehavior.HOLD)
            .build();
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(100)
            .fanIn(1)
            .fanOut(1)
            .qpsMode(TaskConfig.QpsMode.DYNAMIC)
            .profileConfig(profileConfig)
            .build();
        NodeExecutionConfig executionConfig = NodeExecutionConfig.builder()
            .nodeId("node-1")
            .nodeClientCount(25)
            .nodePubCount(10)
            .nodeSubCount(15)
            .mainTotalClientCount(100)
            .build();

        TaskConfig nodeTaskConfig = NodeTaskConfigMapper.toNodeTaskConfig(mainTaskConfig, executionConfig);

        assertThat(valuesOf(nodeTaskConfig.getPublishProfileDataPoints())).containsExactly(20L, 8L);
    }

    @Test
    void toNodeTaskConfigShouldUsePreScaledProfileDataPointsWhenProvided() {
        List<long[]> connect = List.of(new long[] {0, 7});
        List<long[]> disconnect = List.of(new long[] {0, 6});
        List<long[]> subscribe = List.of(new long[] {0, 5});
        List<long[]> publish = List.of(new long[] {0, 4});
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .totalClientCount(100)
            .connectProfileDataPoints(List.of(new long[] {0, 100}))
            .disconnectProfileDataPoints(List.of(new long[] {0, 100}))
            .subscribeProfileDataPoints(List.of(new long[] {0, 100}))
            .profileConfig(TaskConfig.ProfileConfig.builder()
                .dataPoints(List.of(new long[] {0, 100}))
                .build())
            .qpsMode(TaskConfig.QpsMode.DYNAMIC)
            .build();
        NodeExecutionConfig executionConfig = NodeExecutionConfig.builder()
            .nodeId("node-1")
            .nodeClientCount(25)
            .mainTotalClientCount(100)
            .preScaledConnectDataPoints(connect)
            .preScaledDisconnectDataPoints(disconnect)
            .preScaledSubscribeDataPoints(subscribe)
            .preScaledPublishDataPoints(publish)
            .build();

        TaskConfig nodeTaskConfig = NodeTaskConfigMapper.toNodeTaskConfig(mainTaskConfig, executionConfig);

        assertThat(nodeTaskConfig.getConnectProfileDataPoints()).isSameAs(connect);
        assertThat(nodeTaskConfig.getDisconnectProfileDataPoints()).isSameAs(disconnect);
        assertThat(nodeTaskConfig.getSubscribeProfileDataPoints()).isSameAs(subscribe);
        assertThat(nodeTaskConfig.getPublishProfileDataPoints()).isSameAs(publish);
    }

    private List<Long> valuesOf(List<long[]> dataPoints) {
        return dataPoints.stream().map(point -> point[1]).toList();
    }
}
