/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite;

import static com.baidu.iot.test.suite.constants.CommonConstants.LATENCY;
import static com.baidu.iot.test.suite.constants.CommonConstants.TIMESTAMP;

import com.baidu.iot.test.suite.client.MQTTClientWrapper;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ClientTaskType;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.models.ClientTaskEvent;
import com.baidu.iot.test.suite.stats.TaskPubStatsManager;
import com.baidu.iot.test.suite.utils.PayloadUtils;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mafei01 in 3/10/21 11:08 AM
 */
@Slf4j
public class PubClientTask extends ClientTask {

    Logger pubLogger = LoggerFactory.getLogger("pubLogger");

    private final TaskPubStatsManager statsManager;
    private final byte[] payload;
    private final WorkerExecutor workerExecutor;

    @Builder
    public PubClientTask(@NonNull Vertx vertx,
                         @NonNull ClientTaskConfig taskConfig,
                         @NonNull MqttClientConfig mqttClientConfig,
                         @NonNull TaskPubStatsManager statsManager,
                         AtomicReference<TaskStage> taskStage) {
        super(vertx, taskConfig, mqttClientConfig, taskStage);
        this.statsManager = statsManager;
        this.payload = new byte[Math.max(taskConfig.getMessageSize(), (2 * Long.BYTES))];
        ThreadLocalRandom.current().nextBytes(payload);
        this.workerExecutor = vertx.createSharedWorkerExecutor("client-worker", 10);
    }

    @Override
    public void initTask() {
        ClientTaskEvent clientTaskEvent = ClientTaskEvent.builder()
                .clientId(getCId())
                .taskId(taskConfig.getTaskId())
                .eventType(ClientTaskEvent.EventType.CONNECT_RESULT)
                .clientTaskType(ClientTaskType.PUB)
                .build();
        connect(connectionStatus -> {
            if (connectionStatus == ConnectionStatus.CONNECTED_FAILED) {
                reportResult(clientTaskEvent, false);
            }
            reportResult(clientTaskEvent, true);
        }, (msg, dup) -> {
        });
    }

    @Override
    public void startTask(Consumer<MQTTClientWrapper> clientWrapperConsumer) {
        this.eventLoop.execute(() -> {
            // randomIntervalPublish();
            vertx.setTimer(ThreadLocalRandom.current().nextInt(1, taskConfig.getPubIntervalInMs()), e -> {
                // publish();
                long pubPeriodId = vertx.setPeriodic(taskConfig.getPubIntervalInMs(), event -> publish());
                vertx.setTimer(TimeUnit.SECONDS.toMillis(taskConfig.getStressDurationInSec()),
                        t -> vertx.cancelTimer(pubPeriodId));
            });
        });
    }

    public CompletableFuture<Void> close() {
        return super.close();
    }

    private void randomIntervalPublish() {
        vertx.setTimer(ThreadLocalRandom.current().nextInt(1, taskConfig.getPubIntervalInMs()), e -> publish());
    }

    private void publish() {
        long startNano = System.nanoTime();
        PayloadUtils.attachTimeAndIndex(payload, startNano, PayloadUtils.genIndex());
        String pubTopic = taskConfig.getPubTopic();
        if (taskConfig.isRandomPublishing()) {
            pubTopic = pubTopic + "/" + System.currentTimeMillis();
        }

        pubLogger.debug("publish payload: {}, topic: {}, qos: {}, isDup: {}, isRetain: {}",
                payload.length, pubTopic, taskConfig.getMessageQos().value(), false, taskConfig.isRetain());
        mqttClientWrapper.publish(payload, pubTopic, taskConfig.getMessageQos().value(), false,
                        taskConfig.isRetain())
                .whenComplete((v, e) -> {
                    if (e != null) {
                        log.warn("Publish message failed, taskId={}, clientId={}", taskConfig.getTaskId(),
                                clientConfig.getClientId(), e);
                        recordPublishFailed();
                    } else {
                        recordPublishSuccess(startNano, System.nanoTime() - startNano);
                    }
                });
        statsManager.recordExpect();
    }


    private void recordPublishSuccess(Long startTime, long latency) {
        workerExecutor.executeBlocking(p -> {
            statsManager.recordSuccess(latency, TimeUnit.NANOSECONDS);
            if (taskConfig.isSendLatencyEvent()) {
                ClientTaskEvent clientTaskEvent = ClientTaskEvent.builder()
                        .clientId(getCId())
                        .taskId(taskConfig.getTaskId())
                        .eventType(ClientTaskEvent.EventType.C2S_RESULT)
                        .clientTaskType(ClientTaskType.PUB)
                        .build();
                // write eventBus
                clientTaskEvent.putDetail(LATENCY, latency);
                clientTaskEvent.putDetail(TIMESTAMP, startTime);
                reportResult(clientTaskEvent, true);
            }
            p.complete();
        });
    }

    private void recordPublishFailed() {
        statsManager.recordPubFail();
        if (taskConfig.isSendLatencyEvent()) {
            ClientTaskEvent clientTaskEvent = ClientTaskEvent.builder()
                    .clientId(getCId())
                    .taskId(taskConfig.getTaskId())
                    .eventType(ClientTaskEvent.EventType.C2S_RESULT)
                    .clientTaskType(ClientTaskType.PUB)
                    .build();
            reportResult(clientTaskEvent, false);
        }
    }
}
