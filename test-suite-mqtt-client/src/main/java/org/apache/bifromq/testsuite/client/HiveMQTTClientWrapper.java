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
import static org.apache.bifromq.testsuite.constants.ConnectionStatus.CONNECTED;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.MqttClientSslConfigBuilder;
import com.hivemq.client.mqtt.MqttClientTransportConfig;
import com.hivemq.client.mqtt.MqttClientTransportConfigBuilder;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;
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
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.vertx.core.Vertx;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.constants.ConnectionStatus;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.models.TopicFilter;
import org.apache.bifromq.testsuite.utils.ConnectionUtil;

@Slf4j
public class HiveMQTTClientWrapper extends BaseMQTTClientWrapper {

    private final boolean isMqtt5;

    private volatile Mqtt5AsyncClient mqtt5Client;

    private volatile Mqtt3AsyncClient mqtt3Client;

    public HiveMQTTClientWrapper(@NonNull Vertx vertx,
                                 @NonNull ClientTaskConfig taskConfig,
                                 @NonNull MqttClientConfig mqttClientConfig,
                                 AtomicReference<TaskStage> taskStage) {
        super(vertx, mqttClientConfig, taskConfig, taskStage);
        this.isMqtt5 = taskConfig.isMqtt5();
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
        cancelReconnectTimer();
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        if (isConnected()) {
            recordCloseSuccess();
            getDisconnectFuture().whenComplete((r, e) -> {
                if (e != null) {
                    log.warn("Failed to close mqtt client, clientId={}", getClientId(), e);
                }
                closeFuture.complete(null);
            });
        } else {
            recordCloseFailure();
            log.info("mqtt client is not connected, status={}", status);
            closeFuture.complete(null);
        }
        this.status = ConnectionStatus.CLOSED;
        return closeFuture;
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return getDisconnectFuture().exceptionally(e -> {
            log.error("Failed to disconnect mqtt client, clientId={}", clientConfig.getClientId(), e);
            recordDisconnectFailure();
            return null;
        });
    }

    private CompletableFuture<Void> getDisconnectFuture() {
        if (isMqtt5 && mqtt5Client != null) {
            return mqtt5Client.disconnect();
        } else if (!isMqtt5 && mqtt3Client != null) {
            return mqtt3Client.disconnect();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Integer>> subscribe(Set<TopicFilter> topicFilters) {
        if (!isConnectedState()) {
            CompletableFuture<List<Integer>> earlyReturn = new CompletableFuture<>();
            SUB_LOGGER.warn("Subscribe cancelled for mqttClient is not connected, clientId={}",
                clientConfig.getClientId());
            earlyReturn.completeExceptionally(new RuntimeException("mqttClient is not connected"));
            incrementMetric(BifroTaskMetric.SUBSCRIBE_COMPLETION_EXPIRATION_COUNT);
            return earlyReturn;
        }
        if (isMqtt5) {
            return subscribeMqtt5(topicFilters);
        } else {
            return subscribeMqtt3(topicFilters);
        }
    }

    private CompletableFuture<List<Integer>> subscribeMqtt5(Set<TopicFilter> topicFilters) {
        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        io.micrometer.core.instrument.Timer.Sample sample = MetricsHelper.startTimer();
        mqtt5Client.subscribe(Mqtt5Subscribe.builder()
            .addSubscriptions(topicFilters.stream()
                .map(tf -> Mqtt5Subscription.builder()
                    .topicFilter(tf.getName())
                    .qos(MqttQos.fromCode(tf.getQos().value()))
                    .build())
                .collect(Collectors.toList()))
            .build()
        ).whenComplete((ack, e) -> {
            if (e != null) {
                future.completeExceptionally(e);
                recordSubscribeFailure();
            } else {
                recordSubscribeLatency(sample);
                recordSubscribeSuccess(topicFilters.size());
                subscribedTopicFilter.addAll(topicFilters);
                future.complete(ack.getReasonCodes().stream()
                    .map(Mqtt5SubAckReasonCode::getCode)
                    .collect(Collectors.toList()));
            }
        });
        return future;
    }

    private CompletableFuture<List<Integer>> subscribeMqtt3(Set<TopicFilter> topicFilters) {
        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        io.micrometer.core.instrument.Timer.Sample sample = MetricsHelper.startTimer();
        mqtt3Client.subscribe(Mqtt3Subscribe.builder()
            .addSubscriptions(topicFilters.stream()
                .map(tf -> Mqtt3Subscription.builder()
                    .topicFilter(tf.getName())
                    .qos(MqttQos.fromCode(tf.getQos().value()))
                    .build())
                .collect(Collectors.toList()))
            .build()
        ).whenComplete((ack, e) -> {
            if (e != null) {
                future.completeExceptionally(e);
                recordSubscribeFailure();
            } else {
                recordSubscribeLatency(sample);
                recordSubscribeSuccess(topicFilters.size());
                subscribedTopicFilter.addAll(topicFilters);
                future.complete(ack.getReturnCodes().stream()
                    .map(Mqtt3SubAckReturnCode::getCode)
                    .collect(Collectors.toList()));
            }
        });
        return future;
    }

    private void recordSubscribeLatency(io.micrometer.core.instrument.Timer.Sample sample) {
        MetricsHelper.stopTimer(sample, BifroTaskMetric.SUBSCRIBE_LATENCY,
            "taskId", taskConfig.getTaskId(), "nodeId", taskConfig.getNodeId());
    }

    @Override
    public CompletableFuture<Void> unsubscribeAll() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!isConnectedState()) {
            log.warn("Unsubscribe cancelled: client not connected, clientId={}", clientConfig.getClientId());
            future.completeExceptionally(new RuntimeException("mqttClient is not connected"));
            recordUnsubscribeFailure();
            return future;
        }
        List<MqttTopicFilter> filters = subscribedTopicFilter.stream()
            .map(tf -> MqttTopicFilter.of(tf.getName()))
            .collect(Collectors.toList());
        if (isMqtt5) {
            mqtt5Client.unsubscribe(Mqtt5Unsubscribe.builder().addTopicFilters(filters).build())
                .whenComplete((ack, e) -> {
                    subscribedTopicFilter.clear();
                    if (e == null) {
                        recordUnsubscribeSuccess();
                    } else {
                        recordUnsubscribeFailure();
                    }
                    future.complete(null);
                });
        } else {
            mqtt3Client.unsubscribe(com.hivemq.client.mqtt.mqtt3.message.unsubscribe.Mqtt3Unsubscribe.builder()
                    .addTopicFilters(filters).build())
                .whenComplete((ack, e) -> {
                    subscribedTopicFilter.clear();
                    if (e == null) {
                        recordUnsubscribeSuccess();
                    } else {
                        recordUnsubscribeFailure();
                    }
                    future.complete(null);
                });
        }
        return future;
    }

    @Override
    public CompletableFuture<Void> publish(byte[] payload, String topic, int qos, boolean isDup, boolean isRetain) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (!isConnectedState()) {
            result.completeExceptionally(new RuntimeException("Client not connected!"));
            recordPublishFailure("not connected");
            return result;
        }
        if (isMqtt5) {
            mqtt5Client.publish(Mqtt5Publish.builder()
                .topic(topic).payload(payload)
                .qos(MqttQos.fromCode(qos)).retain(isRetain)
                .build()
            ).whenComplete((r, e) -> {
                if (e != null) {
                    result.completeExceptionally(e);
                } else {
                    result.complete(null);
                }
            });
        } else {
            mqtt3Client.publish(Mqtt3Publish.builder()
                .topic(topic).payload(payload)
                .qos(MqttQos.fromCode(qos)).retain(isRetain)
                .build()
            ).whenComplete((r, e) -> {
                if (e != null) {
                    result.completeExceptionally(e);
                } else {
                    result.complete(null);
                }
            });
        }
        return result;
    }

    @Override
    public CompletableFuture<Void> internalConnect() {
        CompletableFuture<Void> connectFuture = new CompletableFuture<>();
        TaskStage stage = taskStage.get();
        long start = System.currentTimeMillis();
        log.info(
            "[HiveMQ-DETAIL] internalConnect called, clientId={}, taskStage={}, protocol={}, host={}, port={}, isMqtt5={}",
            clientConfig.getClientId(), stage, clientConfig.getProtocol(), clientConfig.getHost(),
            clientConfig.getPort(), isMqtt5);
        if (!canConnect(stage)) {
            log.warn("[HiveMQ-DETAIL] cannot connect: stage={}, clientId={}", stage, clientConfig.getClientId());
            return CompletableFuture.completedFuture(null);
        }
        if (!trySetReconnecting()) {
            log.warn("[HiveMQ-DETAIL] already reconnecting, skip, clientId={}", clientConfig.getClientId());
            return CompletableFuture.completedFuture(null);
        }
        try {
            log.info("[HiveMQ-DETAIL] Building SSL config for clientId={}", clientConfig.getClientId());
            MqttClientSslConfig sslConfig = buildSslConfig();
            log.info("[HiveMQ-DETAIL] SSL config built, sslConfig={}, clientId={}", sslConfig,
                clientConfig.getClientId());
            MqttClientTransportConfig transport = buildTransport(sslConfig);
            log.info("[HiveMQ-DETAIL] Transport config built, clientId={}", clientConfig.getClientId());
            MqttClientConnectedListener onConnected = context -> {
                clearReconnecting();
                log.info("[HiveMQ-DETAIL] onConnected callback fired, clientId={}, current status={}",
                    clientConfig.getClientId(), status);
                logConnectSuccess(System.currentTimeMillis() - start);
                recordConnectSuccess();

                if (!connectFuture.isDone()) {
                    log.info("[HiveMQ-DETAIL] onConnected completing connectFuture, clientId={}",
                        clientConfig.getClientId());
                    connectFuture.complete(null);
                }
                log.info("[HiveMQ-DETAIL] onConnected completed, clientId={}, new status={}",
                    clientConfig.getClientId(), status);
            };
            MqttClientDisconnectedListener onDisconnected = context -> {
                clearReconnecting();
                if (status == CLOSED) {
                    log.info("[HiveMQ-DETAIL] onDisconnected but status=CLOSED, ignore, clientId={}",
                        clientConfig.getClientId());
                    return;
                }
                MqttClientDisconnectedContext ctx = context;
                Throwable cause = ctx.getCause();
                String disconnectReason = classifyDisconnectReason(cause);
                log.warn("[HiveMQ-DETAIL] onDisconnected, clientId={}, reason={}, cause={}",
                    clientConfig.getClientId(), disconnectReason, cause);
                incrementMetricWithTags(BifroTaskMetric.UNEXPECTED_DISCONNECT_COUNT,
                    io.micrometer.core.instrument.Tags.of("reason", disconnectReason));
                logConnectFailure(System.currentTimeMillis() - start, cause);
                recordConnectFailure(cause);

                if (!connectFuture.isDone()) {
                    log.warn("[HiveMQ-DETAIL] onDisconnected failing connectFuture, clientId={}",
                        clientConfig.getClientId());
                    connectFuture.completeExceptionally(
                        new RuntimeException("Connection failed: " + (cause != null ? cause.getMessage() : "unknown")));
                }
            };
            if (isMqtt5) {
                log.info("[HiveMQ-DETAIL] Connecting MQTT5 client, clientId={}", clientConfig.getClientId());
                connectMqtt5(transport, onConnected, onDisconnected, connectFuture);
            } else {
                log.info("[HiveMQ-DETAIL] Connecting MQTT3 client, clientId={}", clientConfig.getClientId());
                connectMqtt3(transport, onConnected, onDisconnected, connectFuture);
            }
            log.info("[HiveMQ-DETAIL] connect initiated, returning future, clientId={}", clientConfig.getClientId());
        } catch (Exception e) {
            log.error("[HiveMQ-DETAIL] Exception in internalConnect, clientId={}", clientConfig.getClientId(), e);
            clearReconnecting();
            connectFuture.completeExceptionally(e);
        }
        return connectFuture;
    }

    private void connectMqtt5(MqttClientTransportConfig transport,
                              MqttClientConnectedListener onConnected,
                              MqttClientDisconnectedListener onDisconnected,
                              CompletableFuture<Void> connectFuture) {
        boolean useEmptyId = resolveEmptyClientId();
        String clientId = useEmptyId ? "" : clientConfig.getClientId();
        log.info("[HiveMQ-DETAIL] connectMqtt5: building client, clientId={}, emptyId={}", clientId, useEmptyId);
        mqtt5Client = MqttClient.builder()
            .useMqttVersion5()
            .identifier(clientId)
            .simpleAuth(buildMqtt5Auth())
            .transportConfig(transport)
            .addConnectedListener(onConnected)
            .addDisconnectedListener(onDisconnected)
            .buildAsync();
        log.info("[HiveMQ-DETAIL] connectMqtt5: client built, initiating connect, clientId={}", clientId);
        mqtt5Client.publishes(MqttGlobalPublishFilter.ALL,
            pub -> pubMsgListener.onPublishMessage(pub.getPayloadAsBytes(), false));
        mqtt5Client.connect(buildMqtt5Connect()).whenComplete((r, e) -> {
            log.info("[HiveMQ-DETAIL] connectMqtt5: connect result received, clientId={}, success={}, error={}",
                clientId, e == null, e != null ? e.getMessage() : null);
            if (e != null) {
                log.error("[HiveMQ-DETAIL] connectMqtt5: connect failed, clientId={}", clientId, e);
                recoverAfterConnectFailure(e).whenComplete((v, throwable) -> {
                    if (!connectFuture.isDone()) {
                        if (throwable != null) {
                            connectFuture.completeExceptionally(throwable);
                        } else {
                            connectFuture.complete(null);
                        }
                    }
                });
            } else {
                log.info(
                    "[HiveMQ-DETAIL] connectMqtt5: connect call returned success, waiting for onConnected callback, clientId={}",
                    clientId);

            }
        });
    }

    private void connectMqtt3(MqttClientTransportConfig transport,
                              MqttClientConnectedListener onConnected,
                              MqttClientDisconnectedListener onDisconnected,
                              CompletableFuture<Void> connectFuture) {
        boolean useEmptyId = resolveEmptyClientId();
        String clientId = useEmptyId ? "" : clientConfig.getClientId();
        log.info("[HiveMQ-DETAIL] connectMqtt3: building client, clientId={}, emptyId={}", clientId, useEmptyId);
        mqtt3Client = MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .simpleAuth(com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth.builder()
                .username(clientConfig.getUsername())
                .password(clientConfig.getPassword().getBytes(StandardCharsets.UTF_8))
                .build())
            .transportConfig(transport)
            .addConnectedListener(onConnected)
            .addDisconnectedListener(onDisconnected)
            .buildAsync();
        log.info("[HiveMQ-DETAIL] connectMqtt3: client built, initiating connect, clientId={}", clientId);
        mqtt3Client.publishes(MqttGlobalPublishFilter.ALL,
            pub -> pubMsgListener.onPublishMessage(pub.getPayloadAsBytes(), false));
        mqtt3Client.connect(buildMqtt3Connect()).whenComplete((r, e) -> {
            log.info("[HiveMQ-DETAIL] connectMqtt3: connect result received, clientId={}, success={}, error={}",
                clientId, e == null, e != null ? e.getMessage() : null);
            if (e != null) {
                log.error("[HiveMQ-DETAIL] connectMqtt3: connect failed, clientId={}", clientId, e);
                recoverAfterConnectFailure(e).whenComplete((v, throwable) -> {
                    if (!connectFuture.isDone()) {
                        if (throwable != null) {
                            connectFuture.completeExceptionally(throwable);
                        } else {
                            connectFuture.complete(null);
                        }
                    }
                });
            } else {
                log.info(
                    "[HiveMQ-DETAIL] connectMqtt3: connect call returned success, waiting for onConnected callback, clientId={}",
                    clientId);

            }
        });
    }

    private boolean resolveEmptyClientId() {
        if (clientConfig.isEmptyClientId() && !clientConfig.isCleanSession()) {
            log.warn("emptyClientId=true requires cleanSession=true, ignoring, clientId={}",
                clientConfig.getClientId());
            return false;
        }
        return clientConfig.isEmptyClientId();
    }

    private MqttClientTransportConfig buildTransport(MqttClientSslConfig sslConfig) {
        MqttClientTransportConfigBuilder builder = MqttClientTransportConfig.builder()
            .serverHost(clientConfig.getHost())
            .serverPort(clientConfig.getPort())
            .mqttConnectTimeout(clientConfig.getConnectTimeoutInMs(), TimeUnit.MILLISECONDS);
        if (clientConfig.getLocalAddress() != null && !clientConfig.getLocalAddress().isEmpty()) {
            builder = builder.localAddress(clientConfig.getLocalAddress());
        }
        if (clientConfig.getLocalPort() > 0) {
            builder = builder.localPort(clientConfig.getLocalPort());
        }
        if (sslConfig != null) {
            builder = builder.sslConfig(sslConfig);
        }
        return builder.build();
    }

    private MqttClientSslConfig buildSslConfig() throws Exception {
        if (!ConnectionUtil.isSSL(clientConfig.getProtocol())) {
            return null;
        }
        MqttClientSslConfigBuilder builder = MqttClientSslConfig.builder()
            .hostnameVerifier((host, session) -> true);

        if (clientConfig.getCaCertPem() == null) {

            builder = builder.trustManagerFactory(InsecureTrustManagerFactory.INSTANCE);
        } else {

            TrustManagerFactory tmf = buildTrustManagerFactory(clientConfig.getCaCertPem());
            builder = builder.trustManagerFactory(tmf);
        }
        if (clientConfig.getClientCertPem() != null && clientConfig.getClientKeyPem() != null) {
            KeyManagerFactory kmf = buildKeyManagerFactory(
                clientConfig.getClientCertPem(), clientConfig.getClientKeyPem());
            builder = builder.keyManagerFactory(kmf);
        }

        return builder.build();
    }

    private TrustManagerFactory buildTrustManagerFactory(String caPem) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert = (X509Certificate) cf.generateCertificate(
            new ByteArrayInputStream(caPem.getBytes(StandardCharsets.UTF_8)));
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("ca", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        return tmf;
    }

    private KeyManagerFactory buildKeyManagerFactory(String certPem, String keyPem) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert = cf.generateCertificate(
            new ByteArrayInputStream(certPem.getBytes(StandardCharsets.UTF_8)));
        String keyBase64 = keyPem
            .replaceAll("-----BEGIN.*?-----", "")
            .replaceAll("-----END.*?-----", "")
            .replaceAll("\\s", "");
        PrivateKey privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyBase64)));

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        char[] emptyPwd = new char[0];
        ks.setKeyEntry("client", privateKey, emptyPwd, new Certificate[] {cert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, emptyPwd);
        return kmf;
    }

    private Mqtt5SimpleAuth buildMqtt5Auth() {
        return Mqtt5SimpleAuth.builder()
            .username(clientConfig.getUsername())
            .password(clientConfig.getPassword().getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private Mqtt5Connect buildMqtt5Connect() {
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
                .build())
            .build();
    }

    private Mqtt3Connect buildMqtt3Connect() {
        Mqtt3ConnectBuilder builder = Mqtt3Connect.builder();
        builder = builder.cleanSession(clientConfig.isCleanSession())
            .keepAlive(clientConfig.getKeepAliveInSec());
        return builder.build();
    }

    private String classifyDisconnectReason(Throwable cause) {
        if (cause == null) {
            return "unknown";
        }
        String msg = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
        String type = cause.getClass().getSimpleName().toLowerCase();

        if (msg.contains("keep alive") || msg.contains("keepalive")) {
            return "keepalive_timeout";
        }

        if (type.contains("disconnect") || msg.contains("disconnect") || msg.contains("session expired")) {
            return "server_disconnect";
        }

        if (type.contains("ioexception") || type.contains("closedchannel") || type.contains("eofexception")
            || msg.contains("connection reset") || msg.contains("broken pipe") || msg.contains("closed")) {
            return "tcp_error";
        }

        if (type.contains("protocol") || msg.contains("protocol")) {
            return "protocol_error";
        }
        return "unknown";
    }
}
