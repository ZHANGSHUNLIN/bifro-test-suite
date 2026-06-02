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

import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.worker.TaskConfig;

public final class TaskSpecMapper {

    private TaskSpecMapper() {
    }

    public static TaskConfig toTaskConfig(TaskSpec spec) {
        TaskConfig config = new TaskConfig();
        config.setTaskType(spec.taskType());
        config.setTemplate(spec.template());
        config.setProtocol(spec.protocol());
        config.setBrokers(spec.brokers());
        config.setUsername(spec.username());
        config.setPassword(spec.password());
        config.setThingIdStartAt(spec.thingIdStartAt());
        config.setCleanSession(spec.cleanSession());
        config.setLocalAddresses(spec.localAddresses());
        config.setKeepAliveInSec(spec.keepAliveInSec());
        config.setAckTimeoutInSec(spec.ackTimeoutInSec());
        config.setReconnectMaxAttempts(spec.reconnectMaxAttempts());
        config.setReconnectIntervalInMs(spec.reconnectIntervalInMs());
        config.setConnectTimeoutInMs(spec.connectTimeoutInMs());
        config.setMaxInflightQueue(spec.maxInflightQueue());
        config.setTotalClientCount(spec.totalClientCount());
        config.setFanOut(spec.fanOut());
        config.setFanIn(spec.fanIn());
        config.setTopicsPerClient(spec.topicsPerClient() > 0 ? spec.topicsPerClient() : 1);
        config.setTopic(spec.topic());
        config.setQos(spec.qos());
        config.setFixedTopic(spec.fixedTopic());
        config.setWildcard(spec.wildcard());
        config.setMessageSize(spec.messageSize());
        config.setPublishRate(spec.publishRate());
        config.setStressDurationInSec(spec.stressDurationInSec());
        config.setStageTimeoutInSec(spec.stageTimeoutInSec());
        config.setDelayAfterStageInSec(spec.delayAfterStageInSec());
        config.setRetain(spec.retain());
        config.setMqtt5(spec.mqtt5());
        config.setAuthType(spec.authType());
        config.setEmptyClientId(spec.emptyClientId());
        config.setExpiryIntervalInSec(spec.expiryIntervalInSec());
        config.setConnectRate(spec.connectRate());
        config.setDisconnectRate(spec.disconnectRate());
        config.setGroup(spec.group());
        config.setWillConfig(spec.willConfig());
        config.setEnableAutoMultiAddress(spec.enableAutoMultiAddress());
        config.setClientCertId(spec.clientCertId());
        config.setPayloadMode(spec.payloadMode() != null ? spec.payloadMode() : PayloadMode.BIFRO);
        config.setPayloadTemplate(spec.payloadTemplate());
        config.setQpsMode(spec.qpsMode() != null ? spec.qpsMode() : TaskConfig.QpsMode.FIXED);
        config.setProfileConfig(spec.profileConfig());
        config.setWaveQpsSpec(spec.waveQpsSpec());
        config.setConnectWaveQpsSpec(spec.connectWaveQpsSpec());
        config.setDisconnectWaveQpsSpec(spec.disconnectWaveQpsSpec());
        config.setChaosPolicy(spec.chaosPolicy());
        config.setConnectProfileId(spec.connectProfileId());
        config.setDisconnectProfileId(spec.disconnectProfileId());
        config.setSubscribeQpsMode(spec.subscribeQpsMode() != null
            ? spec.subscribeQpsMode() : TaskConfig.SubscribeQpsMode.FIXED);
        config.setSubscribeRate(spec.subscribeRate());
        config.setSubscribeProfileId(spec.subscribeProfileId());
        return config;
    }
}
