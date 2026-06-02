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

package org.apache.bifromq.testsuite;

import org.apache.bifromq.testsuite.client.MQTTClientWrapper;
import org.apache.bifromq.testsuite.client.MqttClientFactory;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.constants.ConnectionStatus;
import org.apache.bifromq.testsuite.diagnostics.AsyncDiagnosticContext;
import org.apache.bifromq.testsuite.utils.TaskUtils;
import io.netty.channel.EventLoop;
import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class MqttClientTask {

    protected final Vertx vertx;
    @Getter
    protected final MqttClientConfig clientConfig;
    protected final ClientTaskConfig taskConfig;
    protected final String eventAddr;
    protected final AtomicReference<TaskStage> taskStage;

    @Getter
    protected MQTTClientWrapper mqttClientWrapper;
    protected EventLoop eventLoop;

    protected MqttClientTask(@NonNull Vertx vertx,
                             @NonNull ClientTaskConfig taskConfig,
                             @NonNull MqttClientConfig mqttClientConfig,
                             AtomicReference<TaskStage> taskStage) {
        this.vertx = vertx;
        this.taskConfig = taskConfig;
        this.clientConfig = mqttClientConfig;
        this.eventAddr = TaskUtils.getClientTaskAddr(taskConfig.getTaskId());
        this.eventLoop = ((VertxInternal) vertx).nettyEventLoopGroup().next();
        this.taskStage = taskStage;
        this.mqttClientWrapper = MqttClientFactory.createClient(
            vertx, taskConfig, mqttClientConfig, eventLoop, taskStage);
    }

    public abstract CompletableFuture<Void> connect();

    public OptionalLong getMessageCount() {
        return OptionalLong.empty();
    }

    public String getCId() {
        return mqttClientWrapper.getClientId();
    }

    public ConnectionStatus getConnectionStatus() {
        return mqttClientWrapper.getStatus();
    }

    public CompletableFuture<Void> close() {
        return mqttClientWrapper.close();
    }

    protected CompletableFuture<Void> connectWrapper(Consumer<ConnectionStatus> callback) {
        return mqttClientWrapper.connect(callback);
    }

    protected AsyncDiagnosticContext.Scope enterClientContext(String stage) {
        return AsyncDiagnosticContext.with(taskConfig.getTaskId(), taskConfig.getNodeId(), stage, getCId());
    }

    protected Runnable wrapClientContext(String stage, Runnable runnable) {
        return AsyncDiagnosticContext.wrap(
            new AsyncDiagnosticContext.Snapshot(taskConfig.getTaskId(), taskConfig.getNodeId(), stage, getCId()),
            runnable);
    }
}
