
package com.baidu.iot.test.suite;

import static com.baidu.iot.test.suite.constants.CommonConstants.STATUS;

import com.baidu.iot.test.suite.client.MQTTClientWrapper;
import io.vertx.core.Vertx;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ClientTaskType;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.models.ClientTaskEvent;

/**
 *
 */
@Slf4j
public class ConnClientTask extends ClientTask {


    @Builder
    public ConnClientTask(@NonNull Vertx vertx,
                          @NonNull ClientTaskConfig taskConfig,
                          @NonNull MqttClientConfig mqttClientConfig,
                          AtomicReference<TaskStage> taskStage) {
        super(vertx, taskConfig, mqttClientConfig, taskStage);
    }


    @Override
    public CompletableFuture<MQTTClientWrapper> initTask() {
        long start = System.nanoTime();
//        todo 记录指标耗时
        return connect(connectionStatus -> {
        }, (payload, isDup) -> {
        }).thenApply(r -> mqttClientWrapper);
    }

    public CompletableFuture<Void> close() {
        // TODO do some thing more
        return super.close();
    }

}
