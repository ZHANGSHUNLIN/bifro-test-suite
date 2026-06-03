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
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.WillConfig;
import org.apache.bifromq.testsuite.client.AuthType;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.client.MqttClientImpl;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.qps.ProfileQpsSpec;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @Builder.Default
    private TaskStage taskWorkStage = TaskStage.INIT;
    private TaskTemplate template;
    private String taskId;
    @Builder.Default
    private String nodeId = "";
    private TaskType taskType;
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
    @Builder.Default
    private boolean fixedTopic = false;
    private boolean isWildcard;
    @Builder.Default
    private int messageSize = 32;
    @Builder.Default
    private double publishRate = 1.0;
    private int stressDurationInSec;
    @Builder.Default
    private int stageTimeoutInSec = 30;
    @Builder.Default
    private int delayAfterStageInSec = 30;
    @Builder.Default
    private boolean retain = false;
    @Builder.Default
    private boolean isMqtt5 = false;
    private MqttClientImpl clientImpl;
    private AuthType authType;
    @Builder.Default
    private boolean isEmptyClientId = false;
    @Builder.Default
    private long expiryIntervalInSec = 120;
    @Builder.Default
    private int connectRate = 500;
    @Builder.Default
    private int disconnectRate = 500;
    @Builder.Default
    private RateLimiterType rateLimiterType = RateLimiterType.GUAVA;
    @Builder.Default
    private boolean enableAutoMultiAddress = false;
    @Builder.Default
    private LocalPortRangeConfig localPortRangeConfig = new LocalPortRangeConfig();
    @Builder.Default
    private String group = "";
    @Builder.Default
    private WillConfig willConfig = new WillConfig();
    @Builder.Default
    private int subWorkerPoolSize = Runtime.getRuntime().availableProcessors() * 2;
    private String clientCertId;
    private String caCertPem;
    private String clientCertPem;
    private String clientKeyPem;
    @Builder.Default
    private PayloadMode payloadMode = PayloadMode.BIFRO;
    private String payloadTemplate;
    @Builder.Default
    private QpsMode qpsMode = QpsMode.FIXED;
    private ProfileConfig profileConfig;
    private WaveQpsSpec waveQpsSpec;
    private WaveQpsSpec connectWaveQpsSpec;
    private WaveQpsSpec disconnectWaveQpsSpec;
    private org.apache.bifromq.testsuite.chaos.ChaosPolicy chaosPolicy;
    private String connectProfileId;
    private java.util.List<long[]> connectProfileDataPoints;
    private String disconnectProfileId;
    private java.util.List<long[]> disconnectProfileDataPoints;
    @Builder.Default
    private SubscribeQpsMode subscribeQpsMode = SubscribeQpsMode.FIXED;
    @Builder.Default
    private int subscribeRate = 0;
    private String subscribeProfileId;
    private java.util.List<long[]> subscribeProfileDataPoints;
    private java.util.List<long[]> publishProfileDataPoints;
    @Builder.Default
    private int nodePubCount = -1;
    @Builder.Default
    private int nodeSubCount = -1;

    public enum SubscribeQpsMode {
        FIXED,
        DYNAMIC
    }

    public enum TaskType {
        CONN,
        PUBSUB,
        CHAOS
    }

    public enum QpsMode {
        FIXED,
        WAVE,
        DYNAMIC
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileConfig implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String profileId;
        @Builder.Default
        private ProfileQpsSpec.EndBehavior endBehavior = ProfileQpsSpec.EndBehavior.LOOP;
        private java.util.List<long[]> dataPoints;
        private long totalDurationMs;
    }
}
