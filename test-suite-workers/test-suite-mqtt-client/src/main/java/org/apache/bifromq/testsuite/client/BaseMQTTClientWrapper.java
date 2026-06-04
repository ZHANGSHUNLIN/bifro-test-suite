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

package org.apache.bifromq.testsuite.client;

import static org.apache.bifromq.testsuite.constants.ConnectionStatus.CLOSED;
import static org.apache.bifromq.testsuite.constants.ConnectionStatus.CONNECTED;
import static org.apache.bifromq.testsuite.constants.ConnectionStatus.CONNECTED_FAILED;
import static org.apache.bifromq.testsuite.constants.ConnectionStatus.CONNECTING;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.bifromq.testsuite.IPubMsgListener;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.constants.ConnectionStatus;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.models.TopicFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public abstract class BaseMQTTClientWrapper implements MQTTClientWrapper {
    protected static final Logger CONN_LOGGER = LoggerFactory.getLogger("connLogger");
    protected static final Logger PUB_LOGGER = LoggerFactory.getLogger("pubLogger");
    protected static final Logger SUB_LOGGER = LoggerFactory.getLogger("subLogger");
    private static final int CONNECT_FAILURE_WARN_SAMPLE_INTERVAL = 50;
    private static final int MAX_LOCAL_PORT_FALLBACK_ATTEMPTS = 16;
    private static final ConcurrentHashMap<String, AtomicInteger> LOCAL_PORT_FALLBACK_COUNTERS =
        new ConcurrentHashMap<>();
    protected final MqttClientConfig clientConfig;
    protected final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicInteger localPortFallbackAttempts = new AtomicInteger(0);
    private final AtomicBoolean finalConnectFailureRecorded = new AtomicBoolean(false);
    private final AtomicBoolean finalFailureResourcesReleased = new AtomicBoolean(false);
    private final AtomicBoolean recoveryScheduled = new AtomicBoolean(false);
    protected final Vertx vertx;
    protected final ClientTaskConfig taskConfig;
    protected final AtomicBoolean reconnecting = new AtomicBoolean(false);
    protected final Set<TopicFilter> subscribedTopicFilter;
    protected final AtomicReference<TaskStage> taskStage;
    protected final AtomicLong connectedAt = new AtomicLong(0);
    private final AtomicBoolean activeConnectionMetric = new AtomicBoolean(false);

    protected final Tags baseTags;
    protected ConnectionStatus status;
    protected Consumer<ConnectionStatus> connectCallback;
    protected IPubMsgListener pubMsgListener;
    protected long reconnectTimer;
    protected volatile Timer.Sample connectLatencySample;

    protected BaseMQTTClientWrapper(Vertx vertx, MqttClientConfig clientConfig,
                                    ClientTaskConfig taskConfig,
                                    AtomicReference<TaskStage> taskStage) {
        this.clientConfig = clientConfig;
        this.vertx = vertx;
        this.taskConfig = taskConfig;
        this.taskStage = taskStage;
        this.status = ConnectionStatus.INIT;
        this.subscribedTopicFilter = createTopicFilterSet();
        this.pubMsgListener = (payload, isDup) -> {
        };
        this.baseTags = Tags.of("taskId", taskConfig.getTaskId(), "nodeId", taskConfig.getNodeId());
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String classifyConnectFailure(Throwable cause) {
        if (cause == null) {
            return "unknown";
        }
        String message = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
        if (message.contains("cannot assign requested address")) {
            return "local_addr_or_ephemeral_port_exhausted";
        }
        if (message.contains("address already in use")) {
            return "local_port_address_in_use";
        }
        if (message.contains("connection refused")) {
            return "connection_refused";
        }
        if (message.contains("connect timed out") || message.contains("timeout")) {
            return "connect_timeout";
        }
        if (message.contains("no route to host")) {
            return "no_route_to_host";
        }
        return cause.getClass().getSimpleName();
    }

    private static boolean isLocalPortBindFailure(String reasonType) {
        return "local_addr_or_ephemeral_port_exhausted".equals(reasonType)
            || "local_port_address_in_use".equals(reasonType)
            || "BindException".equals(reasonType);
    }

    private static String tagValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static void withTaskMdc(String taskId, Runnable action) {
        String oldTaskId = MDC.get("taskId");
        if (taskId != null) {
            MDC.put("taskId", taskId);
        } else {
            MDC.remove("taskId");
        }
        try {
            action.run();
        } finally {
            if (oldTaskId != null) {
                MDC.put("taskId", oldTaskId);
            } else {
                MDC.remove("taskId");
            }
        }
    }

    @Override
    public void setMessageListener(IPubMsgListener listener) {
        this.pubMsgListener = listener;
    }

    @Override
    public CompletableFuture<Void> connect(Consumer<ConnectionStatus> connectCallback) {
        if (status == CONNECTED || status == CONNECTING || status == CONNECTED_FAILED || isConnected()) {
            CONN_LOGGER.warn("mqttClient is already connected, clientId={}", clientConfig.getClientId());
            return CompletableFuture.failedFuture(new RuntimeException("mqttClient is already connected"));
        }
        this.connectCallback = connectCallback;
        status = ConnectionStatus.CONNECTING;

        connectLatencySample = MetricsHelper.startTimer();
        return internalConnect();
    }

    @Override
    public abstract CompletableFuture<List<Integer>> subscribe(Set<TopicFilter> topicFilters);

    protected void recordConnectSuccess() {
        if (status == CONNECTED_FAILED || status == CLOSED) {
            CONN_LOGGER.warn("Ignore connect success after terminal state, taskId={}, clientId={}, status={}",
                taskConfig.getTaskId(), clientConfig.getClientId(), status);
            releaseClientResourcesAfterFinalFailureOnce();
            return;
        }
        incrementMetric(BifroTaskMetric.CONNECT_SUCCESS_COUNT);

        if (connectLatencySample != null) {
            MetricsHelper.stopTimer(connectLatencySample, BifroTaskMetric.CONNECT_LATENCY,
                "taskId", taskConfig.getTaskId(), "nodeId", taskConfig.getNodeId(), "result", "success");
            connectLatencySample = null;
        }

        resetReconnectAttempts();
        status = CONNECTED;
        connectedAt.set(System.currentTimeMillis());
        setActiveConnectionGauge(true);
        connectCallback.accept(CONNECTED);
    }

    protected void recordConnectFailure() {
        recordConnectFailure(null);
    }

    protected void recordConnectFailure(Throwable cause) {
        if (status == CONNECTED_FAILED || status == CLOSED) {
            CONN_LOGGER.debug("Ignore connect failure after terminal state, taskId={}, clientId={}, status={}",
                taskConfig.getTaskId(), clientConfig.getClientId(), status);
            return;
        }
        incrementMetric(BifroTaskMetric.CONNECT_EXCEPTION_COUNT);

        if (connectLatencySample != null) {
            MetricsHelper.stopTimer(connectLatencySample, BifroTaskMetric.CONNECT_LATENCY,
                "taskId", taskConfig.getTaskId(), "nodeId", taskConfig.getNodeId(), "result", "failure");
            connectLatencySample = null;
        }
        status = ConnectionStatus.DISCONNECTED;
        setActiveConnectionGauge(false);
        connectCallback.accept(ConnectionStatus.DISCONNECTED);
        recoverAfterConnectFailure(cause);
    }

    protected boolean recordFinalConnectFailure() {
        if (!finalConnectFailureRecorded.compareAndSet(false, true)) {
            return false;
        }
        incrementMetric(BifroTaskMetric.CONNECT_EXCEPTION_COUNT);
        if (connectLatencySample != null) {
            MetricsHelper.stopTimer(connectLatencySample, BifroTaskMetric.CONNECT_LATENCY,
                "taskId", taskConfig.getTaskId(), "nodeId", taskConfig.getNodeId(), "result", "failure");
            connectLatencySample = null;
        }
        status = CONNECTED_FAILED;
        setActiveConnectionGauge(false);
        releaseClientResourcesAfterFinalFailureOnce();
        return true;
    }

    protected CompletableFuture<Void> recoverAfterConnectFailure(Throwable cause) {
        if (status == CONNECTED_FAILED || status == CLOSED) {
            CONN_LOGGER.debug("Skip reconnect after terminal state, taskId={}, clientId={}, status={}",
                taskConfig.getTaskId(), clientConfig.getClientId(), status);
            return CompletableFuture.failedFuture(new RuntimeException("MqttClient connection is terminal: " + status));
        }
        if (!recoveryScheduled.compareAndSet(false, true)) {
            CONN_LOGGER.debug("Skip duplicate reconnect recovery, taskId={}, clientId={}, status={}",
                taskConfig.getTaskId(), clientConfig.getClientId(), status);
            return CompletableFuture.failedFuture(new RuntimeException("MqttClient reconnect recovery is scheduled"));
        }
        if (cause != null) {
            String reasonType = classifyConnectFailure(rootCause(cause));
            rotateLocalPortOnBindFailure(reasonType);
        }
        clearReconnecting();
        return tryRecoverConnect();
    }

    protected void recordDisconnect() {
        incrementMetric(BifroTaskMetric.DISCONNECT_COMPLETION_COUNT);
        setActiveConnectionGauge(false);
    }

    protected void recordSubscribeFailure() {
        incrementMetric(BifroTaskMetric.SUBSCRIBE_COMPLETION_EXPIRATION_COUNT);
        incrementMetricWithTags(BifroTaskMetric.SUBSCRIBE_FAILURE_COUNT,
            Tags.of("reason", "timeout"));
    }

    protected void recordSubscribeSuccess() {
        incrementMetric(BifroTaskMetric.SUBSCRIBE_COMPLETION_COUNT);
    }

    protected void recordSubscribeSuccess(int subscriptionCount) {
        if (subscriptionCount <= 0) {
            return;
        }
        incrementMetric(BifroTaskMetric.SUBSCRIBE_COMPLETION_COUNT, subscriptionCount);
    }

    protected void recordPublishFailure(String reason) {
        incrementMetricWithTags(BifroTaskMetric.PUBLISH_COMPLETION_EXPIRATION_COUNT, Tags.of("state", status.name()));
        incrementMetricWithTags(BifroTaskMetric.PUBLISH_FAILURE_COUNT,
            Tags.of("reason", reason != null ? reason : "error"));
    }

    protected void recordPublishSuccess() {
        incrementMetric(BifroTaskMetric.PUBLISH_COMPLETION_COUNT);
    }

    protected void recordUnsubscribeFailure() {
        incrementMetric(BifroTaskMetric.UNSUBSCRIBE_COMPLETION_EXPIRATION_COUNT);
    }

    protected void recordUnsubscribeSuccess() {
        incrementMetric(BifroTaskMetric.UNSUBSCRIBE_COMPLETION_COUNT);
    }

    protected void recordDisconnectFailure() {
        incrementMetric(BifroTaskMetric.DISCONNECT_COMPLETION_EXPIRATION_COUNT);
    }

    protected void recordCloseSuccess() {
        incrementMetric(BifroTaskMetric.CLIENT_CLOSE_COUNT);
        setActiveConnectionGauge(false);
    }

    protected void recordCloseFailure() {
        incrementMetric(BifroTaskMetric.CLIENT_CLOSE_EXCEPTION_COUNT);
        setActiveConnectionGauge(false);
    }

    protected void incrementMetric(BifroTaskMetric metric) {
        MetricsHelper.counter(metric, baseTags);
    }

    protected void incrementMetric(BifroTaskMetric metric, double amount) {
        MetricsHelper.counter(metric, amount,
            "taskId", taskConfig.getTaskId(),
            "nodeId", taskConfig.getNodeId());
    }

    protected void incrementMetricWithTags(BifroTaskMetric metric, Tags additionalTags) {
        MetricsHelper.counter(metric, baseTags.and(additionalTags));
    }

    private void setActiveConnectionGauge(boolean active) {
        boolean changed = active
            ? activeConnectionMetric.compareAndSet(false, true)
            : activeConnectionMetric.compareAndSet(true, false);
        if (!changed) {
            return;
        }
        MetricsHelper.gaugeDelta(BifroTaskMetric.CLIENT_ACTIVE_GAUGE, active ? 1.0 : -1.0,
            "taskId", taskConfig.getTaskId(),
            "nodeId", taskConfig.getNodeId(),
            "clientType", taskConfig.getType() == null ? "unknown" : taskConfig.getType().name());
    }

    protected boolean isConnectedState() {
        return status == CONNECTED && isConnected();
    }

    protected boolean canConnect(TaskStage stage) {
        if (status == CONNECTED_FAILED) {
            CONN_LOGGER.warn("MqttClient connect not allowed after final failure, clientId={}",
                clientConfig.getClientId());
            return false;
        }
        if (stage == TaskStage.SHUTDOWN || stage == TaskStage.STOPPED) {
            MetricsHelper.counter(BifroTaskMetric.ILLEGAL_TASK_STATE,
                baseTags.and(Tags.of("stage", stage.name())));
            CONN_LOGGER.warn("MqttClient connect not allowed, clientId={}, taskStage={}", clientConfig.getClientId(),
                stage);
            return false;
        }
        if (status == CLOSED) {
            MetricsHelper.counter(BifroTaskMetric.ILLEGAL_STATE_CLIENT_CLOSED, baseTags);
            CONN_LOGGER.warn("MqttClient is closed, clientId={}", clientConfig.getClientId());
            return false;
        }
        return true;
    }

    protected void logConnectSuccess(long duration) {
        CONN_LOGGER.debug("MqttClient connect success, clientId={}, costs={}ms, hashcode: {}",
            clientConfig.getClientId(), duration, this.hashCode());
    }

    protected void logConnectFailure(long duration, Throwable cause) {
        Throwable root = rootCause(cause);
        String reason = root != null && root.getMessage() != null ? root.getMessage() : "unknown";
        String reasonType = classifyConnectFailure(root);
        recordLocalPortBindFailureIfNeeded(reasonType);
        int attempt = reconnectAttempts.get() + 1;
        boolean warnLog = shouldLogConnectFailureWarn(attempt);
        withTaskMdc(taskConfig.getTaskId(), () -> {
            if (warnLog) {
                CONN_LOGGER.warn(
                    "MqttClient connect failed in {}th attempt, taskId={}, clientId={}, localAddr={}, localPort={}, "
                        + "remote={}:{}, reasonType={}, reason={}, costs={}ms",
                    attempt, taskConfig.getTaskId(), clientConfig.getClientId(),
                    clientConfig.getLocalAddress(), clientConfig.getLocalPort(), clientConfig.getHost(),
                    clientConfig.getPort(),
                    reasonType, reason, duration);
            } else {
                CONN_LOGGER.debug(
                    "MqttClient connect failed (sampled), attempt={}, taskId={}, clientId={}, localAddr={}, "
                        + "localPort={}, remote={}:{}, reasonType={}, reason={}, costs={}ms",
                    attempt, taskConfig.getTaskId(), clientConfig.getClientId(),
                    clientConfig.getLocalAddress(), clientConfig.getLocalPort(), clientConfig.getHost(),
                    clientConfig.getPort(), reasonType, reason, duration);
            }
        });
    }

    protected void recordLocalPortBindFailureIfNeeded(String reasonType) {
        if (!isLocalPortBindFailure(reasonType)) {
            return;
        }
        incrementMetricWithTags(BifroTaskMetric.LOCAL_PORT_BIND_FAILURE_COUNT,
            Tags.of(
                "reasonType", reasonType,
                "localAddress", tagValue(clientConfig.getLocalAddress()),
                "localPortEnabled", Boolean.toString(clientConfig.getLocalPort() > 0),
                "remoteHost", tagValue(clientConfig.getHost()),
                "remotePort", String.valueOf(clientConfig.getPort())
            ));
    }

    protected void logDisconnect() {
        CONN_LOGGER.debug("MQTT client connection closed, taskId={}, clientId={}, status before close={}",
            taskConfig.getTaskId(), clientConfig.getClientId(), status);
    }

    protected Set<TopicFilter> createTopicFilterSet() {
        return new java.util.HashSet<>();
    }

    protected CompletableFuture<Void> tryRecoverConnect() {
        CompletableFuture<Void> connFuture = new CompletableFuture<>();
        if (status == CONNECTED_FAILED || status == CLOSED) {
            connFuture.completeExceptionally(new RuntimeException("MqttClient connection is terminal: " + status));
            return connFuture;
        }
        if (reconnectAttempts.getAndIncrement() < clientConfig.getReconnectMaxAttempts()) {
            MetricsHelper.counter(BifroTaskMetric.RECONNECT_COUNT, baseTags);

            reconnectTimer =
                vertx.setTimer(ThreadLocalRandom.current().nextLong(clientConfig.getReconnectIntervalInMs(),
                        clientConfig.getReconnectIntervalInMs() * 2L),
                    t -> {
                        clearRecoveryScheduled();
                        internalConnect().whenComplete((v, throwable) -> {
                            if (throwable != null) {
                                Throwable root = rootCause(throwable);
                                String reasonType = classifyConnectFailure(root);
                                recordLocalPortBindFailureIfNeeded(reasonType);
                                int attempt = reconnectAttempts.get();
                                String reason =
                                    root != null && root.getMessage() != null ? root.getMessage() : "unknown";
                                boolean warnLog = shouldLogConnectFailureWarn(attempt);
                                withTaskMdc(taskConfig.getTaskId(), () -> {
                                    if (warnLog) {
                                        CONN_LOGGER.warn(
                                            "MqttClient reconnect failed, attempt={}, taskId={}, clientId={}, "
                                                + "localAddr={}, localPort={}, remote={}:{}, reasonType={}, reason={}",
                                            attempt, taskConfig.getTaskId(), clientConfig.getClientId(),
                                            clientConfig.getLocalAddress(), clientConfig.getLocalPort(),
                                            clientConfig.getHost(),
                                            clientConfig.getPort(), reasonType, reason);
                                    } else {
                                        CONN_LOGGER.debug(
                                            "MqttClient reconnect failed (sampled), attempt={}, taskId={}, "
                                                + "clientId={}, localAddr={}, localPort={}, remote={}:{}, "
                                                + "reasonType={}, reason={}",
                                            attempt, taskConfig.getTaskId(), clientConfig.getClientId(),
                                            clientConfig.getLocalAddress(), clientConfig.getLocalPort(),
                                            clientConfig.getHost(), clientConfig.getPort(), reasonType, reason);
                                    }
                                });
                                connFuture.completeExceptionally(throwable);
                            } else {
                                connFuture.complete(null);
                            }
                        });
                    });
        } else {
            if (recordFinalConnectFailure()) {
                MetricsHelper.counter(BifroTaskMetric.RECONNECT_LIMIT_EXCEEDED, baseTags);
                withTaskMdc(taskConfig.getTaskId(), () -> CONN_LOGGER.error(
                    "MqttClient connect failed after {} retries, taskId={}, clientId={}, localAddr={}, localPort={}, "
                        + "remote={}:{}, reasonType=retry_limit_exceeded",
                    clientConfig.getReconnectMaxAttempts(),
                    taskConfig.getTaskId(), clientConfig.getClientId(), clientConfig.getLocalAddress(),
                    clientConfig.getLocalPort(), clientConfig.getHost(), clientConfig.getPort()));
                connectCallback.accept(CONNECTED_FAILED);
            }
            connFuture.completeExceptionally(new RuntimeException("MqttClient connect failed after "));
        }
        return connFuture;
    }

    protected void releaseClientResourcesAfterFinalFailure() {
    }

    private void releaseClientResourcesAfterFinalFailureOnce() {
        if (finalFailureResourcesReleased.compareAndSet(false, true)) {
            releaseClientResourcesAfterFinalFailure();
        }
    }

    protected boolean rotateLocalPortOnBindFailure(String reasonType) {
        if (!"local_port_address_in_use".equals(reasonType)
            || clientConfig.getLocalPort() <= 0
            || clientConfig.getLocalPortRangeConfig() == null
            || !clientConfig.getLocalPortRangeConfig().isEnabled()) {
            return false;
        }
        int fallbackIndex = localPortFallbackAttempts.getAndIncrement();
        if (fallbackIndex >= MAX_LOCAL_PORT_FALLBACK_ATTEMPTS) {
            CONN_LOGGER.warn(
                "Local source port fallback exhausted, taskId={}, clientId={}, localAddr={}, localPort={}, "
                    + "remote={}:{}, fallbackAttempts={}",
                taskConfig.getTaskId(), clientConfig.getClientId(), clientConfig.getLocalAddress(),
                clientConfig.getLocalPort(), clientConfig.getHost(), clientConfig.getPort(), fallbackIndex);
            return false;
        }
        int oldPort = clientConfig.getLocalPort();
        try {
            int nextPort = nextFallbackLocalPort(oldPort);
            clientConfig.setLocalPort(nextPort);
            CONN_LOGGER.warn(
                "Rotated local source port after bind conflict, taskId={}, clientId={}, localAddr={}, oldPort={}, "
                    + "newPort={}, remote={}:{}, fallbackIndex={}",
                taskConfig.getTaskId(), clientConfig.getClientId(), clientConfig.getLocalAddress(), oldPort, nextPort,
                clientConfig.getHost(), clientConfig.getPort(), fallbackIndex);
            return true;
        } catch (IllegalStateException e) {
            CONN_LOGGER.warn(
                "Local source port fallback failed, taskId={}, clientId={}, localAddr={}, localPort={}, "
                    + "remote={}:{}, reason={}",
                taskConfig.getTaskId(), clientConfig.getClientId(), clientConfig.getLocalAddress(), oldPort,
                clientConfig.getHost(), clientConfig.getPort(), e.getMessage());
            return false;
        }
    }

    private int nextFallbackLocalPort(int oldPort) {
        AtomicInteger counter = LOCAL_PORT_FALLBACK_COUNTERS.computeIfAbsent(localPortFallbackKey(),
            ignored -> new AtomicInteger());
        for (int i = 0; i < MAX_LOCAL_PORT_FALLBACK_ATTEMPTS; i++) {
            int nextPort = LocalPortAllocator.fallback(counter.getAndIncrement(),
                clientConfig.getLocalPortRangeConfig());
            if (nextPort != oldPort) {
                return nextPort;
            }
        }
        throw new IllegalStateException("Source port fallback could not find a port different from " + oldPort);
    }

    private String localPortFallbackKey() {
        LocalPortRangeConfig config = clientConfig.getLocalPortRangeConfig().normalized();
        return tagValue(clientConfig.getLocalAddress()) + ":" + config.getStartPort() + "-" + config.getEndPort()
            + ":" + config.getExcludedPorts();
    }

    protected void cancelReconnectTimer() {
        vertx.cancelTimer(reconnectTimer);
    }

    protected void resetReconnectAttempts() {
        reconnectAttempts.set(0);
    }

    protected boolean trySetReconnecting() {
        return reconnecting.compareAndSet(false, true);
    }

    protected void clearReconnecting() {
        reconnecting.set(false);
    }

    protected void clearRecoveryScheduled() {
        recoveryScheduled.set(false);
    }

    public long getConnectedAt() {
        return connectedAt.get();
    }

    private boolean shouldLogConnectFailureWarn(int attempt) {
        int maxAttempts = clientConfig.getReconnectMaxAttempts();
        return attempt <= 3
            || attempt == maxAttempts
            || attempt % CONNECT_FAILURE_WARN_SAMPLE_INTERVAL == 0;
    }

    abstract CompletableFuture<Void> internalConnect();

}
