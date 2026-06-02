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

package org.apache.bifromq.testsuite;

import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.constants.ConnectionStatus;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.payload.PayloadStrategy;
import org.apache.bifromq.testsuite.payload.PayloadStrategyFactory;
import io.micrometer.core.instrument.Tags;
import io.vertx.core.Vertx;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnMqttClientTask extends MqttClientTask {

    
    private final PayloadStrategy payloadStrategy;
    private final AtomicLong pubCount = new AtomicLong(0);

    @Builder
    public ConnMqttClientTask(@NonNull Vertx vertx,
                              @NonNull ClientTaskConfig taskConfig,
                              @NonNull MqttClientConfig mqttClientConfig,
                              AtomicReference<TaskStage> taskStage) {
        super(vertx, taskConfig, mqttClientConfig, taskStage);
        this.payloadStrategy = taskConfig.isPublishOnConnect()
            ? PayloadStrategyFactory.create(
            taskConfig.getPayloadMode(),
            taskConfig.getPayloadTemplate(),
            mqttClientConfig.getClientId(),
            taskConfig.getTaskId())
            : null;
    }

    @Override
    public CompletableFuture<Void> connect() {
        if (payloadStrategy == null) {
            return connectWrapper(connectionStatus -> wrapClientContext("StartConnClients", () -> {
            }).run());
        }
        return connectWrapper(connectionStatus -> {
            wrapClientContext("StartConnClients", () -> {
                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    publishOnce();
                }
            }).run();
        });
    }

    private void publishOnce() {
        String taskId = taskConfig.getTaskId();
        String nodeId = taskConfig.getNodeId();
        String clientId = mqttClientWrapper.getClientId();
        Tags tags = Tags.of("taskId", taskId, "nodeId", nodeId);

        byte[] payload = payloadStrategy.buildPayload(
            pubCount.get(), taskConfig.getMessageSize());
        String topic = taskConfig.getPubTopic();
        int qos = taskConfig.getMessageQos().value();

        log.debug("publish-on-connect: clientId={}, topic={}, payloadLen={}",
            clientId, topic, payload.length);

        mqttClientWrapper.publish(payload, topic, qos, false, taskConfig.isRetain())
            .whenComplete((v, e) -> wrapClientContext("StartConnClients", () -> {
                if (e != null) {
                    log.warn("publish-on-connect failed, taskId={}, clientId={}: {}",
                        taskId, clientId, e.getMessage());
                    MetricsHelper.counter(BifroTaskMetric.PUBLISH_FAILURE_COUNT, tags);
                } else {
                    pubCount.incrementAndGet();
                    MetricsHelper.counter(BifroTaskMetric.PUBLISH_COMPLETION_COUNT, tags);
                    MetricsHelper.counter(BifroTaskMetric.THROUGHPUT_MESSAGES, tags);
                    MetricsHelper.counter(BifroTaskMetric.THROUGHPUT_BYTES,
                        payload.length, "taskId", taskId, "nodeId", nodeId);
                    log.debug("publish-on-connect succeeded, clientId={}", clientId);
                }
            }).run());
    }
}
