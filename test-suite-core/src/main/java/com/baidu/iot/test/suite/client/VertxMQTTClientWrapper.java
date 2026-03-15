/*
 * Copyright (C) 2024 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite.client;

import static com.baidu.iot.test.suite.constants.ConnectionStatus.CLOSED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTED_FAILED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTING;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.DISCONNECTED;

import com.baidu.iot.test.suite.IPubMsgListener;
import com.baidu.iot.test.suite.WillConfig;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.models.TopicFilter;
import com.baidu.iot.test.suite.utils.ConnectionUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.NetClientImpl;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.vertx.mqtt.impl.MqttClientImpl;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class VertxMQTTClientWrapper implements MQTTClientWrapper {

    Logger connLogger = LoggerFactory.getLogger("connLogger");
    Logger pubLogger = LoggerFactory.getLogger("pubLogger");
    Logger subLogger = LoggerFactory.getLogger("subLogger");

    private final Vertx vertx;
    private final MqttClientConfig clientConfig;
    private final ClientTaskConfig taskConfig;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicBoolean reconnectFlag = new AtomicBoolean();
    private final MqttClientOptions mqttOptions;
    private final EventLoop eventLoop;

    private MqttClient mqttClient;
    private ConnectionStatus status;
    private Consumer<ConnectionStatus> connectCallback;
    private IPubMsgListener pubMsgListener;
    protected long reconnectTimer;
    private final Set<TopicFilter> subscribedTopicFilter = new HashSet<>();
    private final Map<Integer, CompletableFuture<List<Integer>>> inflightSubs = new HashMap<>();
    private final Map<Integer, CompletableFuture<Void>> inflightPubs = new HashMap<>();
    private final Set<Integer> pubAckCache = new HashSet<>();

    public VertxMQTTClientWrapper(@NonNull Vertx vertx,
                                  @NonNull ClientTaskConfig taskConfig,
                                  @NonNull MqttClientConfig mqttClientConfig,
                                  @NonNull EventLoop eventLoop) {
        status = ConnectionStatus.INIT;
        this.vertx = vertx;
        this.taskConfig = taskConfig;
        this.clientConfig = mqttClientConfig;
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
        vertx.cancelTimer(reconnectTimer);
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        if (mqttClient != null) {
            if (mqttClient.isConnected()) {
                this.mqttClient.disconnect(event -> {
                    closeFuture.complete(null);
                    if (event.failed()) {
                        log.error("Failed to close mqtt client, clientId={}, ",
                                mqttClient.clientId(), event.cause());
                    }
                });
            }
        } else {
            log.info("mqtt client is null");
            closeFuture.complete(null);
        }
        return closeFuture;
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        if (mqttClient != null) {
            return mqttClient.disconnect().toCompletionStage().toCompletableFuture();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void connect(Consumer<ConnectionStatus> connectCallback, IPubMsgListener pubMsgListener) {
        if (status == CONNECTED || status == CONNECTING || isConnected()) {
            log.warn("mqttClient is already connected, clientId={}", clientConfig.getClientId());
            return;
        }
        this.connectCallback = connectCallback;
        this.pubMsgListener = pubMsgListener;
        status = ConnectionStatus.CONNECTING;
         internalConnect();
    }

    @Override
    public CompletableFuture<List<Integer>> subscribe(Set<TopicFilter> topicFilters) {
        CompletableFuture<List<Integer>> subFuture = new CompletableFuture<>();
        if (!(status == CONNECTED && isConnected())) {
            log.warn("Subscribe cancelled for mqttClient is not connected, clientId={}", clientConfig.getClientId());
            subFuture.completeExceptionally(new RuntimeException("mqttClient is not connected"));
            return subFuture;
        }
        mqttClient.subscribe(
                topicFilters.stream().collect(Collectors.toMap(TopicFilter::getName, tf -> tf.getQos().value())),
                subPacket -> {
                    if (subPacket.failed()) {
                        subFuture.completeExceptionally(subPacket.cause());
                    } else {
                        inflightSubs.put(subPacket.result(), subFuture);
                        subscribedTopicFilter.addAll(topicFilters);
                    }
                }
        );
        return subFuture;
    }

    public CompletableFuture<Void> unsubscribeAll() {
        CompletableFuture<Void> unsubFuture = new CompletableFuture<>();
        if (!(status == CONNECTED && isConnected())) {
            log.warn("Unsubscribe cancelled for mqttClient is not connected, clientId={}", clientConfig.getClientId());
            unsubFuture.completeExceptionally(new RuntimeException("mqttClient is not connected"));
            return unsubFuture;
        }
        AtomicInteger futureCount = new AtomicInteger();
        subscribedTopicFilter.forEach(topicFilter -> mqttClient.unsubscribe(topicFilter.getName(), event -> {
            log.info("unsub the topicFilter: {}, ok: {}", topicFilter.getName(), event.succeeded());
            subscribedTopicFilter.remove(topicFilter);
            if (futureCount.incrementAndGet() == subscribedTopicFilter.size()) {
                unsubFuture.complete(null);
            }
        }));
        return unsubFuture;
    }

    @Override
    public CompletableFuture<Void> publish(byte[] payload, String topic, int qos, boolean isDup, boolean isRetain) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (status == ConnectionStatus.CONNECTED && isConnected()) {
            mqttClient.publish(topic, Buffer.buffer(payload), MqttQoS.valueOf(qos),
                    isDup, isRetain,
                    pubResult -> {
                        if (pubResult.succeeded()) {
                            // qos0 immediately record succeed
                            if (MqttQoS.AT_MOST_ONCE.equals( MqttQoS.valueOf(qos))) {
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
                    }).exceptionHandler(e -> {
                        log.error("Failed to publish message", e);
                        result.completeExceptionally(e);
            });
        } else {
            result.completeExceptionally(new RuntimeException("Client not connected!"));
        }
        return result;
    }


    public void bindLocalAddress(MqttClient client, String localIp, int localPort) {
        try {
            // 1. 从 MqttClientImpl 拿到底层的 NetClientImpl
            Field clientField = MqttClientImpl.class.getDeclaredField("client");
            clientField.setAccessible(true);
            NetClientImpl netClient = (NetClientImpl) clientField.get(client);
            // 2. 从 NetClientImpl 拿到 Netty 的 Bootstrap
            // 注意：不同版本中字段名可能略有不同，通常是 "bootstrap"
            Field bootstrapField = NetClientImpl.class.getDeclaredField("bootstrap");
            bootstrapField.setAccessible(true);
            Bootstrap bootstrap = (Bootstrap) bootstrapField.get(netClient);
            // 3. 强行绑定本地 IP 和端口
            bootstrap.localAddress(localIp, localPort);
            System.out.println("Successfully bound to " + localIp + ":" + localPort);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void internalConnect() {
        long start = System.currentTimeMillis();
        if (status == CLOSED) {
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
                    if (status == CLOSED) {
                        return;
                    }
                    status = DISCONNECTED;
                    connectCallback.accept(DISCONNECTED);
                    tryRecoverConnect();
                });
                mqttClient.exceptionHandler(error ->
                        log.error("MqttClient exception, id={}", mqttClient.clientId(), error));
                mqttClient.subscribeCompletionHandler(subAckMessage -> {
                    inflightSubs.computeIfPresent(subAckMessage.messageId(), (id, future) -> {
                        future.complete(subAckMessage.grantedQoSLevels());
                        return null;
                    });
                });
                mqttClient.publishHandler(
                        mqttPublishMessage -> this.pubMsgListener.onPublishMessage(mqttPublishMessage.payload().getBytes(),
                                mqttPublishMessage.isDup()));
                mqttClient.publishCompletionHandler(pubAckPacketId -> {
                    CompletableFuture<Void> pendingResult = inflightPubs.remove(pubAckPacketId);
                    if (pendingResult != null) {
                        pendingResult.complete(null);
                    } else {
                        pubAckCache.add(pubAckPacketId);
                    }
                });
                mqttClient.publishCompletionExpirationHandler(pubPacketId -> {
                    inflightPubs.remove(pubPacketId);
                    log.warn("PubAck has not arrive in ackTimeout, pubPacketId={}", pubPacketId);
                });
                mqttClient.publishCompletionUnknownPacketIdHandler(pubPacketId -> {
                    inflightPubs.remove(pubPacketId);
                    log.warn("Publish completion unknown, pubPacketId={}", pubPacketId);
                });
                mqttClient.connect(clientConfig.getPort(), clientConfig.getHost(), connAck -> {
                    reconnectFlag.set(false);
                    if (connAck.failed()) {
                        connLogger.warn(
                                "MqttClient connect failed in {}th attempt, clientId={}, localAddr={}, reason={}, costs={}ms",
                                reconnectAttempts.get() + 1, clientConfig.getClientId(), clientConfig.getLocalAddress(),
                                connAck.result(), System.currentTimeMillis() - start, connAck.cause());
                        tryRecoverConnect();
                    } else {
                        connLogger.debug("MqttClient connect success, clientId={}, costs={}ms, hashcode: {}",
                                clientConfig.getClientId(), System.currentTimeMillis() - start, this.hashCode());
                        status = CONNECTED;
                        connectCallback.accept(CONNECTED);
                    }
                });
            });
        }
    }

    private void tryRecoverConnect() {
        if (reconnectAttempts.getAndIncrement() < clientConfig.getReconnectMaxAttempts()) {
            reconnectTimer =
                    vertx.setTimer(ThreadLocalRandom.current().nextLong(clientConfig.getReconnectIntervalInMs(),
                                    clientConfig.getReconnectIntervalInMs() * 2L),
                            t -> internalConnect());
        } else {
            status = CONNECTED_FAILED;
            log.error("MqttClient connect failed after {} retries, clientId={}, localAddr={}",
                    clientConfig.getReconnectMaxAttempts(),
                    clientConfig.getClientId(), clientConfig.getLocalAddress());
            connectCallback.accept(CONNECTED_FAILED);
        }
    }
}
