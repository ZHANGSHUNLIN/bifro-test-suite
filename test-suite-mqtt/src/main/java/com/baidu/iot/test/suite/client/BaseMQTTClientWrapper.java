package com.baidu.iot.test.suite.client;

import com.baidu.iot.test.suite.IPubMsgListener;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.metric.BifroTaskMetric;
import com.baidu.iot.test.suite.metric.MetricsHelper;
import com.baidu.iot.test.suite.models.TopicFilter;
import io.micrometer.core.instrument.Tags;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.baidu.iot.test.suite.constants.ConnectionStatus.CLOSED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTED_FAILED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTING;

public abstract class BaseMQTTClientWrapper implements MQTTClientWrapper {
    protected Logger connLogger = LoggerFactory.getLogger("connLogger");
    protected Logger pubLogger = LoggerFactory.getLogger("pubLogger");
    protected Logger subLogger = LoggerFactory.getLogger("subLogger");
    protected ConnectionStatus status;
    protected Consumer<ConnectionStatus> connectCallback;
    protected IPubMsgListener pubMsgListener;
    protected final MqttClientConfig clientConfig;
    protected final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    protected long reconnectTimer;
    protected final Vertx vertx;
    protected final ClientTaskConfig taskConfig;
    protected final AtomicBoolean reconnecting = new AtomicBoolean(false);
    protected final Set<TopicFilter> subscribedTopicFilter;
    protected final AtomicReference<TaskStage> taskStage;


    protected BaseMQTTClientWrapper(Vertx vertx, MqttClientConfig clientConfig,
                                    ClientTaskConfig taskConfig,
                                    AtomicReference<TaskStage> taskStage) {
        this.clientConfig = clientConfig;
        this.vertx = vertx;
        this.taskConfig = taskConfig;
        this.taskStage = taskStage;
        this.status = ConnectionStatus.INIT;
        this.subscribedTopicFilter = createTopicFilterSet();
    }


    @Override
    public CompletableFuture<Void> connect(Consumer<ConnectionStatus> connectCallback, IPubMsgListener pubMsgListener) {
        if (status == CONNECTED || status == CONNECTING || isConnected()) {
            connLogger.warn("mqttClient is already connected, clientId={}", clientConfig.getClientId());
            return CompletableFuture.failedFuture(new RuntimeException("mqttClient is already connected"));
        }
        this.connectCallback = connectCallback;
        this.pubMsgListener = pubMsgListener;
        status = ConnectionStatus.CONNECTING;
       return internalConnect();
    }

    @Override
    public CompletableFuture<List<Integer>> subscribe(Set<TopicFilter> topicFilters) {
        CompletableFuture<List<Integer>> subFuture = new CompletableFuture<>();
        if (!isConnectedState()) {
            subLogger.warn("Subscribe cancelled for mqttClient is not connected, clientId={}", clientConfig.getClientId());
            subFuture.completeExceptionally(new RuntimeException("mqttClient is not connected"));
            incrementMetric(BifroTaskMetric.SUBSCRIBE_COMPLETION_EXPIRATION_COUNT);
            return subFuture;
        }
        return null;
    }

    /**
     * 记录连接成功的指标
     */
    protected void recordConnectSuccess() {
        incrementMetric(BifroTaskMetric.CONNECT_SUCCESS_COUNT);
        status = CONNECTED;
        connectCallback.accept(CONNECTED);
    }

    /**
     * 记录连接失败的指标
     */
    protected void recordConnectFailure() {
        incrementMetric(BifroTaskMetric.CONNECT_EXCEPTION_COUNT);
        status = ConnectionStatus.DISCONNECTED;
        connectCallback.accept(ConnectionStatus.DISCONNECTED);
        tryRecoverConnect();
    }

    /**
     * 记录断开连接的指标
     */
    protected void recordDisconnect() {
        incrementMetric(BifroTaskMetric.DISCONNECT_COMPLETION_COUNT);
    }

    /**
     * 记录订阅失败的指标
     */
    protected void recordSubscribeFailure() {
        incrementMetric(BifroTaskMetric.SUBSCRIBE_COMPLETION_EXPIRATION_COUNT);
    }

    /**
     * 记录订阅成功的指标
     */
    protected void recordSubscribeSuccess() {
        incrementMetric(BifroTaskMetric.SUBSCRIBE_COMPLETION_COUNT);
    }

    /**
     * 记录发布失败的指标
     */
    protected void recordPublishFailure(String reason) {
        incrementMetricWithTags(BifroTaskMetric.PUBLISH_COMPLETION_EXPIRATION_COUNT, Tags.of("state", status.name()));
    }

    /**
     * 记录发布成功的指标
     */
    protected void recordPublishSuccess() {
        incrementMetric(BifroTaskMetric.PUBLISH_COMPLETION_COUNT);
    }

    /**
     * 记录取消订阅的指标
     */
    protected void recordUnsubscribeFailure() {
        incrementMetric(BifroTaskMetric.UNSUBSCRIBE_COMPLETION_EXPIRATION_COUNT);
    }

    /**
     * 记录取消订阅成功的指标
     */
    protected void recordUnsubscribeSuccess() {
        incrementMetric(BifroTaskMetric.UNSUBSCRIBE_COMPLETION_COUNT);
    }

    /**
     * 记录断开连接失败的指标
     */
    protected void recordDisconnectFailure() {
        incrementMetric(BifroTaskMetric.DISCONNECT_COMPLETION_EXPIRATION_COUNT);
    }

    /**
     * 记录关闭客户端成功的指标
     */
    protected void recordCloseSuccess() {
        incrementMetric(BifroTaskMetric.CLIENT_CLOSE_COUNT);
    }

    /**
     * 记录关闭客户端失败的指标
     */
    protected void recordCloseFailure() {
        incrementMetric(BifroTaskMetric.CLIENT_CLOSE_EXCEPTION_COUNT);
    }

    /**
     * 递增指标计数器
     */
    protected void incrementMetric(BifroTaskMetric metric) {
        MetricsHelper.counter(metric, Tags.of("taskId", taskConfig.getTaskId()));
    }

    /**
     * 递增带标签的指标计数器
     */
    protected void incrementMetricWithTags(BifroTaskMetric metric, Tags additionalTags) {
        Tags baseTags = Tags.of("taskId", taskConfig.getTaskId());
        MetricsHelper.counter(metric, baseTags.and(additionalTags));
    }

    /**
     * 检查是否处于已连接状态
     */
    protected boolean isConnectedState() {
        return status == CONNECTED && isConnected();
    }

    /**
     * 检查连接前的任务状态
     * 允许连接的状态：初始化阶段(连接客户端)、进行中
     * 不允许连接的状态：正在关闭、已关闭、已停止
     */
    protected boolean canConnect(TaskStage stage) {
        if (stage == TaskStage.SHUTDOWN || stage == TaskStage.STOPPED) {
            MetricsHelper.counter(BifroTaskMetric.ILLEGAL_TASK_STATE,
                    Tags.of("stage", stage.name(), "taskId", taskConfig.getTaskId()));
            connLogger.warn("MqttClient connect not allowed, clientId={}, taskStage={}", clientConfig.getClientId(), stage);
            return false;
        }
        if (status == CLOSED) {
            MetricsHelper.counter(BifroTaskMetric.ILLEGAL_STATE_CLIENT_CLOSED,
                    Tags.of("taskId", taskConfig.getTaskId()));
            connLogger.warn("MqttClient is closed, clientId={}", clientConfig.getClientId());
            return false;
        }
        return true;
    }

    /**
     * 记录连接日志（开始）
     */
    protected void logConnectStart(long startTime) {
        connLogger.debug("Starting MQTT client connection, clientId={}", clientConfig.getClientId());
    }

    /**
     * 记录连接成功日志
     */
    protected void logConnectSuccess(long duration) {
        connLogger.debug("MqttClient connect success, clientId={}, costs={}ms, hashcode: {}",
                clientConfig.getClientId(), duration, this.hashCode());
    }

    /**
     * 记录连接失败日志
     */
    protected void logConnectFailure(long duration, Throwable cause) {
        connLogger.warn("MqttClient connect failed in {}th attempt, clientId={}, localAddr={}, reason={}, costs={}ms",
                reconnectAttempts.get() + 1, clientConfig.getClientId(), clientConfig.getLocalAddress(),
                cause != null ? cause.getMessage() : "unknown", duration, cause);
    }

    /**
     * 记录断开连接日志
     */
    protected void logDisconnect() {
        connLogger.debug("MQTT client connection closed, taskId={}, clientId={}, status before close={}",
                taskConfig.getTaskId(), clientConfig.getClientId(), status);
    }

    /**
     * 创建 TopicFilter 集合，供子类覆盖
     */
    protected Set<TopicFilter> createTopicFilterSet() {
        return new java.util.HashSet<>();
    }

    protected CompletableFuture<Void> tryRecoverConnect() {
        CompletableFuture<Void> connFuture = new CompletableFuture<>();
        if (reconnectAttempts.getAndIncrement() < clientConfig.getReconnectMaxAttempts()) {
            MetricsHelper.counter(BifroTaskMetric.RECONNECT_COUNT,
                    Tags.of("taskId", taskConfig.getTaskId()));

            reconnectTimer =
                    vertx.setTimer(ThreadLocalRandom.current().nextLong(clientConfig.getReconnectIntervalInMs(),
                                    clientConfig.getReconnectIntervalInMs() * 2L),
                            t -> internalConnect()
                                    .whenComplete((v, throwable) -> {
                                        if (throwable != null) {
                                            connLogger.error("MqttClient reconnect failed, clientId={}, localAddr={}",
                                                    clientConfig.getClientId(),
                                                    clientConfig.getLocalAddress(), throwable);
                                            connFuture.completeExceptionally(throwable);
                                        } else {
                                            connFuture.complete(null);
                                        }
                                    }));
        } else {
            MetricsHelper.counter(BifroTaskMetric.RECONNECT_LIMIT_EXCEEDED,
                    Tags.of("taskId", taskConfig.getTaskId()));
            status = CONNECTED_FAILED;
            connLogger.error("MqttClient connect failed after {} retries, clientId={}, localAddr={}",
                    clientConfig.getReconnectMaxAttempts(),
                    clientConfig.getClientId(), clientConfig.getLocalAddress());
            connectCallback.accept(CONNECTED_FAILED);
            connFuture.completeExceptionally(new RuntimeException("MqttClient connect failed after "));
        }
        return connFuture;
    }

    /**
     * 清理重连定时器
     */
    protected void cancelReconnectTimer() {
        vertx.cancelTimer(reconnectTimer);
    }

    /**
     * 重置重连次数
     */
    protected void resetReconnectAttempts() {
        reconnectAttempts.set(0);
    }

    /**
     * 获取重连是否正在进行
     */
    protected boolean isReconnecting() {
        return reconnecting.get();
    }

    /**
     * 尝试设置重连状态
     */
    protected boolean trySetReconnecting() {
        return reconnecting.compareAndSet(false, true);
    }

    /**
     * 清除重连状态
     */
    protected void clearReconnecting() {
        reconnecting.set(false);
    }

    abstract CompletableFuture<Void> internalConnect();


}
