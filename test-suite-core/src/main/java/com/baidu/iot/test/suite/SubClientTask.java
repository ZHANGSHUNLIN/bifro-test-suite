/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite;

import static com.baidu.iot.test.suite.constants.CommonConstants.LATENCY;
import static com.baidu.iot.test.suite.constants.CommonConstants.TIMESTAMP;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTED;
import static com.baidu.iot.test.suite.constants.ConnectionStatus.CONNECTED_FAILED;

import com.baidu.iot.test.suite.client.MQTTClientWrapper;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ClientTaskType;
import com.baidu.iot.test.suite.models.ClientTaskEvent;
import com.baidu.iot.test.suite.stats.TaskSubStatsManager;
import com.baidu.iot.test.suite.utils.PayloadUtils;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.Json;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class SubClientTask extends ClientTask {

    Logger subLogger = LoggerFactory.getLogger("subLogger");

    private final TaskSubStatsManager statsManager;
    private final WorkerExecutor workerExecutor;

    @Builder
    public SubClientTask(@NonNull Vertx vertx,
                         @NonNull ClientTaskConfig taskConfig,
                         @NonNull MqttClientConfig mqttClientConfig,
                         @NonNull TaskSubStatsManager statsManager,
                         AtomicReference<TaskStage> taskStage) {
        super(vertx, taskConfig, mqttClientConfig, taskStage);
        this.statsManager = statsManager;
        this.workerExecutor = vertx.createSharedWorkerExecutor("client-worker", 10);
    }

    public void initTask() {
        ClientTaskEvent clientTaskEvent = ClientTaskEvent.builder()
            .clientId(getCId())
            .taskId(taskConfig.getTaskId())
            .eventType(ClientTaskEvent.EventType.CONNECT_RESULT)
            .clientTaskType(ClientTaskType.SUB)
            .build();
        connect(connectionStatus -> {
            if (connectionStatus == CONNECTED_FAILED) {
                subLogger.warn("Failed to conn & subscribe, clientId={}, topicFilters={}",
                    getCId(), Json.encode(taskConfig.getTopicFilters()));
                reportResult(clientTaskEvent, false);
            } else if (connectionStatus == CONNECTED) {
                mqttClientWrapper.subscribe(taskConfig.getTopicFilters())
                    .whenComplete((grantQosList, ex) -> {
                        if (ex != null) {
                            subLogger.warn("Subscribe error, clientId={}, topicFilters={}",
                                getCId(), Json.encode(taskConfig.getTopicFilters()), ex);
                            mqttClientWrapper.disconnect();
                        } else if (grantQosList.stream().anyMatch(q -> q == MqttQoS.FAILURE.value())) {
                            subLogger.warn("Failed grant subscribe, clientId={}, topicFilters={}, grantQos={}",
                                getCId(), Json.encode(taskConfig.getTopicFilters()), Json.encode(grantQosList));
                            mqttClientWrapper.disconnect();
                        } else {
                            subLogger.debug("Success to sub, clientId={}, topicFilters={}",
                                getCId(), Json.encode(taskConfig.getTopicFilters()));
                            reportResult(clientTaskEvent, true);
                        }
                    });
            }
        }, this::handlePublishMessage);
    }

    public void startTask(Consumer<MQTTClientWrapper> clientWrapperConsumer) {
        if (clientWrapperConsumer != null) {
            clientWrapperConsumer.accept(mqttClientWrapper);
        }
    }

    public CompletableFuture<Void> close() {
        // TODO do some thing more
        return super.close();
    }

    // for test bed
    public CompletableFuture<Void> disconnect() {
        CompletableFuture<Void> disconnectFuture = new CompletableFuture<>();
        if (mqttClientWrapper != null) {
            this.mqttClientWrapper.disconnect().whenComplete((v, e) -> {
                if (e != null) {
                    log.warn("Failed to close mqtt client, clientId={}", getCId(), e);
                }
                disconnectFuture.complete(null);
            });
        } else {
            disconnectFuture.complete(null);
        }
        return disconnectFuture;
    }

    // for test bed
    public CompletableFuture<List<Integer>> subscribe() {
        log.info("Subscribe topicFilters={}", taskConfig.getTopicFilters());
        return mqttClientWrapper.subscribe(taskConfig.getTopicFilters());
    }

    // for test bed
    public CompletableFuture<Void> unsubscribe() {
        log.info("Unsubscribe All");
        return mqttClientWrapper.unsubscribeAll();
    }

    private void handlePublishMessage(byte[] payload, boolean isDup) {
        subLogger.debug("handlePublishMessage payload length: {} , isDup: {}", payload.length, isDup);
        long startTime = PayloadUtils.extractTimestamp(payload);
        long latency = System.nanoTime() - startTime;
        // TODO Use index to check message, if repeated
        // long index = PayloadUtils.extractIndex(message.payload().getBytes());
        workerExecutor.executeBlocking(() -> {
            if (isDup) {
                statsManager.recordSubDuplicate();
            } else {
                statsManager.recordSuccess(latency, TimeUnit.NANOSECONDS);
                if (taskConfig.isSendLatencyEvent()) {
                    ClientTaskEvent clientTaskEvent = ClientTaskEvent.builder()
                        .clientId(getCId())
                        .taskId(taskConfig.getTaskId())
                        .eventType(ClientTaskEvent.EventType.S2C_RESULT)
                        .clientTaskType(ClientTaskType.SUB)
                        .build();
                    // write eventBus
                    clientTaskEvent.putDetail(LATENCY, latency);
                    clientTaskEvent.putDetail(TIMESTAMP, startTime);
                    reportResult(clientTaskEvent, true);
                }
            }
            return null;
        });
    }
}
