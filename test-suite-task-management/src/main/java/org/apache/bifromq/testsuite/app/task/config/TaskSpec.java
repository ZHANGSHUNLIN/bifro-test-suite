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

import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.WillConfig;
import org.apache.bifromq.testsuite.chaos.ChaosPolicy;
import org.apache.bifromq.testsuite.client.AuthType;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;
import org.apache.bifromq.testsuite.worker.TaskBroker;
import org.apache.bifromq.testsuite.worker.TaskConfig;

@Builder
public record TaskSpec(
    TaskConfig.TaskType taskType,
    TaskTemplate template,
    String protocol,
    List<String> localAddresses,
    List<TaskBroker> brokers,
    String username,
    String password,
    String tenantId,
    int thingIdStartAt,
    String thingIdPrefix,
    boolean cleanSession,
    int keepAliveInSec,
    int ackTimeoutInSec,
    int reconnectMaxAttempts,
    int reconnectIntervalInMs,
    int connectTimeoutInMs,
    int maxInflightQueue,
    int totalClientCount,
    int fanOut,
    int fanIn,
    int topicsPerClient,
    String topic,
    MqttQoS qos,
    boolean fixedTopic,
    boolean wildcard,
    int messageSize,
    double publishRate,
    int stressDurationInSec,
    int stageTimeoutInSec,
    int delayAfterStageInSec,
    boolean retain,
    boolean mqtt5,
    AuthType authType,
    boolean emptyClientId,
    long expiryIntervalInSec,
    int connectRate,
    int disconnectRate,
    String group,
    WillConfig willConfig,
    boolean enableAutoMultiAddress,
    String clientCertId,
    PayloadMode payloadMode,
    String payloadTemplate,
    TaskConfig.QpsMode qpsMode,
    TaskConfig.ProfileConfig profileConfig,
    WaveQpsSpec waveQpsSpec,
    WaveQpsSpec connectWaveQpsSpec,
    WaveQpsSpec disconnectWaveQpsSpec,
    ChaosPolicy chaosPolicy,
    String connectProfileId,
    String disconnectProfileId,
    TaskConfig.SubscribeQpsMode subscribeQpsMode,
    int subscribeRate,
    String subscribeProfileId
) {
    public TaskSpec {
        localAddresses = localAddresses == null ? new ArrayList<>() : localAddresses;
        brokers = brokers == null ? new ArrayList<>() : brokers;
    }
}
