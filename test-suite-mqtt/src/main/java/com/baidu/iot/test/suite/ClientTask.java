
package com.baidu.iot.test.suite;

import com.baidu.iot.test.suite.client.HiveMQTTClientWrapper;
import com.baidu.iot.test.suite.client.MQTTClientWrapper;
import com.baidu.iot.test.suite.client.VertxMQTTClientWrapper;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.utils.TaskUtils;
import io.netty.channel.EventLoop;
import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ClientTask {

    protected final Vertx vertx;
    protected final MqttClientConfig clientConfig;
    protected final ClientTaskConfig taskConfig;
    protected final String eventAddr;
    protected final AtomicReference<TaskStage> taskStage;

    protected MQTTClientWrapper mqttClientWrapper;
    protected EventLoop eventLoop;

    ClientTask(@NonNull Vertx vertx,
               @NonNull ClientTaskConfig taskConfig,
               @NonNull MqttClientConfig mqttClientConfig,
               AtomicReference<TaskStage> taskStage) {
        this.vertx = vertx;
        this.taskConfig = taskConfig;
        this.clientConfig = mqttClientConfig;
        this.eventAddr = TaskUtils.getClientTaskAddr(taskConfig.getTaskId());
        this.eventLoop = ((VertxInternal) vertx).nettyEventLoopGroup().next();
        this.taskStage = taskStage;

        mqttClientWrapper = taskConfig.isMqtt5() ?
                new HiveMQTTClientWrapper(vertx, taskConfig, mqttClientConfig, eventLoop,taskStage) :
                new VertxMQTTClientWrapper(vertx, taskConfig, mqttClientConfig, eventLoop,taskStage);
    }

    public CompletableFuture<MQTTClientWrapper> initTask() {
        return CompletableFuture.completedFuture(mqttClientWrapper);
    }

    public  CompletableFuture<MQTTClientWrapper> startTask(){
        return CompletableFuture.completedFuture(mqttClientWrapper);
    }

    public String getCId() {
        return mqttClientWrapper.getClientId();
    }

    /**
     * Shutdown this mqtt client
     *
     */
    public CompletableFuture<Void> close() {
        return mqttClientWrapper.close();
    }


    protected CompletableFuture<Void> connect(Consumer<ConnectionStatus> callback, IPubMsgListener msgListener) {
        return mqttClientWrapper.connect(callback, msgListener);
    }



}
