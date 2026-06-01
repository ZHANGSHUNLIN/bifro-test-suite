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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.List;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.WillConfig;
import org.apache.bifromq.testsuite.app.bean.dto.BrokerEntry;
import org.apache.bifromq.testsuite.app.bean.dto.TaskRequest;
import org.apache.bifromq.testsuite.client.AuthType;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.qps.ProfileQpsSpec;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.junit.jupiter.api.Test;

class TaskSpecMapperTest {

    @Test
    void taskRequestShouldCreateTaskSpecAndPreserveTaskConfigMapping() {
        TaskConfig.ProfileConfig profileConfig = TaskConfig.ProfileConfig.builder()
            .profileId("profile-1")
            .dataPoints(List.of(new long[] {0, 10}))
            .endBehavior(ProfileQpsSpec.EndBehavior.HOLD)
            .totalDurationMs(1000)
            .build();
        WaveQpsSpec waveQpsSpec = WaveQpsSpec.builder()
            .baseQps(10)
            .totalDurationMs(1000)
            .build();
        WillConfig willConfig = new WillConfig(true, "will/topic", "bye", null, 1, false);
        TaskRequest request = new TaskRequest();
        request.setTaskType(TaskConfig.TaskType.PUBSUB);
        request.setTemplate(TaskTemplate.PUBSUB_STANDARD.name());
        request.setGroup("group-a");
        request.setProtocol("ssl");
        request.setAutoMultiAddress(true);
        request.setLocalAddresses(List.of("127.0.0.1"));
        request.setBrokers(List.of(broker("broker-1", "localhost", 1883)));
        request.setUsername("user");
        request.setPassword("pass");
        request.setTenantId("tenant");
        request.setThingIdStartAt(100);
        request.setThingIdPrefix("thing");
        request.setCleanSession(false);
        request.setKeepAliveInSec(60);
        request.setAckTimeoutInSec(30);
        request.setReconnectMaxAttempts(3);
        request.setReconnectIntervalInMs(1000);
        request.setConnectTimeoutInMs(2000);
        request.setMaxInflightQueue(10);
        request.setTotalClientCount(50);
        request.setFanOut(2);
        request.setFanIn(3);
        request.setTopicsPerClient(4);
        request.setTopic("topic/${thingId}");
        request.setQos(MqttQoS.AT_LEAST_ONCE);
        request.setFixedTopic(true);
        request.setWildcard(true);
        request.setMessageSize(128);
        request.setPublishRate(1.0);
        request.setStressDurationInSec(90);
        request.setStageTimeoutInSec(20);
        request.setDelayAfterStageInSec(10);
        request.setRetain(true);
        request.setMqtt5(true);
        request.setAuthType(AuthType.BYOC);
        request.setClientCertEnabled(true);
        request.setClientCertId("cert-1");
        request.setEmptyClientId(true);
        request.setExpiryIntervalInSec(600);
        request.setConnectRate(1000);
        request.setDisconnectRate(200);
        request.setWillConfig(willConfig);
        request.setPayloadMode(PayloadMode.TEMPLATE);
        request.setPayloadTemplate("{\"id\":\"${thingId}\"}");
        request.setWaveQpsSpec(waveQpsSpec);
        request.setQpsMode(TaskConfig.QpsMode.DYNAMIC);
        request.setProfileConfig(profileConfig);
        request.setConnectWaveQpsSpec(waveQpsSpec);
        request.setDisconnectWaveQpsSpec(waveQpsSpec);
        request.setConnectProfileId("connect-profile");
        request.setDisconnectProfileId("disconnect-profile");
        request.setSubscribeQpsMode(TaskConfig.SubscribeQpsMode.DYNAMIC);
        request.setSubscribeRate(25);
        request.setSubscribeProfileId("subscribe-profile");

        TaskSpec spec = request.toTaskSpec();
        TaskConfig config = request.toTaskConfig();

        assertThat(spec.taskType()).isEqualTo(TaskConfig.TaskType.PUBSUB);
        assertThat(spec.template()).isEqualTo(TaskTemplate.PUBSUB_STANDARD);
        assertThat(spec.clientCertId()).isEqualTo("cert-1");
        assertThat(config.getTaskType()).isEqualTo(TaskConfig.TaskType.PUBSUB);
        assertThat(config.getTemplate()).isEqualTo(TaskTemplate.PUBSUB_STANDARD);
        assertThat(config.getProtocol()).isEqualTo("ssl");
        assertThat(config.getLocalAddresses()).containsExactly("127.0.0.1");
        assertThat(config.getBrokers()).singleElement()
            .satisfies(broker -> {
                assertThat(broker.getHost()).isEqualTo("localhost");
                assertThat(broker.getPort()).isEqualTo(1883);
            });
        assertThat(config.getUsername()).isEqualTo("user");
        assertThat(config.getPassword()).isEqualTo("pass");
        assertThat(config.getTenantId()).isEqualTo("tenant");
        assertThat(config.getThingIdStartAt()).isEqualTo(100);
        assertThat(config.getThingIdPrefix()).isEqualTo("thing");
        assertThat(config.isCleanSession()).isFalse();
        assertThat(config.getKeepAliveInSec()).isEqualTo(60);
        assertThat(config.getAckTimeoutInSec()).isEqualTo(30);
        assertThat(config.getReconnectMaxAttempts()).isEqualTo(3);
        assertThat(config.getReconnectIntervalInMs()).isEqualTo(1000);
        assertThat(config.getConnectTimeoutInMs()).isEqualTo(2000);
        assertThat(config.getMaxInflightQueue()).isEqualTo(10);
        assertThat(config.getTotalClientCount()).isEqualTo(50);
        assertThat(config.getFanOut()).isEqualTo(2);
        assertThat(config.getFanIn()).isEqualTo(3);
        assertThat(config.getTopicsPerClient()).isEqualTo(4);
        assertThat(config.getTopic()).isEqualTo("topic/${thingId}");
        assertThat(config.getQos()).isEqualTo(MqttQoS.AT_LEAST_ONCE);
        assertThat(config.isFixedTopic()).isTrue();
        assertThat(config.isWildcard()).isTrue();
        assertThat(config.getMessageSize()).isEqualTo(128);
        assertThat(config.getPublishRate()).isEqualTo(1.0);
        assertThat(config.getStressDurationInSec()).isEqualTo(90);
        assertThat(config.getStageTimeoutInSec()).isEqualTo(20);
        assertThat(config.getDelayAfterStageInSec()).isEqualTo(10);
        assertThat(config.isRetain()).isTrue();
        assertThat(config.isMqtt5()).isTrue();
        assertThat(config.getAuthType()).isEqualTo(AuthType.BYOC);
        assertThat(config.getClientCertId()).isEqualTo("cert-1");
        assertThat(config.isEmptyClientId()).isTrue();
        assertThat(config.getExpiryIntervalInSec()).isEqualTo(600);
        assertThat(config.getConnectRate()).isEqualTo(1000);
        assertThat(config.getDisconnectRate()).isEqualTo(200);
        assertThat(config.getGroup()).isEqualTo("group-a");
        assertThat(config.getWillConfig()).isSameAs(willConfig);
        assertThat(config.isEnableAutoMultiAddress()).isTrue();
        assertThat(config.getPayloadMode()).isEqualTo(PayloadMode.TEMPLATE);
        assertThat(config.getPayloadTemplate()).isEqualTo("{\"id\":\"${thingId}\"}");
        assertThat(config.getQpsMode()).isEqualTo(TaskConfig.QpsMode.DYNAMIC);
        assertThat(config.getProfileConfig()).isSameAs(profileConfig);
        assertThat(config.getWaveQpsSpec()).isSameAs(waveQpsSpec);
        assertThat(config.getConnectWaveQpsSpec()).isSameAs(waveQpsSpec);
        assertThat(config.getDisconnectWaveQpsSpec()).isSameAs(waveQpsSpec);
        assertThat(config.getConnectProfileId()).isEqualTo("connect-profile");
        assertThat(config.getDisconnectProfileId()).isEqualTo("disconnect-profile");
        assertThat(config.getSubscribeQpsMode()).isEqualTo(TaskConfig.SubscribeQpsMode.DYNAMIC);
        assertThat(config.getSubscribeRate()).isEqualTo(25);
        assertThat(config.getSubscribeProfileId()).isEqualTo("subscribe-profile");
    }

    @Test
    void taskRequestShouldIgnoreClientCertificateIdWhenClientCertificateDisabled() {
        TaskRequest request = new TaskRequest();
        request.setTemplate(TaskTemplate.CONN_STANDARD.name());
        request.setBrokers(List.of(broker("broker-1", "localhost", 1883)));
        request.setClientCertEnabled(false);
        request.setClientCertId("cert-1");

        TaskConfig config = request.toTaskConfig();

        assertThat(request.toTaskSpec().clientCertId()).isNull();
        assertThat(config.getClientCertId()).isNull();
    }

    @Test
    void taskRequestShouldDeserializeLegacyBooleanPropertyNames() throws Exception {
        String json = """
            {
              "taskType": "CONN",
              "template": "CONN_STANDARD",
              "group": "group-a",
              "protocol": "mqtt",
              "brokers": [{"brokerId": "broker-1", "host": "localhost", "port": 1883}],
              "isMqtt5": true,
              "isEmptyClientId": true
            }
            """;

        TaskRequest request = new ObjectMapper().readValue(json, TaskRequest.class);

        assertThat(request.toTaskConfig().isMqtt5()).isTrue();
        assertThat(request.toTaskConfig().isEmptyClientId()).isTrue();
    }

    @Test
    void taskSpecMapperShouldApplyDefaultModesWhenSpecModesAreNull() {
        TaskSpec spec = TaskSpec.builder().build();

        TaskConfig config = TaskSpecMapper.toTaskConfig(spec);

        assertThat(config.getPayloadMode()).isEqualTo(PayloadMode.BIFRO);
        assertThat(config.getQpsMode()).isEqualTo(TaskConfig.QpsMode.FIXED);
        assertThat(config.getSubscribeQpsMode()).isEqualTo(TaskConfig.SubscribeQpsMode.FIXED);
    }

    private BrokerEntry broker(String brokerId, String host, int port) {
        BrokerEntry broker = new BrokerEntry();
        broker.setBrokerId(brokerId);
        broker.setHost(host);
        broker.setPort(port);
        return broker;
    }
}
