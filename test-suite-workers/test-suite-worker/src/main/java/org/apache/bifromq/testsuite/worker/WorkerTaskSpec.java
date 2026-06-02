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

import io.netty.handler.codec.mqtt.MqttQoS;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.WillConfig;
import org.apache.bifromq.testsuite.client.AuthType;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.client.MqttClientImpl;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;
import org.apache.bifromq.testsuite.worker.ratelimit.IRateLimiter;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WorkerTaskSpec implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String taskId;
    @Builder.Default
    private String nodeId = "";
    private TaskConfig.TaskType taskType;
    private TaskTemplate template;
    private String protocol;
    @Builder.Default
    private List<String> localAddresses = new ArrayList<>();
    @Builder.Default
    private List<TaskBroker> brokers = new ArrayList<>();
    private String username;
    private String password;
    @Builder.Default
    private int thingIdStartAt = 0;
    private boolean cleanSession;
    @Builder.Default
    private int keepAliveInSec = 120;
    @Builder.Default
    private int ackTimeoutInSec = 120;
    @Builder.Default
    private int reconnectMaxAttempts = 2;
    @Builder.Default
    private int reconnectIntervalInMs = 5000;
    @Builder.Default
    private int connectTimeoutInMs = 10000;
    @Builder.Default
    private int maxInflightQueue = 200;
    private int totalClientCount;
    @Builder.Default
    private int fanOut = 1;
    @Builder.Default
    private int fanIn = 1;
    @Builder.Default
    private int topicsPerClient = 1;
    private String topic;
    private MqttQoS qos;
    private boolean wildcard;
    @Builder.Default
    private int messageSize = 32;
    @Builder.Default
    private double publishRate = 1.0;
    private int stressDurationInSec;
    @Builder.Default
    private int stageTimeoutInSec = 30;
    @Builder.Default
    private int delayAfterStageInSec = 30;
    private boolean retain;
    private boolean mqtt5;
    private MqttClientImpl clientImpl;
    private AuthType authType;
    private boolean emptyClientId;
    @Builder.Default
    private long expiryIntervalInSec = 120;
    @Builder.Default
    private int connectRate = 500;
    @Builder.Default
    private int disconnectRate = 500;
    @Builder.Default
    private IRateLimiter.Type rateLimiterType = IRateLimiter.Type.GUAVA;
    private boolean enableAutoMultiAddress;
    @Builder.Default
    private LocalPortRangeConfig localPortRangeConfig = new LocalPortRangeConfig();
    @Builder.Default
    private WillConfig willConfig = new WillConfig();
    @Builder.Default
    private int subWorkerPoolSize = Runtime.getRuntime().availableProcessors() * 2;
    @Builder.Default
    private PayloadMode payloadMode = PayloadMode.BIFRO;
    private String payloadTemplate;
    @Builder.Default
    private TaskConfig.QpsMode qpsMode = TaskConfig.QpsMode.FIXED;
    private TaskConfig.ProfileConfig profileConfig;
    private WaveQpsSpec waveQpsSpec;
    private WaveQpsSpec connectWaveQpsSpec;
    private WaveQpsSpec disconnectWaveQpsSpec;
    private org.apache.bifromq.testsuite.chaos.ChaosPolicy chaosPolicy;
    private List<long[]> connectProfileDataPoints;
    private List<long[]> disconnectProfileDataPoints;
    @Builder.Default
    private TaskConfig.SubscribeQpsMode subscribeQpsMode = TaskConfig.SubscribeQpsMode.FIXED;
    private int subscribeRate;
    private List<long[]> subscribeProfileDataPoints;
    private List<long[]> publishProfileDataPoints;
    private Long plannedStartAtMs;
    @Builder.Default
    private int nodePubCount = -1;
    @Builder.Default
    private int nodeSubCount = -1;

    public static WorkerTaskSpec fromTaskConfig(TaskConfig config) {
        return WorkerTaskSpec.builder()
            .taskId(config.getTaskId())
            .nodeId(config.getNodeId())
            .taskType(config.getTaskType())
            .template(config.getTemplate())
            .protocol(config.getProtocol())
            .localAddresses(config.getLocalAddresses())
            .brokers(config.getBrokers())
            .username(config.getUsername())
            .password(config.getPassword())
            .thingIdStartAt(config.getThingIdStartAt())
            .cleanSession(config.isCleanSession())
            .keepAliveInSec(config.getKeepAliveInSec())
            .ackTimeoutInSec(config.getAckTimeoutInSec())
            .reconnectMaxAttempts(config.getReconnectMaxAttempts())
            .reconnectIntervalInMs(config.getReconnectIntervalInMs())
            .connectTimeoutInMs(config.getConnectTimeoutInMs())
            .maxInflightQueue(config.getMaxInflightQueue())
            .totalClientCount(config.getTotalClientCount())
            .fanOut(config.getFanOut())
            .fanIn(config.getFanIn())
            .topicsPerClient(config.getTopicsPerClient())
            .topic(config.getTopic())
            .qos(config.getQos())
            .wildcard(config.isWildcard())
            .messageSize(config.getMessageSize())
            .publishRate(config.getPublishRate())
            .stressDurationInSec(config.getStressDurationInSec())
            .stageTimeoutInSec(config.getStageTimeoutInSec())
            .delayAfterStageInSec(config.getDelayAfterStageInSec())
            .retain(config.isRetain())
            .mqtt5(config.isMqtt5())
            .clientImpl(config.getClientImpl())
            .authType(config.getAuthType())
            .emptyClientId(config.isEmptyClientId())
            .expiryIntervalInSec(config.getExpiryIntervalInSec())
            .connectRate(config.getConnectRate())
            .disconnectRate(config.getDisconnectRate())
            .rateLimiterType(config.getRateLimiterType())
            .enableAutoMultiAddress(config.isEnableAutoMultiAddress())
            .localPortRangeConfig(config.getLocalPortRangeConfig())
            .willConfig(config.getWillConfig())
            .subWorkerPoolSize(config.getSubWorkerPoolSize())
            .payloadMode(config.getPayloadMode())
            .payloadTemplate(config.getPayloadTemplate())
            .qpsMode(config.getQpsMode())
            .profileConfig(config.getProfileConfig())
            .waveQpsSpec(config.getWaveQpsSpec())
            .connectWaveQpsSpec(config.getConnectWaveQpsSpec())
            .disconnectWaveQpsSpec(config.getDisconnectWaveQpsSpec())
            .chaosPolicy(config.getChaosPolicy())
            .connectProfileDataPoints(config.getConnectProfileDataPoints())
            .disconnectProfileDataPoints(config.getDisconnectProfileDataPoints())
            .subscribeQpsMode(config.getSubscribeQpsMode())
            .subscribeRate(config.getSubscribeRate())
            .subscribeProfileDataPoints(config.getSubscribeProfileDataPoints())
            .publishProfileDataPoints(config.getPublishProfileDataPoints())
            .nodePubCount(config.getNodePubCount())
            .nodeSubCount(config.getNodeSubCount())
            .build();
    }

    public TaskConfig toTaskConfig() {
        return TaskConfig.builder()
            .taskId(taskId)
            .nodeId(nodeId)
            .taskType(taskType)
            .template(template)
            .protocol(protocol)
            .localAddresses(localAddresses == null ? new ArrayList<>() : localAddresses)
            .brokers(brokers == null ? new ArrayList<>() : brokers)
            .username(username)
            .password(password)
            .thingIdStartAt(thingIdStartAt)
            .cleanSession(cleanSession)
            .keepAliveInSec(keepAliveInSec)
            .ackTimeoutInSec(ackTimeoutInSec)
            .reconnectMaxAttempts(reconnectMaxAttempts)
            .reconnectIntervalInMs(reconnectIntervalInMs)
            .connectTimeoutInMs(connectTimeoutInMs)
            .maxInflightQueue(maxInflightQueue)
            .totalClientCount(totalClientCount)
            .fanOut(fanOut)
            .fanIn(fanIn)
            .topicsPerClient(topicsPerClient)
            .topic(topic)
            .qos(qos)
            .isWildcard(wildcard)
            .messageSize(messageSize)
            .publishRate(publishRate)
            .stressDurationInSec(stressDurationInSec)
            .stageTimeoutInSec(stageTimeoutInSec)
            .delayAfterStageInSec(delayAfterStageInSec)
            .retain(retain)
            .isMqtt5(mqtt5)
            .clientImpl(clientImpl)
            .authType(authType)
            .isEmptyClientId(emptyClientId)
            .expiryIntervalInSec(expiryIntervalInSec)
            .connectRate(connectRate)
            .disconnectRate(disconnectRate)
            .rateLimiterType(rateLimiterType)
            .enableAutoMultiAddress(enableAutoMultiAddress)
            .localPortRangeConfig(localPortRangeConfig)
            .willConfig(willConfig)
            .subWorkerPoolSize(subWorkerPoolSize)
            .payloadMode(payloadMode)
            .payloadTemplate(payloadTemplate)
            .qpsMode(qpsMode)
            .profileConfig(profileConfig)
            .waveQpsSpec(waveQpsSpec)
            .connectWaveQpsSpec(connectWaveQpsSpec)
            .disconnectWaveQpsSpec(disconnectWaveQpsSpec)
            .chaosPolicy(chaosPolicy)
            .connectProfileDataPoints(connectProfileDataPoints)
            .disconnectProfileDataPoints(disconnectProfileDataPoints)
            .subscribeQpsMode(subscribeQpsMode)
            .subscribeRate(subscribeRate)
            .subscribeProfileDataPoints(subscribeProfileDataPoints)
            .publishProfileDataPoints(publishProfileDataPoints)
            .nodePubCount(nodePubCount)
            .nodeSubCount(nodeSubCount)
            .build();
    }
}
