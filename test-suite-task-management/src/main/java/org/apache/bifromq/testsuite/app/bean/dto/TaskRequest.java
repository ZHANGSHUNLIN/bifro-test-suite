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

package org.apache.bifromq.testsuite.app.bean.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.handler.codec.mqtt.MqttQoS;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.apache.bifromq.testsuite.app.task.config.TaskSpec;
import org.apache.bifromq.testsuite.app.task.config.TaskSpecMapper;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.WillConfig;
import org.apache.bifromq.testsuite.client.AuthType;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;
import org.apache.bifromq.testsuite.worker.TaskBroker;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.TaskConfig.TaskType;
import lombok.Data;

@Data
public class TaskRequest {

    private String taskName;

    
    @NotNull(message = "{validation.task.type.notNull}")
    private TaskType taskType;

    
    @NotBlank(message = "{validation.task.template.notBlank}")
    private String template;

    
    @NotBlank(message = "{validation.task.group.notBlank}")
    private String group;

    
    @NotBlank(message = "{validation.task.protocol.notBlank}")
    private String protocol = "tcp";

    
    private boolean autoMultiAddress = false;

    
    private List<String> localAddresses = new ArrayList<>();

    
    @NotEmpty(message = "{validation.task.hostList.notEmpty}")
    private List<BrokerEntry> brokers = new ArrayList<>();

    
    @Min(value = 1, message = "{validation.port.min}")
    @Max(value = 65535, message = "{validation.port.max}")
    private int port = 1883;

    private String username = "";

    
    private String password = "";

    
    private String tenantId;

    
    @Min(value = 0, message = "{validation.task.thingIdOffset.min}")
    private int thingIdStartAt = 0;

    
    private String thingIdPrefix;

    
    private boolean cleanSession = true;

    
    @Min(value = 0, message = "{validation.task.keepAlive.min}")
    private int keepAliveInSec = 120;

    
    @Min(value = 0, message = "{validation.task.ackTimeout.min}")
    private int ackTimeoutInSec = 120;

    
    @Min(value = 0, message = "{validation.task.maxReconnect.min}")
    private int reconnectMaxAttempts = 2;

    
    @Min(value = 0, message = "{validation.task.reconnectInterval.min}")
    private int reconnectIntervalInMs = 5000;

    
    @Min(value = 0, message = "{validation.task.ackTimeout.min}")
    private int connectTimeoutInMs = 10000;

    
    @Min(value = 0, message = "{validation.port.min}")
    private int maxInflightQueue = 200;

    
    @Min(value = 1, message = "{validation.port.min}")
    private int totalClientCount = 1;

    
    @Min(value = 1, message = "{validation.port.min}")
    private int fanOut = 1;

    
    @Min(value = 1, message = "{validation.port.min}")
    private int fanIn = 1;

    @Min(value = 1, message = "{validation.port.min}")
    private int topicsPerClient = 1;

    private String topic;

    
    private MqttQoS qos = MqttQoS.AT_MOST_ONCE;

    
    private boolean fixedTopic = false;

    
    private boolean isWildcard = false;

    
    @Min(value = 1, message = "{validation.port.min}")
    private int messageSize = 32;

    
    @DecimalMin(value = "0.001", message = "{validation.task.thingIdOffset.min}")
    private double publishRate = 1.0;

    
    @Min(value = 0, message = "{validation.task.thingIdOffset.min}")
    private int stressDurationInSec = 60;

    
    @Min(value = 0, message = "{validation.task.ackTimeout.min}")
    private int stageTimeoutInSec = 30;

    
    @Min(value = 0, message = "{validation.task.thingIdOffset.min}")
    private int delayAfterStageInSec = 30;

    
    private boolean retain = false;

    
    @JsonProperty("mqtt5")
    @JsonAlias("isMqtt5")
    private boolean isMqtt5 = false;

    
    private AuthType authType = AuthType.NONE;

    
    private boolean clientCertEnabled = false;

    
    private String clientCertId;

    
    @JsonProperty("emptyClientId")
    @JsonAlias("isEmptyClientId")
    private boolean isEmptyClientId = false;

    
    @Min(value = 0, message = "{validation.task.keepAlive.min}")
    private long expiryIntervalInSec = 120;

    @Min(value = 0, message = "{validation.task.keepAlive.min}")
    private int connectRate = 500;

    @Min(value = 0, message = "{validation.task.keepAlive.min}")
    private int disconnectRate = 500;

    private WillConfig willConfig = new WillConfig();

    
    private PayloadMode payloadMode = PayloadMode.BIFRO;

    
    private String payloadTemplate;

    
    private WaveQpsSpec waveQpsSpec;

    private TaskConfig.QpsMode qpsMode = TaskConfig.QpsMode.FIXED;

    private TaskConfig.ProfileConfig profileConfig;

    
    private WaveQpsSpec connectWaveQpsSpec;

    
    private WaveQpsSpec disconnectWaveQpsSpec;

    
    private org.apache.bifromq.testsuite.chaos.ChaosPolicy chaosPolicy;

    

    
    private String connectProfileId;

    
    private String disconnectProfileId;

    

    
    private TaskConfig.SubscribeQpsMode subscribeQpsMode = TaskConfig.SubscribeQpsMode.FIXED;

    
    private int subscribeRate = 0;

    
    private String subscribeProfileId;

    public TaskSpec toTaskSpec() {
        return TaskSpec.builder()
            .taskType(this.taskType)
            .template(this.template == null ? null : TaskTemplate.valueOf(this.template))
            .protocol(this.protocol)
            .brokers(this.brokers.stream()
                .map(r -> TaskBroker.builder().host(r.getHost()).port(r.getPort()).build()).toList())
            .username(this.username)
            .password(this.password)
            .tenantId(this.tenantId)
            .thingIdStartAt(this.thingIdStartAt)
            .thingIdPrefix(this.thingIdPrefix)
            .cleanSession(this.cleanSession)
            .localAddresses(this.localAddresses)
            .keepAliveInSec(this.keepAliveInSec)
            .ackTimeoutInSec(this.ackTimeoutInSec)
            .reconnectMaxAttempts(this.reconnectMaxAttempts)
            .reconnectIntervalInMs(this.reconnectIntervalInMs)
            .connectTimeoutInMs(this.connectTimeoutInMs)
            .maxInflightQueue(this.maxInflightQueue)
            .totalClientCount(this.totalClientCount)
            .fanOut(this.fanOut)
            .fanIn(this.fanIn)
            .topicsPerClient(this.topicsPerClient)
            .topic(this.topic)
            .qos(this.qos)
            .fixedTopic(this.fixedTopic)
            .wildcard(this.isWildcard)
            .messageSize(this.messageSize)
            .publishRate(this.publishRate)
            .stressDurationInSec(this.stressDurationInSec)
            .stageTimeoutInSec(this.stageTimeoutInSec)
            .delayAfterStageInSec(this.delayAfterStageInSec)
            .retain(this.retain)
            .mqtt5(this.isMqtt5)
            .authType(this.authType)
            .emptyClientId(this.isEmptyClientId)
            .expiryIntervalInSec(this.expiryIntervalInSec)
            .connectRate(this.connectRate)
            .disconnectRate(this.disconnectRate)
            .group(this.group)
            .willConfig(this.willConfig)
            .enableAutoMultiAddress(this.autoMultiAddress)
            .clientCertId(this.clientCertEnabled ? this.clientCertId : null)
            .payloadMode(this.payloadMode)
            .payloadTemplate(this.payloadTemplate)
            .qpsMode(this.qpsMode)
            .profileConfig(this.profileConfig)
            .waveQpsSpec(this.waveQpsSpec)
            .connectWaveQpsSpec(this.connectWaveQpsSpec)
            .disconnectWaveQpsSpec(this.disconnectWaveQpsSpec)
            .chaosPolicy(this.chaosPolicy)
            .connectProfileId(this.connectProfileId)
            .disconnectProfileId(this.disconnectProfileId)
            .subscribeQpsMode(this.subscribeQpsMode)
            .subscribeRate(this.subscribeRate)
            .subscribeProfileId(this.subscribeProfileId)
            .build();
    }

    public TaskConfig toTaskConfig() {
        return TaskSpecMapper.toTaskConfig(toTaskSpec());
    }
}
