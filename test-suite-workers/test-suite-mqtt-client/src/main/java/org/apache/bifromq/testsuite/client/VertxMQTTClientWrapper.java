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
/*
 * Copyright (C) 2024 Baidu, Inc. All Rights Reserved.
 */

package org.apache.bifromq.testsuite.client;

import static org.apache.bifromq.testsuite.constants.ConnectionStatus.CLOSED;
import static org.apache.bifromq.testsuite.constants.ConnectionStatus.DISCONNECTED;

import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.WillConfig;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.constants.ConnectionStatus;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.models.TopicFilter;
import org.apache.bifromq.testsuite.utils.ConnectionUtil;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class VertxMQTTClientWrapper extends BaseMQTTClientWrapper {

    private final MqttClientOptions mqttOptions;
    private final EventLoop eventLoop;
    private volatile Map<Integer, CompletableFuture<List<Integer>>> inflightSubs;
    private volatile Map<Integer, CompletableFuture<Void>> inflightPubs;
    private volatile Set<Integer> pubAckCache;
    private volatile Map<Integer, io.micrometer.core.instrument.Timer.Sample> publishLatencySamples;
    private volatile Map<Integer, io.micrometer.core.instrument.Timer.Sample> subscribeLatencySamples;
    private MqttClient mqttClient;

    public VertxMQTTClientWrapper(@NonNull Vertx vertx,
                                  @NonNull ClientTaskConfig taskConfig,
                                  @NonNull MqttClientConfig mqttClientConfig,
                                  @NonNull EventLoop eventLoop, AtomicReference<TaskStage> taskStage) {
        super(vertx, mqttClientConfig, taskConfig, taskStage);
        this.eventLoop = eventLoop;
        log.debug("mqtt client config, {}", clientConfig);

        mqttOptions = new MqttClientOptions()
            .setClientId(clientConfig.getClientId())
            .setUsername(clientConfig.getUsername())
            .setPassword(clientConfig.getPassword())
            .setMaxInflightQueue(clientConfig.getMaxInflightQueue())
            .setAckTimeout(clientConfig.getAckTimeoutInSec())
            .setCleanSession(clientConfig.isCleanSession())
            .setMaxMessageSize(Math.max(512, taskConfig.getMessageSize() * 2))
            .setKeepAliveInterval(clientConfig.getKeepAliveInSec())
        ;

        WillConfig willConfig = clientConfig.getWillConfig();
        if (willConfig.getWillFlag()) {
            String willMessage = willConfig.getWillMessage();
            Buffer lastMsg;
            if (StringUtils.isNotBlank(willMessage)) {
                lastMsg = Buffer.buffer(willMessage);
            } else if (willConfig.getWillMessageLen() != null && willConfig.getWillMessageLen() > 0) {
                lastMsg = Buffer.buffer(new byte[willConfig.getWillMessageLen()]);
            } else {
                lastMsg = Buffer.buffer(new byte[0]);
            }
            log.trace("willConfig={}", willConfig);
            mqttOptions.setWillFlag(true)
                .setWillTopic(willConfig.getWillTopic())
                .setWillMessageBytes(lastMsg)
                .setWillQoS(willConfig.getWillQos())
                .setWillRetain(willConfig.getWillRetain());
        }

        mqttOptions.setConnectTimeout(clientConfig.getConnectTimeoutInMs())
            .setReconnectAttempts(clientConfig.getReconnectMaxAttempts())
            .setReconnectInterval(clientConfig.getReconnectIntervalInMs())
            .setTcpKeepAlive(true)
            .setReuseAddress(true)
            .setLocalAddress(clientConfig.getLocalAddress());
        if (ConnectionUtil.isSSL(clientConfig.getProtocol())) {
            mqttOptions.setSsl(true);
            mqttOptions.setTrustAll(true);
            
        }
    }

    @Override
    public String getClientId() {
        return mqttOptions.getClientId();
    }

    @Override
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    @Override
    public ConnectionStatus getStatus() {
        return this.status;
    }

    @Override
    public CompletableFuture<Void> close() {
        this.status = ConnectionStatus.CLOSED;
        cancelReconnectTimer();
        cancelPendingOperations(new CancellationException("pending operations cancelled when client closed"));
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        if (mqttClient != null) {
            if (mqttClient.isConnected()) {
                recordCloseSuccess();
                this.mqttClient.disconnect().onComplete(event -> {
                    closeFuture.complete(null);
                    if (event.failed()) {
                        log.error("Failed to close mqtt client, clientId={}, ",
                            mqttClient.clientId(), event.cause());
                    }
                });
            } else {
                
                recordCloseFailure();
                closeFuture.complete(null);
            }
        } else {
            recordCloseFailure();
            closeFuture.complete(null);
        }
        return closeFuture;
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        if (mqttClient != null) {
            return mqttClient.disconnect().toCompletionStage().toCompletableFuture()
                .exceptionally(e -> {
                    log.error("Failed to disconnect mqtt client, clientId={}, ", mqttClient.clientId(), e);
                    recordDisconnectFailure();
                    return null;
                });
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Integer>> subscribe(Set<TopicFilter> topicFilters) {
        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        if (!isConnectedState()) {
            SUB_LOGGER.warn("Subscribe cancelled for mqttClient is not connected, clientId={}",
                clientConfig.getClientId());
            future.completeExceptionally(new RuntimeException("mqttClient is not connected"));
            incrementMetric(BifroTaskMetric.SUBSCRIBE_COMPLETION_EXPIRATION_COUNT);
            return future;
        }
        mqttClient.subscribe(
            topicFilters.stream().collect(Collectors.toMap(TopicFilter::getName, tf -> tf.getQos().value()))
        ).onComplete(subPacket -> {
            if (subPacket.failed()) {
                future.completeExceptionally(subPacket.cause());
            } else {
                
                subscribeLatencySamples().put(subPacket.result(), MetricsHelper.startTimer());
                inflightSubs().put(subPacket.result(), future);
                subscribedTopicFilter.addAll(topicFilters);
            }
        });
        return future;
    }

    public CompletableFuture<Void> unsubscribeAll() {
        CompletableFuture<Void> unsubFuture = new CompletableFuture<>();
        if (!isConnectedState()) {
            log.warn("Unsubscribe cancelled for mqttClient is not connected, clientId={}", clientConfig.getClientId());
            unsubFuture.completeExceptionally(new RuntimeException("mqttClient is not connected"));
            recordUnsubscribeFailure();
            return unsubFuture;
        }
        List<String> topics = subscribedTopicFilter.stream()
            .map(TopicFilter::getName)
            .collect(Collectors.toList());
        mqttClient.unsubscribe(topics).onComplete(event -> {
            log.info("unsub the topics: {}, ok: {}", topics, event.succeeded());
            subscribedTopicFilter.clear();
            recordUnsubscribeSuccess();
            unsubFuture.complete(null);
        });
        return unsubFuture;
    }

    @Override
    public CompletableFuture<Void> publish(byte[] payload, String topic, int qos, boolean isDup, boolean isRetain) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (isConnectedState()) {
            io.micrometer.core.instrument.Timer.Sample pubSample = MetricsHelper.startTimer();
            mqttClient.publish(topic, Buffer.buffer(payload), MqttQoS.valueOf(qos),
                isDup, isRetain
            ).onComplete(pubResult -> {
                if (pubResult.succeeded()) {
                    
                    if (MqttQoS.AT_MOST_ONCE.equals(MqttQoS.valueOf(qos))) {
                        
                        MetricsHelper.stopTimer(pubSample, BifroTaskMetric.PUBLISH_LATENCY,
                            "taskId", taskConfig.getTaskId(), "nodeId", taskConfig.getNodeId(), "qos", "0");
                        result.complete(null);
                    } else {
                        publishLatencySamples().put(pubResult.result(), pubSample);
                        boolean exist = pubAckCache().remove(pubResult.result());
                        if (exist) {
                            log.info("Handle ack earlier than tcp ack, clientId={}", mqttClient.clientId());
                            publishLatencySamples().remove(pubResult.result());
                            MetricsHelper.stopTimer(pubSample, BifroTaskMetric.PUBACK_LATENCY,
                                "taskId", taskConfig.getTaskId(), "nodeId", taskConfig.getNodeId());
                            result.complete(null);
                        } else {
                            inflightPubs().put(pubResult.result(), result);
                        }
                    }
                } else {
                    result.completeExceptionally(new RuntimeException(pubResult.cause()));
                }
            });
        } else {
            result.completeExceptionally(new RuntimeException("Client not connected!"));
            recordPublishFailure("not connected");
        }
        return result;
    }

    @Override
    public CompletableFuture<Void> internalConnect() {
        TaskStage stage = taskStage.get();
        long start = System.currentTimeMillis();
        if (!canConnect(stage)) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> connFuture = new CompletableFuture<>();

        if (trySetReconnecting()) {
            
            status = ConnectionStatus.CONNECTING;
            this.eventLoop.execute(() -> {
                AtomicBoolean connectAttemptHandled = new AtomicBoolean(false);

                MqttClient previousClient = this.mqttClient;
                if (previousClient != null) {
                    previousClient.closeHandler(ignored -> {
                    });
                    cancelPendingOperations(new CancellationException(
                        "pending operations cancelled before reconnect"));
                }

                mqttClient = MqttClient.create(vertx, mqttOptions);
                mqttClient.closeHandler(xVoid -> {
                    logDisconnect();
                    cancelPendingOperations(new CancellationException(
                        "pending operations cancelled when connection closed"));
                    recordDisconnect();
                    if (status == CLOSED) {
                        return;
                    }
                    
                    
                    
                    if (status == ConnectionStatus.CONNECTING) {
                        if (connectAttemptHandled.compareAndSet(false, true)) {
                            RuntimeException cause = new RuntimeException("Connection closed during connect");
                            logConnectFailure(System.currentTimeMillis() - start, cause);
                            recoverAfterConnectFailure(cause).whenComplete((v, throwable) -> {
                                if (throwable != null) {
                                    connFuture.completeExceptionally(throwable);
                                } else {
                                    connFuture.complete(null);
                                }
                            });
                        }
                        return;
                    }
                    status = DISCONNECTED;
                    connectCallback.accept(DISCONNECTED);
                    tryRecoverConnect();
                });
                mqttClient.exceptionHandler(error -> {
                    incrementMetric(BifroTaskMetric.CONNECT_EXCEPTION_COUNT);
                    log.error("MqttClient exception, id={}", mqttClient.clientId(), error);
                });
                mqttClient.subscribeCompletionHandler(subAckMessage -> {
                        recordSubscribeSuccess(subAckMessage.grantedQoSLevels().size());
                        
                        io.micrometer.core.instrument.Timer.Sample subSample =
                            removeSubscribeLatencySample(subAckMessage.messageId());
                        if (subSample != null) {
                            MetricsHelper.stopTimer(subSample, BifroTaskMetric.SUBSCRIBE_LATENCY,
                                "taskId", taskConfig.getTaskId(), "nodeId", taskConfig.getNodeId());
                        }
                        completeSubscription(subAckMessage.messageId(), subAckMessage.grantedQoSLevels());
                    }
                );
                mqttClient.publishHandler(
                    mqttPublishMessage -> {

                        this.pubMsgListener.onPublishMessage(mqttPublishMessage.payload().getBytes(),
                            mqttPublishMessage.isDup());
                });
                mqttClient.publishCompletionHandler(pubAckPacketId -> {
                    CompletableFuture<Void> pendingResult = removeInflightPub(pubAckPacketId);
                    if (pendingResult != null) {
                        pendingResult.complete(null);
                    } else {
                        pubAckCache().add(pubAckPacketId);
                    }
                    incrementMetric(BifroTaskMetric.PUBLISH_COUNT);
                    
                    io.micrometer.core.instrument.Timer.Sample pubSample =
                        removePublishLatencySample(pubAckPacketId);
                    if (pubSample != null) {
                        MetricsHelper.stopTimer(pubSample, BifroTaskMetric.PUBACK_LATENCY,
                            "taskId", taskConfig.getTaskId(), "nodeId", taskConfig.getNodeId());
                    }

                });
                mqttClient.publishCompletionExpirationHandler(pubPacketId -> {
                    CompletableFuture<Void> pendingResult = removeInflightPub(pubPacketId);
                    removePublishLatencySample(pubPacketId);
                    if (pendingResult != null) {
                        pendingResult.completeExceptionally(
                            new RuntimeException("PubAck has not arrive in ack Timeout"));
                    }
                    incrementMetric(BifroTaskMetric.PUBLISH_COMPLETION_EXPIRATION_COUNT);
                    log.warn("PubAck has not arrive in ack Timeout, pubPacketId={}", pubPacketId);
                });
                mqttClient.publishCompletionUnknownPacketIdHandler(pubPacketId -> {
                    CompletableFuture<Void> pendingResult = removeInflightPub(pubPacketId);
                    removePublishLatencySample(pubPacketId);
                    if (pendingResult != null) {
                        pendingResult.completeExceptionally(
                            new RuntimeException("Publish completion unknown, pubPacketId=" + pubPacketId));
                    }
                    incrementMetric(BifroTaskMetric.PUBLISH_COMPLETION_UNKNOWN_PACKET_ID_COUNT);
                    log.warn("Publish completion unknown, pubPacketId={}", pubPacketId);
                });
                if (clientConfig.getLocalPort() > 0) {
                    PinnedLocalPortTransport.pinLocalPort(clientConfig.getLocalAddress(), clientConfig.getLocalPort());
                }
                mqttClient.connect(clientConfig.getPort(), clientConfig.getHost())
                    .onComplete(connAck -> {
                        if (!connectAttemptHandled.compareAndSet(false, true)) {
                            return;
                        }
                        clearReconnecting();
                        if (connAck.failed()) {
                            logConnectFailure(System.currentTimeMillis() - start, connAck.cause());
                            recoverAfterConnectFailure(connAck.cause()).whenComplete((v, throwable) -> {
                                if (throwable != null) {
                                    connFuture.completeExceptionally(throwable);
                                } else {
                                    connFuture.complete(null);
                                }
                            });
                        } else {
                            logConnectSuccess(System.currentTimeMillis() - start);
                            recordConnectSuccess();
                            connFuture.complete(null);
                        }
                    });
            });
        } else {
            connFuture.complete(null);
        }
        return connFuture;
    }

    private void cancelPendingOperations(Throwable cause) {
        Map<Integer, CompletableFuture<List<Integer>>> subs = inflightSubs;
        if (subs != null) {
            subs.values().forEach(future -> future.completeExceptionally(cause));
            subs.clear();
        }
        Map<Integer, CompletableFuture<Void>> pubs = inflightPubs;
        if (pubs != null) {
            pubs.values().forEach(future -> future.completeExceptionally(cause));
            pubs.clear();
        }
        Map<Integer, io.micrometer.core.instrument.Timer.Sample> subSamples = subscribeLatencySamples;
        if (subSamples != null) {
            subSamples.clear();
        }
        Map<Integer, io.micrometer.core.instrument.Timer.Sample> pubSamples = publishLatencySamples;
        if (pubSamples != null) {
            pubSamples.clear();
        }
        Set<Integer> acks = pubAckCache;
        if (acks != null) {
            acks.clear();
        }
    }

    private Map<Integer, CompletableFuture<List<Integer>>> inflightSubs() {
        Map<Integer, CompletableFuture<List<Integer>>> current = inflightSubs;
        if (current == null) {
            synchronized (this) {
                current = inflightSubs;
                if (current == null) {
                    current = new ConcurrentHashMap<>();
                    inflightSubs = current;
                }
            }
        }
        return current;
    }

    private Map<Integer, CompletableFuture<Void>> inflightPubs() {
        Map<Integer, CompletableFuture<Void>> current = inflightPubs;
        if (current == null) {
            synchronized (this) {
                current = inflightPubs;
                if (current == null) {
                    current = new ConcurrentHashMap<>();
                    inflightPubs = current;
                }
            }
        }
        return current;
    }

    private Set<Integer> pubAckCache() {
        Set<Integer> current = pubAckCache;
        if (current == null) {
            synchronized (this) {
                current = pubAckCache;
                if (current == null) {
                    current = ConcurrentHashMap.newKeySet();
                    pubAckCache = current;
                }
            }
        }
        return current;
    }

    private Map<Integer, io.micrometer.core.instrument.Timer.Sample> publishLatencySamples() {
        Map<Integer, io.micrometer.core.instrument.Timer.Sample> current = publishLatencySamples;
        if (current == null) {
            synchronized (this) {
                current = publishLatencySamples;
                if (current == null) {
                    current = new ConcurrentHashMap<>();
                    publishLatencySamples = current;
                }
            }
        }
        return current;
    }

    private Map<Integer, io.micrometer.core.instrument.Timer.Sample> subscribeLatencySamples() {
        Map<Integer, io.micrometer.core.instrument.Timer.Sample> current = subscribeLatencySamples;
        if (current == null) {
            synchronized (this) {
                current = subscribeLatencySamples;
                if (current == null) {
                    current = new ConcurrentHashMap<>();
                    subscribeLatencySamples = current;
                }
            }
        }
        return current;
    }

    private CompletableFuture<Void> removeInflightPub(int packetId) {
        Map<Integer, CompletableFuture<Void>> current = inflightPubs;
        return current == null ? null : current.remove(packetId);
    }

    private io.micrometer.core.instrument.Timer.Sample removePublishLatencySample(int packetId) {
        Map<Integer, io.micrometer.core.instrument.Timer.Sample> current = publishLatencySamples;
        return current == null ? null : current.remove(packetId);
    }

    private io.micrometer.core.instrument.Timer.Sample removeSubscribeLatencySample(int packetId) {
        Map<Integer, io.micrometer.core.instrument.Timer.Sample> current = subscribeLatencySamples;
        return current == null ? null : current.remove(packetId);
    }

    private void completeSubscription(int packetId, List<Integer> grantedQosLevels) {
        Map<Integer, CompletableFuture<List<Integer>>> current = inflightSubs;
        if (current == null) {
            return;
        }
        current.computeIfPresent(packetId, (id, future) -> {
            future.complete(grantedQosLevels);
            return null;
        });
    }

    @Override
    protected void releaseClientResourcesAfterFinalFailure() {
        MqttClient client = mqttClient;
        if (client == null) {
            return;
        }
        client.closeHandler(ignored -> {
        });
        cancelPendingOperations(new CancellationException("pending operations cancelled after final connect failure"));
        client.disconnect().onFailure(e -> log.debug(
            "Failed to release Vert.x MQTT client after final connect failure, clientId={}",
            clientConfig.getClientId(), e));
    }

}
