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

package org.apache.bifromq.testsuite.configs;

import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.bifromq.testsuite.client.AuthType;
import org.apache.bifromq.testsuite.constants.ClientTaskType;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.models.TopicFilter;
import org.apache.bifromq.testsuite.qps.ProfileQpsSpec;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientTaskConfig {
    private String taskId;
    @Builder.Default
    private String nodeId = "";
    private AuthType authType;
    private ClientTaskType type;
    private String pubTopic;
    private List<String> pubTopics;
    private MqttQoS messageQos;
    private int messageSize;
    private Set<TopicFilter> topicFilters;
    private double publishRate;
    private int stressDurationInSec;
    private int stageTimeoutInSec;
    private boolean retain;
    private boolean isMqtt5;
    private org.apache.bifromq.testsuite.client.MqttClientImpl clientImpl;
    @Builder.Default
    private boolean isEmptyClientId = false;
    @Builder.Default
    private boolean sendLatencyEvent = false;
    @Builder.Default
    private boolean randomPublishing = false;
    @Builder.Default
    private PayloadMode payloadMode = PayloadMode.BIFRO;
    private String payloadTemplate;
    private WaveQpsSpec waveQpsSpec;
    private ProfileQpsSpec profileQpsSpec;
    private org.apache.bifromq.testsuite.chaos.ChaosPolicy chaosPolicy;
    @Builder.Default
    private boolean publishOnConnect = false;
}
