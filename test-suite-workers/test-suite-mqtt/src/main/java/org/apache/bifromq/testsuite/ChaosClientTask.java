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

import org.apache.bifromq.testsuite.chaos.ChaosBehavior;
import org.apache.bifromq.testsuite.chaos.ChaosBehavior.BrokerReaction;
import org.apache.bifromq.testsuite.chaos.ChaosContext;
import org.apache.bifromq.testsuite.chaos.ChaosPolicy;
import org.apache.bifromq.testsuite.chaos.RawMqttConnection;
import org.apache.bifromq.testsuite.chaos.VertxRawMqttConnection;
import org.apache.bifromq.testsuite.chaos.behaviors.DoubleConnectBehavior;
import org.apache.bifromq.testsuite.chaos.behaviors.DuplicatePubackBehavior;
import org.apache.bifromq.testsuite.chaos.behaviors.ExceedInflightWindowBehavior;
import org.apache.bifromq.testsuite.chaos.behaviors.InvalidPacketIdZeroBehavior;
import org.apache.bifromq.testsuite.chaos.behaviors.MalformedTopicBehavior;
import org.apache.bifromq.testsuite.chaos.behaviors.OversizedPayloadBehavior;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.constants.ConnectionStatus;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import io.micrometer.core.instrument.Tags;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChaosClientTask {

    private static final Map<String, ChaosBehavior> BEHAVIOR_REGISTRY = Map.of(
        "DUPLICATE_PUBACK", new DuplicatePubackBehavior(),
        "EXCEED_INFLIGHT_WINDOW", new ExceedInflightWindowBehavior(),
        "DOUBLE_CONNECT", new DoubleConnectBehavior(),
        "INVALID_PACKET_ID_ZERO", new InvalidPacketIdZeroBehavior(),
        "OVERSIZED_PAYLOAD", new OversizedPayloadBehavior(),
        "MALFORMED_TOPIC", new MalformedTopicBehavior()
    );

    private final Vertx vertx;
    private final ClientTaskConfig taskConfig;
    private final MqttClientConfig clientConfig;
    private final ChaosPolicy chaosPolicy;
    private final List<ChaosBehavior> behaviors;
    private final AtomicReference<TaskStage> taskStage;
    private final AtomicInteger packetIdCounter = new AtomicInteger(1);

    private RawMqttConnection connection;

    public ChaosClientTask(Vertx vertx,
                           ClientTaskConfig taskConfig,
                           MqttClientConfig clientConfig,
                           AtomicReference<TaskStage> taskStage) {
        this.vertx = vertx;
        this.taskConfig = taskConfig;
        this.clientConfig = clientConfig;
        this.taskStage = taskStage;
        this.chaosPolicy = taskConfig.getChaosPolicy();
        this.behaviors = resolveBehaviors(chaosPolicy);
    }

    
    
    

    public CompletableFuture<Void> connect() {
        connection = new VertxRawMqttConnection(vertx);
        return connection.connect(
                clientConfig.getHost(),
                clientConfig.getPort(),
                clientConfig.getClientId(),
                taskConfig.getStageTimeoutInSec())
            .thenAccept(returnCode -> {
                if (returnCode != 0) {
                    throw new RuntimeException("CONNACK failed, returnCode=" + returnCode);
                }
                log.debug("[Chaos] Connected: clientId={}", clientConfig.getClientId());
            });
    }

    public CompletableFuture<Void> executeChaos() {
        if (behaviors.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ChaosBehavior behavior : behaviors) {
            int packetId = packetIdCounter.getAndAdd(10); 

            int effectiveMaxInflight = chaosPolicy.getMaxInflight();
            int effectiveMaxPacket = chaosPolicy.getMaxPacketSizeOverride() > 0
                ? chaosPolicy.getMaxPacketSizeOverride() : 65536;

            ChaosContext ctx = ChaosContext.builder()
                .connection(connection)
                .startPacketId(packetId)
                .topic(taskConfig.getPubTopic() != null ? taskConfig.getPubTopic() : "chaos/test")
                .maxInflightWindow(effectiveMaxInflight)
                .maxPacketSize(effectiveMaxPacket)
                .build();

            CompletableFuture<Void> f = behavior.execute(ctx)
                .thenAccept(reaction -> recordReaction(behavior.name(), reaction))
                .exceptionally(ex -> {
                    log.warn("[Chaos] behavior={} threw: {}", behavior.name(), ex.getMessage());
                    return null;
                });
            futures.add(f);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public CompletableFuture<Void> close() {
        if (connection == null) {
            return CompletableFuture.completedFuture(null);
        }
        return connection.close();
    }

    public String getClientId() {
        return clientConfig.getClientId();
    }

    public ConnectionStatus getConnectionStatus() {
        return connection != null ? ConnectionStatus.CONNECTED : ConnectionStatus.DISCONNECTED;
    }

    public OptionalLong getMessageCount() {
        return OptionalLong.empty();
    }

    
    
    

    private List<ChaosBehavior> resolveBehaviors(ChaosPolicy policy) {
        List<ChaosBehavior> result = new ArrayList<>();
        if (policy == null || policy.getBehaviors() == null) {
            return result;
        }
        for (String name : policy.getBehaviors()) {
            ChaosBehavior b = BEHAVIOR_REGISTRY.get(name);
            if (b != null) {
                result.add(b);
            } else {
                log.warn("[Chaos] Unknown behavior name: '{}', skipped", name);
            }
        }
        return result;
    }

    private void recordReaction(String behaviorName, BrokerReaction reaction) {
        log.info("[Chaos] clientId={} behavior={} brokerReaction={}",
            clientConfig.getClientId(), behaviorName, reaction);
        MetricsHelper.counter(BifroTaskMetric.CHAOS_BEHAVIOR_COUNT,
            Tags.of(
                "taskId", taskConfig.getTaskId(),
                "nodeId", taskConfig.getNodeId(),
                "behavior", behaviorName,
                "brokerReaction", reaction.name()
            ));
    }
}
