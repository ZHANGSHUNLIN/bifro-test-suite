/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite;

import static com.baidu.iot.test.suite.constants.CommonConstants.STATUS;

import com.baidu.iot.test.suite.client.MQTTClientWrapper;
import io.vertx.core.Vertx;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ClientTaskType;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.models.ClientTaskEvent;
import com.baidu.iot.test.suite.stats.TaskConnStatsManager;

/**
 * Created by mafei01 in 3/10/21 11:08 AM
 */
@Slf4j
public class ConnClientTask extends ClientTask {

    private final TaskConnStatsManager statsManager;

    @Builder
    public ConnClientTask(@NonNull Vertx vertx,
                          @NonNull ClientTaskConfig taskConfig,
                          @NonNull MqttClientConfig mqttClientConfig,
                          @NonNull TaskConnStatsManager statsManager) {
        super(vertx, taskConfig, mqttClientConfig);
        this.statsManager = statsManager;
    }

    @Override
    public void initTask() {
    }

    @Override
    public void startTask(Consumer<MQTTClientWrapper> mqttClientWrapperConsumer) {
        ClientTaskEvent clientTaskEvent = ClientTaskEvent.builder()
                .clientId(getCId())
                .taskId(taskConfig.getTaskId())
                .clientTaskType(ClientTaskType.CONN)
                .build();
        long start = System.nanoTime();
         connect(new Consumer<>() {
            final AtomicInteger count = new AtomicInteger();

            @Override
            public void accept(ConnectionStatus connectionStatus) {
                if (mqttClientWrapperConsumer != null) {
                    mqttClientWrapperConsumer.accept(mqttClientWrapper);
                }
                clientTaskEvent.setEventType(ClientTaskEvent.EventType.CONNECT_STATUS);
                clientTaskEvent.putDetail(STATUS, connectionStatus.name());
                report(clientTaskEvent);
                if (count.getAndIncrement() == 0) {
                    clientTaskEvent.setEventType(ClientTaskEvent.EventType.CONNECT_RESULT);
                    clientTaskEvent.clearDetail();
                    if (connectionStatus == ConnectionStatus.CONNECTED_FAILED) {
                        statsManager.recordConnFail();
                        reportResult(clientTaskEvent, false);
                    } else {
                        statsManager.recordSuccess((System.nanoTime() - start), TimeUnit.NANOSECONDS);
                        reportResult(clientTaskEvent, true);
                    }
                }
            }
        }, (payload, isDup) -> {});
    }

    public CompletableFuture<Void> close() {
        // TODO do some thing more
        return super.close();
    }

}
