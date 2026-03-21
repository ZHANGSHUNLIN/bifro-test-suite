/*
 * Copyright (C) 2024 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite.client;

import static com.baidu.iot.test.suite.constants.ConnectionStatus.CLOSED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.DISCONNECTED;

import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.metric.BifroTaskMetric;
import com.baidu.iot.test.suite.metric.MetricsHelper;
import com.baidu.iot.test.suite.models.TopicFilter;
import com.hivemq.client.internal.mqtt.lifecycle.MqttClientDisconnectedContextImpl;
import com.hivemq.client.mqtt.MqttClientTransportConfig;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5ConnectBuilder;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5ConnectRestrictions;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.Mqtt5Unsubscribe;
import io.micrometer.core.instrument.Tags;
import io.netty.channel.EventLoop;
import io.vertx.core.Vertx;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class HiveMQTTClientWrapper extends BaseMQTTClientWrapper {

    Logger connLogger = LoggerFactory.getLogger("connLogger");

    private final MqttClientConfig clientConfig;
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicBoolean reconnecting = new AtomicBoolean();
    private final EventLoop eventLoop;

    private Mqtt5AsyncClient mqttClient;
    private ConnectionStatus status = ConnectionStatus.INIT;
    protected long reconnectTimer;
    private final Set<TopicFilter> subscribedTopicFilter = new HashSet<>();
    private final AtomicReference<TaskStage> taskStage;


    public HiveMQTTClientWrapper(@NonNull Vertx vertx,
                                 @NonNull ClientTaskConfig taskConfig,
                                 @NonNull MqttClientConfig mqttClientConfig,
                                 @NonNull EventLoop eventLoop, AtomicReference<TaskStage> taskStage) {
        super(vertx, mqttClientConfig, taskConfig);
        this.clientConfig = mqttClientConfig;
        this.eventLoop = eventLoop;
        this.taskStage = taskStage;
    }

    @Override
    public String getClientId() {
        return clientConfig.getClientId();
    }

    @Override
    public boolean isConnected() {
        return status == CONNECTED;
    }

    @Override
    public ConnectionStatus getStatus() {
        return this.status;
    }

    @Override
    public CompletableFuture<Void> close() {
        vertx.cancelTimer(reconnectTimer);
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        if (isConnected()) {
            MetricsHelper.counter(BifroTaskMetric.CLIENT_CLOSE_COUNT,
                    Tags.of("taskId", taskConfig.getTaskId()));
            this.mqttClient.disconnect()
                    .whenComplete((r, e) -> {
                        if (e != null) {
                            log.warn("Failed to close mqtt client, clientId={}", getClientId(), e);
                        }
                        closeFuture.complete(null);
                    });
        } else {
            MetricsHelper.counter(BifroTaskMetric.CLIENT_CLOSE_EXCEPTION_COUNT,
                    Tags.of("taskId", taskConfig.getTaskId()));

            log.info("mqtt client is null , status: {}", status);
            closeFuture.complete(null);
        }
        this.status = ConnectionStatus.CLOSED;
        return closeFuture;
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        if (mqttClient != null) {
            return mqttClient.disconnect()
                    .exceptionally(e -> {
                        log.error("Failed to disconnect mqtt client, clientId={}, ", clientConfig.getClientId(), e);
                        MetricsHelper.counter(BifroTaskMetric.DISCONNECT_COMPLETION_EXPIRATION_COUNT,
                                Tags.of("taskId", taskConfig.getTaskId()));
                        return null;
                    });
        }
        return CompletableFuture.completedFuture(null);
    }


    @Override
    public CompletableFuture<List<Integer>> subscribe(Set<TopicFilter> topicFilters) {
        CompletableFuture<List<Integer>> subFuture = super.subscribe(topicFilters);
        if (subFuture != null) {
            return subFuture;
        }
        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        mqttClient.subscribe(Mqtt5Subscribe.builder()
                .addSubscriptions(
                        topicFilters.stream().map(tf -> Mqtt5Subscription.builder().topicFilter(tf.getName())
                                .qos(MqttQos.fromCode(tf.getQos().value())).build()).collect(
                                Collectors.toList())
                ).build()
        ).whenComplete((ack, e) -> {
            if (e != null) {
                future.completeExceptionally(e);
                MetricsHelper.counter(BifroTaskMetric.SUBSCRIBE_COMPLETION_EXPIRATION_COUNT,
                        Tags.of("taskId", taskConfig.getTaskId()));

            } else {
                MetricsHelper.counter(BifroTaskMetric.SUBSCRIBE_COMPLETION_COUNT,
                        Tags.of("taskId", taskConfig.getTaskId()));
                subscribedTopicFilter.addAll(topicFilters);
                future.complete(ack.getReasonCodes().stream()
                        .map(Mqtt5SubAckReasonCode::getCode).collect(Collectors.toList()));
            }
        });
        return future;
    }

    public CompletableFuture<Void> unsubscribeAll() {
        CompletableFuture<Void> unsubFuture = new CompletableFuture<>();
        if (!(status == CONNECTED && isConnected())) {
            log.warn("Unsubscribe cancelled for mqttClient is not connected, clientId={}", clientConfig.getClientId());
            unsubFuture.completeExceptionally(new RuntimeException("mqttClient is not connected"));
            MetricsHelper.counter(BifroTaskMetric.UNSUBSCRIBE_COMPLETION_EXPIRATION_COUNT,
                    Tags.of("taskId", taskConfig.getTaskId()));
            return unsubFuture;
        }
        List<MqttTopicFilter> mqttTopicFilters = subscribedTopicFilter.stream()
                .map(tf -> MqttTopicFilter.of(tf.getName())).collect(Collectors.toList());
        subscribedTopicFilter.forEach(topicFilter ->
                mqttClient.unsubscribe(Mqtt5Unsubscribe.builder().addTopicFilters(mqttTopicFilters).build())
                        .whenComplete((ack, e) -> {
                            log.info("unsub the topicFilter: {}, ok: {}", topicFilter.getName(), e == null);
                            subscribedTopicFilter.clear();
                            MetricsHelper.counter(BifroTaskMetric.UNSUBSCRIBE_COMPLETION_COUNT,
                                    Tags.of("taskId", taskConfig.getTaskId()));
                            unsubFuture.complete(null);
                        })
        );
        return unsubFuture;
    }

    @Override
    public CompletableFuture<Void> publish(byte[] payload, String topic, int qos, boolean isDup, boolean isRetain) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (status == ConnectionStatus.CONNECTED && isConnected()) {
            String pubTopic = taskConfig.getPubTopic();
            if (taskConfig.isRandomPublishing()) {
                pubTopic = pubTopic + "/" + System.currentTimeMillis();
            }
            mqttClient.publish(Mqtt5Publish.builder()
                            .topic(pubTopic)
                            .payload(payload)
                            .qos(MqttQos.fromCode(qos))
                            .retain(isRetain)
                            .build())
                    .whenComplete((pubResult, e) -> {
                        if (e != null) {
                            result.completeExceptionally(e);
                        } else {
                            result.complete(null);
                        }
                    });
            MetricsHelper.counter(BifroTaskMetric.PUBLISH_COMPLETION_COUNT,
                    Tags.of("taskId", taskConfig.getTaskId()));
        } else {
            result.completeExceptionally(new RuntimeException("Client not connected!"));
            MetricsHelper.counter(BifroTaskMetric.PUBLISH_COMPLETION_EXPIRATION_COUNT,
                    Tags.of("taskId", taskConfig.getTaskId(), "state", status.name()));
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
        if (reconnecting.compareAndSet(false, true)) {
            this.eventLoop.execute(() -> {
                mqttClient = buildClient(context -> {
                    // only handle connect success
                    reconnecting.set(false);
                    connLogger.debug("MqttClient connect success, clientId={}, costs={}ms, hashcode: {}",
                            context.getClientConfig().getClientIdentifier(), System.currentTimeMillis() - start,
                            this.hashCode());
                    MetricsHelper.counter(BifroTaskMetric.CONNECT_SUCCESS_COUNT, Tags.of("taskId", taskConfig.getTaskId()));
                    status = CONNECTED;
                    connectCallback.accept(CONNECTED);
                }, context -> {
                    // handle connect failed and disconnect
                    reconnecting.set(false);
                    if (status == CLOSED) {
                        return;
                    }
                    MqttClientDisconnectedContextImpl disconnectedContext = (MqttClientDisconnectedContextImpl) context;
                    connLogger.warn(
                            "MqttClient connect failed in {}th attempt, clientId={}, localAddr={}, reason={}, costs={}ms",
                            reconnectAttempts.get() + 1, context.getClientConfig().getClientIdentifier(),
                            clientConfig.getLocalAddress(),
                            disconnectedContext.getCause().getMessage(), System.currentTimeMillis() - start,
                            disconnectedContext.getCause());
                    status = DISCONNECTED;
                    MetricsHelper.counter(BifroTaskMetric.CONNECT_EXCEPTION_COUNT,
                            Tags.of("taskId", taskConfig.getTaskId()));
                    connectCallback.accept(DISCONNECTED);
                    tryRecoverConnect();
                });
                mqttClient.publishes(MqttGlobalPublishFilter.ALL, pub -> this.pubMsgListener.onPublishMessage(pub.getPayloadAsBytes(), false));
                mqttClient.connect(buildConnect());
            });
        }
    }


    private Mqtt5AsyncClient buildClient(MqttClientConnectedListener connectedListener,
                                         MqttClientDisconnectedListener disconnectedListener) {
        boolean emptyClientId = clientConfig.isEmptyClientId();
        if (emptyClientId) {
            if (!clientConfig.isCleanSession()) {
                log.warn("Since cleanSession=false, emptyClientId is set to false");
                emptyClientId = false;
            }
        }
        connLogger.debug("clientId:{}, localAddress: {} , host: {}, port: {},username: {}, password: {}", clientConfig.getClientId(), clientConfig.getLocalAddress(), clientConfig.getHost(), clientConfig.getPort(), clientConfig.getUsername(), clientConfig.getPassword());
        // TODO ssl support
        return mqttClient = com.hivemq.client.mqtt.MqttClient.builder()
                .useMqttVersion5()
                .identifier(emptyClientId ? "" : clientConfig.getClientId())
                .simpleAuth(Mqtt5SimpleAuth.builder()
                        .username(clientConfig.getUsername())
                        .password(clientConfig.getPassword().getBytes(StandardCharsets.UTF_8))
                        .build())
                .transportConfig(MqttClientTransportConfig.builder()
                        .localAddress(clientConfig.getLocalAddress())
                        .serverHost(clientConfig.getHost())
                        .serverPort(clientConfig.getPort())
                        .mqttConnectTimeout(clientConfig.getConnectTimeoutInMs(), TimeUnit.MILLISECONDS)
                        .build()
                )
                .addConnectedListener(connectedListener)
                .addDisconnectedListener(disconnectedListener)
                .buildAsync();
    }

    private Mqtt5Connect buildConnect() {
        Mqtt5ConnectBuilder builder = Mqtt5Connect.builder();
        if (clientConfig.isCleanSession()) {
            builder = builder.cleanStart(true).sessionExpiryInterval(0);
        } else {
            builder = builder.cleanStart(false).sessionExpiryInterval(clientConfig.getExpiryIntervalInSec());
        }
        return builder.keepAlive(clientConfig.getKeepAliveInSec())
                .restrictions(Mqtt5ConnectRestrictions.builder()
                        .maximumPacketSize(Math.max(512, taskConfig.getMessageSize() * 2))
                        .sendMaximum(clientConfig.getMaxInflightQueue())
                        .build()
                ).build();
    }
}
