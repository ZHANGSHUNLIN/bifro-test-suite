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

package org.apache.bifromq.testsuite.worker.context;

import io.netty.handler.codec.mqtt.MqttQoS;
import java.util.List;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.client.MqttClientImpl;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.qps.ProfileQpsSpec;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;
import org.apache.bifromq.testsuite.worker.TaskBroker;

public record TaskExecutionConfig(
    String taskId,
    String nodeId,
    String taskTypeName,
    TaskTemplate template,
    List<TaskBroker> brokers,
    int messageSize,
    double publishRate,
    boolean mqtt5,
    boolean retain,
    MqttQoS qos,
    MqttClientImpl clientImpl,
    PayloadMode payloadMode,
    String payloadTemplate,
    WaveQpsSpec clientPublishWaveQpsSpec,
    ProfileQpsSpec clientPublishProfileQpsSpec,
    org.apache.bifromq.testsuite.chaos.ChaosPolicy chaosPolicy,
    boolean publishOnConnect,
    String topic,
    int topicsPerClient,
    boolean wildcard,
    int subWorkerPoolSize,
    Long plannedStartAtMs,
    List<long[]> connectProfileDataPoints,
    WaveQpsSpec connectWaveQpsSpec,
    List<long[]> disconnectProfileDataPoints,
    WaveQpsSpec disconnectWaveQpsSpec,
    List<long[]> subscribeProfileDataPoints
) {
}
