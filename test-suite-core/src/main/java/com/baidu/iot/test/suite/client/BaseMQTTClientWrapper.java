package com.baidu.iot.test.suite.client;

import com.baidu.iot.test.suite.IPubMsgListener;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.metric.BifroTaskMetric;
import com.baidu.iot.test.suite.metric.MetricsHelper;
import com.baidu.iot.test.suite.models.TopicFilter;
import io.micrometer.core.instrument.Tags;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTED_FAILED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTING;

@Slf4j
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


    protected BaseMQTTClientWrapper(Vertx vertx, MqttClientConfig clientConfig, ClientTaskConfig taskConfig) {
        this.clientConfig = clientConfig;
        this.vertx = vertx;
        this.taskConfig = taskConfig;
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
        return null;
    }

    protected void tryRecoverConnect() {
        if (reconnectAttempts.getAndIncrement() < clientConfig.getReconnectMaxAttempts()) {
            MetricsHelper.counter(BifroTaskMetric.RECONNECT_COUNT,
                    Tags.of("taskId", taskConfig.getTaskId()));

            reconnectTimer =
                    vertx.setTimer(ThreadLocalRandom.current().nextLong(clientConfig.getReconnectIntervalInMs(),
                                    clientConfig.getReconnectIntervalInMs() * 2L),
                            t -> internalConnect());
        } else {
            MetricsHelper.counter(BifroTaskMetric.RECONNECT_LIMIT_EXCEEDED,
                    Tags.of("taskId", taskConfig.getTaskId()));
            status = CONNECTED_FAILED;
            log.error("MqttClient connect failed after {} retries, clientId={}, localAddr={}",
                    clientConfig.getReconnectMaxAttempts(),
                    clientConfig.getClientId(), clientConfig.getLocalAddress());
            connectCallback.accept(CONNECTED_FAILED);
        }
    }

    abstract void internalConnect();


}
