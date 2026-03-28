/*
 * Copyright (C) 2024 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite.client;

import static com.baidu.iot.test.suite.constants.ConnectionStatus.CLOSED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.DISCONNECTED;

import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.WillConfig;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.metric.BifroTaskMetric;
import com.baidu.iot.test.suite.models.TopicFilter;
import com.baidu.iot.test.suite.utils.ConnectionUtil;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class VertxMQTTClientWrapper extends BaseMQTTClientWrapper {

    private final MqttClientOptions mqttOptions;
    private final EventLoop eventLoop;

    private MqttClient mqttClient;
    private final Map<Integer, CompletableFuture<List<Integer>>> inflightSubs = new HashMap<>();
    private final Map<Integer, CompletableFuture<Void>> inflightPubs = new HashMap<>();
    private final Set<Integer> pubAckCache = new java.util.HashSet<>();


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
            connLogger.trace("willConfig={}", willConfig);
            mqttOptions.setWillFlag(true)
                    .setWillTopic(willConfig.getWillTopic())
                    .setWillMessageBytes(lastMsg)
                    .setWillQoS(willConfig.getWillQos())
                    .setWillRetain(willConfig.getWillRetain());
        }

        // ConnectTimeout in vertx is TCP connect timeout.
        mqttOptions.setConnectTimeout(clientConfig.getConnectTimeoutInMs())
                .setReconnectAttempts(clientConfig.getReconnectMaxAttempts())
                .setReconnectInterval(clientConfig.getReconnectIntervalInMs())
                .setTcpKeepAlive(true)
                .setLocalAddress(clientConfig.getLocalAddress());
        if (ConnectionUtil.isSSL(clientConfig.getProtocol())) {
            mqttOptions.setSsl(true);
            mqttOptions.setTrustAll(true);
            // TODO support detailed ssl connection.
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
            }
            recordCloseFailure();
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
        CompletableFuture<List<Integer>> subscribe = super.subscribe(topicFilters);
        if (subscribe != null) {
            return subscribe;
        }
        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        mqttClient.subscribe(
                topicFilters.stream().collect(Collectors.toMap(TopicFilter::getName, tf -> tf.getQos().value()))
        ).onComplete(subPacket -> {
            if (subPacket.failed()) {
                future.completeExceptionally(subPacket.cause());
            } else {
                inflightSubs.put(subPacket.result(), future);
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
            mqttClient.publish(topic, Buffer.buffer(payload), MqttQoS.valueOf(qos),
                    isDup, isRetain
            ).onComplete(pubResult -> {
                if (pubResult.succeeded()) {
                    // qos0 immediately record succeed
                    if (MqttQoS.AT_MOST_ONCE.equals(MqttQoS.valueOf(qos))) {
                        recordPublishSuccess();
                        result.complete(null);
                    } else {
                        // if pub ack return earlier than tcp ack
                        boolean exist = pubAckCache.remove(pubResult.result());
                        if (exist) {
                            log.info("handle ack earlier than tcp ack!");
                            result.complete(null);
                        } else {
                            inflightPubs.put(pubResult.result(), result);
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
    public void internalConnect() {
        TaskStage stage = taskStage.get();
        long start = System.currentTimeMillis();
        if (!canConnect(stage)) {
            return;
        }
        if (trySetReconnecting()) {
            this.eventLoop.execute(() -> {
                mqttClient = MqttClient.create(vertx, mqttOptions);
                mqttClient.closeHandler(xVoid -> {
                    logDisconnect();
                    inflightSubs.values().forEach(future -> future.completeExceptionally(
                            new CancellationException("inflightSubs cancelled when connection closed")));
                    recordDisconnect();
                    if (status == CLOSED) {
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
                            incrementMetric(BifroTaskMetric.SUBSCRIBE_COMPLETION_COUNT);
                            inflightSubs.computeIfPresent(subAckMessage.messageId(),
                                    (id, future) -> {
                                        future.complete(subAckMessage.grantedQoSLevels());
                                        return null;
                                    });
                        }
                );
                mqttClient.publishHandler(
                        mqttPublishMessage -> {
                            this.pubMsgListener.onPublishMessage(mqttPublishMessage.payload().getBytes(),
                                    mqttPublishMessage.isDup());
                            recordPublishSuccess();
                        });
                mqttClient.publishCompletionHandler(pubAckPacketId -> {
                    CompletableFuture<Void> pendingResult = inflightPubs.remove(pubAckPacketId);
                    if (pendingResult != null) {
                        pendingResult.complete(null);
                    } else {
                        pubAckCache.add(pubAckPacketId);
                    }
                    incrementMetric(BifroTaskMetric.PUBLISH_COUNT);

                });
                mqttClient.publishCompletionExpirationHandler(pubPacketId -> {
                    inflightPubs.remove(pubPacketId);
                    incrementMetric(BifroTaskMetric.PUBLISH_COMPLETION_EXPIRATION_COUNT);
                    log.warn("PubAck has not arrive in ack Timeout, pubPacketId={}", pubPacketId);
                });
                mqttClient.publishCompletionUnknownPacketIdHandler(pubPacketId -> {
                    inflightPubs.remove(pubPacketId);
                    incrementMetric(BifroTaskMetric.PUBLISH_COMPLETION_UNKNOWN_PACKET_ID_COUNT);
                    log.warn("Publish completion unknown, pubPacketId={}", pubPacketId);
                });
                mqttClient.connect(clientConfig.getPort(), clientConfig.getHost()).onComplete(connAck -> {
                    clearReconnecting();
                    if (connAck.failed()) {
                        logConnectFailure(System.currentTimeMillis() - start, connAck.cause());
                        tryRecoverConnect();
                    } else {
                        logConnectSuccess(System.currentTimeMillis() - start);
                        recordConnectSuccess();
                    }
                });
            });
        }
    }


}
