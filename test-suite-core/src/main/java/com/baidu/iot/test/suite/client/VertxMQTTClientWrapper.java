/*
 * Copyright (C) 2024 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite.client;

import static com.baidu.iot.test.suite.constants.ConnectionStatus.CLOSED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.DISCONNECTED;

import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.WillConfig;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.metric.BifroTaskMetric;
import com.baidu.iot.test.suite.metric.MetricsHelper;
import com.baidu.iot.test.suite.models.TopicFilter;
import com.baidu.iot.test.suite.utils.ConnectionUtil;
import io.micrometer.core.instrument.Tags;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class VertxMQTTClientWrapper extends BaseMQTTClientWrapper {

    private final AtomicBoolean reconnectFlag = new AtomicBoolean();
    private final MqttClientOptions mqttOptions;
    private final EventLoop eventLoop;

    private MqttClient mqttClient;
    private final Set<TopicFilter> subscribedTopicFilter = new HashSet<>();
    private final Map<Integer, CompletableFuture<List<Integer>>> inflightSubs = new HashMap<>();
    private final Map<Integer, CompletableFuture<Void>> inflightPubs = new HashMap<>();
    private final Set<Integer> pubAckCache = new HashSet<>();
    private final AtomicReference<TaskStage> taskStage;


    public VertxMQTTClientWrapper(@NonNull Vertx vertx,
                                  @NonNull ClientTaskConfig taskConfig,
                                  @NonNull MqttClientConfig mqttClientConfig,
                                  @NonNull EventLoop eventLoop, AtomicReference<TaskStage> taskStage) {
        super(vertx, mqttClientConfig, taskConfig);
        status = ConnectionStatus.INIT;
        this.eventLoop = eventLoop;
        this.taskStage = taskStage;
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
        vertx.cancelTimer(reconnectTimer);
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        if (mqttClient != null) {
            if (mqttClient.isConnected()) {
                MetricsHelper.counter(BifroTaskMetric.CLIENT_CLOSE_COUNT,
                        Tags.of("taskId", taskConfig.getTaskId()));
                this.mqttClient.disconnect().onComplete(event -> {
                    closeFuture.complete(null);
                    if (event.failed()) {
                        log.error("Failed to close mqtt client, clientId={}, ",
                                mqttClient.clientId(), event.cause());
                    }
                });
            }
            MetricsHelper.counter(BifroTaskMetric.CLIENT_CLOSE_EXCEPTION_COUNT,
                    Tags.of("taskId", taskConfig.getTaskId()));
        } else {
            MetricsHelper.counter(BifroTaskMetric.CLIENT_CLOSE_EXCEPTION_COUNT,
                    Tags.of("taskId", taskConfig.getTaskId()));
//            log.info("mqtt client is null");
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
                        MetricsHelper.counter(BifroTaskMetric.DISCONNECT_COMPLETION_EXPIRATION_COUNT,
                                Tags.of("taskId", taskConfig.getTaskId()));
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
        if (!(status == CONNECTED && isConnected())) {
            MetricsHelper.counter(BifroTaskMetric.UNSUBSCRIBE_COMPLETION_EXPIRATION_COUNT,
                    Tags.of("taskId", taskConfig.getTaskId()));
            log.warn("Unsubscribe cancelled for mqttClient is not connected, clientId={}", clientConfig.getClientId());
            unsubFuture.completeExceptionally(new RuntimeException("mqttClient is not connected"));
            return unsubFuture;
        }
        AtomicInteger futureCount = new AtomicInteger();
        List<String> topics = subscribedTopicFilter.stream()
                .map(TopicFilter::getName)
                .collect(Collectors.toList());
        mqttClient.unsubscribe(topics).onComplete(event -> {
            log.info("unsub the topics: {}, ok: {}", topics, event.succeeded());
            subscribedTopicFilter.clear();
            MetricsHelper.counter(BifroTaskMetric.UNSUBSCRIBE_COMPLETION_COUNT,
                    Tags.of("taskId", taskConfig.getTaskId()));
            unsubFuture.complete(null);
        });
        return unsubFuture;
    }

    @Override
    public CompletableFuture<Void> publish(byte[] payload, String topic, int qos, boolean isDup, boolean isRetain) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (status == ConnectionStatus.CONNECTED && isConnected()) {
            mqttClient.publish(topic, Buffer.buffer(payload), MqttQoS.valueOf(qos),
                    isDup, isRetain
            ).onComplete(pubResult -> {
                if (pubResult.succeeded()) {
                    // qos0 immediately record succeed
                    if (MqttQoS.AT_MOST_ONCE.equals(MqttQoS.valueOf(qos))) {
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
        }
        return result;
    }

    @Override
    public void internalConnect() {
        TaskStage stage = taskStage.get();
        if (stage != TaskStage.ONGOING) {
            MetricsHelper.counter(BifroTaskMetric.ILLEGAL_TASK_STATE,
                    Tags.of("stage", stage.name(), "taskId", taskConfig.getTaskId()));
            log.warn("MqttClient reconnect cancelled, clientId={}, taskStage={}", clientConfig.getClientId(), stage);
            return;
        }
        long start = System.currentTimeMillis();
        if (status == CLOSED) {
            MetricsHelper.counter(BifroTaskMetric.ILLEGAL_STATE_CLIENT_CLOSED,
                    Tags.of("taskId", taskConfig.getTaskId()));
            log.warn("MqttClient is closed, clientId={}", clientConfig.getClientId());
            return;
        }
        if (reconnectFlag.compareAndSet(false, true)) {
            this.eventLoop.execute(() -> {
                mqttClient = MqttClient.create(vertx, mqttOptions);
                mqttClient.closeHandler(xVoid -> {
                    connLogger.debug("MQTT client connection closed, taskId={}, clientId={}, status before close={}", taskConfig.getTaskId(),
                            clientConfig.getClientId(), status);
                    inflightSubs.values().forEach(future -> future.completeExceptionally(
                            new CancellationException("inflightSubs cancelled when connection closed")));
                    MetricsHelper.counter(BifroTaskMetric.DISCONNECT_COMPLETION_COUNT,
                            Tags.of("taskId", taskConfig.getTaskId()));
                    if (status == CLOSED) {
                        return;
                    }
                    status = DISCONNECTED;
                    connectCallback.accept(DISCONNECTED);
                    tryRecoverConnect();
                });
                mqttClient.exceptionHandler(error -> {
                    MetricsHelper.counter(BifroTaskMetric.CONNECT_EXCEPTION_COUNT,
                            Tags.of("taskId", taskConfig.getTaskId()));
                    log.error("MqttClient exception, id={}", mqttClient.clientId(), error);
                });
                mqttClient.subscribeCompletionHandler(subAckMessage -> {
                            MetricsHelper.counter(BifroTaskMetric.SUBSCRIBE_COMPLETION_COUNT,
                                    Tags.of("taskId", taskConfig.getTaskId()));
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
                            MetricsHelper.counter(BifroTaskMetric.PUBLISH_COMPLETION_COUNT,
                                    Tags.of("taskId", taskConfig.getTaskId()));
                        });
                mqttClient.publishCompletionHandler(pubAckPacketId -> {
                    CompletableFuture<Void> pendingResult = inflightPubs.remove(pubAckPacketId);
                    if (pendingResult != null) {
                        pendingResult.complete(null);
                    } else {
                        pubAckCache.add(pubAckPacketId);
                    }
                    MetricsHelper.counter(BifroTaskMetric.PUBLISH_COUNT, Tags.of("taskId", taskConfig.getTaskId()));

                });
                mqttClient.publishCompletionExpirationHandler(pubPacketId -> {
                    inflightPubs.remove(pubPacketId);
                    MetricsHelper.counter(BifroTaskMetric.PUBLISH_COMPLETION_EXPIRATION_COUNT, Tags.of("taskId", taskConfig.getTaskId()));
                    log.warn("PubAck has not arrive in ack Timeout, pubPacketId={}", pubPacketId);
                });
                mqttClient.publishCompletionUnknownPacketIdHandler(pubPacketId -> {
                    inflightPubs.remove(pubPacketId);
                    MetricsHelper.counter(BifroTaskMetric.PUBLISH_COMPLETION_UNKNOWN_PACKET_ID_COUNT, Tags.of("taskId", taskConfig.getTaskId()));
                    log.warn("Publish completion unknown, pubPacketId={}", pubPacketId);
                });
                mqttClient.connect(clientConfig.getPort(), clientConfig.getHost()).onComplete(connAck -> {
                    reconnectFlag.set(false);
                    if (connAck.failed()) {
                        connLogger.warn(
                                "MqttClient connect failed in {}th attempt, clientId={}, localAddr={}, reason={}, costs={}ms",
                                reconnectAttempts.get() + 1, clientConfig.getClientId(), clientConfig.getLocalAddress(),
                                connAck.result(), System.currentTimeMillis() - start, connAck.cause());
                        MetricsHelper.counter(BifroTaskMetric.SUBSCRIBE_COMPLETION_EXPIRATION_COUNT, Tags.of("taskId", taskConfig.getTaskId()));
                        tryRecoverConnect();
                    } else {
                        connLogger.debug("MqttClient connect success, clientId={}, costs={}ms, hashcode: {}",
                                clientConfig.getClientId(), System.currentTimeMillis() - start, this.hashCode());
                        status = CONNECTED;
                        MetricsHelper.counter(BifroTaskMetric.CONNECT_SUCCESS_COUNT, Tags.of("taskId", taskConfig.getTaskId()));
                        connectCallback.accept(CONNECTED);
                    }
                });
            });
        }
    }


}
