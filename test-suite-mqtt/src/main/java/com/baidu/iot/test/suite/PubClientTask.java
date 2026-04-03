

package com.baidu.iot.test.suite;

import com.baidu.iot.test.suite.client.MQTTClientWrapper;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ClientTaskType;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.models.ClientTaskEvent;
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
 *
 */
@Slf4j
public class PubClientTask extends ClientTask {

    Logger pubLogger = LoggerFactory.getLogger("pubLogger");

    private Long pubPeriodId;

    private final byte[] payload;

    @Builder
    public PubClientTask(@NonNull Vertx vertx,
                         @NonNull ClientTaskConfig taskConfig,
                         @NonNull MqttClientConfig mqttClientConfig,
                         AtomicReference<TaskStage> taskStage) {
        super(vertx, taskConfig, mqttClientConfig, taskStage);
        this.payload = new byte[Math.max(taskConfig.getMessageSize(), (2 * Long.BYTES))];
        ThreadLocalRandom.current().nextBytes(payload);
    }

    @Override
    public CompletableFuture<MQTTClientWrapper> initTask() {
        return connect(connectionStatus -> {
        }, (msg, dup) -> {
        }).thenApply(r -> mqttClientWrapper);
    }

    @Override
    public CompletableFuture<MQTTClientWrapper> startTask() {
        this.eventLoop.execute(() -> {
            // randomIntervalPublish();
            vertx.setTimer(ThreadLocalRandom.current().nextInt(1, taskConfig.getPubIntervalInMs()), e -> {
                // publish();
                 pubPeriodId = vertx.setPeriodic(taskConfig.getPubIntervalInMs(), event -> publish());
//                vertx.setTimer(TimeUnit.SECONDS.toMillis(taskConfig.getStressDurationInSec()),
//                        t -> vertx.cancelTimer(pubPeriodId));
            });
        });
        return CompletableFuture.completedFuture(mqttClientWrapper);
    }

    public CompletableFuture<Void> close() {
        if (pubPeriodId != null) {
            vertx.cancelTimer(pubPeriodId);
        }
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
                    } else {
                    }
                });
        // todo 记录ok的指标
    }


}
