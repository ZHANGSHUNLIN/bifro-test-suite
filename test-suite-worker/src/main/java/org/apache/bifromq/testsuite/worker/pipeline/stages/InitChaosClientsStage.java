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

package org.apache.bifromq.testsuite.worker.pipeline.stages;

import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.ChaosClientTask;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.client.MqttClientConfigFactory;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;

@Slf4j
public class InitChaosClientsStage implements PipelineStage<TaskPipelineContext> {

    static final String CHAOS_CLIENTS_KEY = "chaosClients";

    @Override
    public String getName() {
        return "InitChaosClients";
    }

    @Override
    public String getLabel() {
        return Messages.get("pipeline.stage.initChaos");
    }

    @Override
    public TaskEvent getTriggerEvent() {
        return TaskEvent.ONGOING;
    }

    @Override
    public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();
        Vertx vertx = context.getVertx();

        vertx.executeBlocking(() -> {
            try (var ignored = context.enterStage(getName())) {
                MqttClientConfigFactory factory = context.getExecutionContext().mqttClientConfigFactory();
                int totalCount = context.getExecutionContext().totalClientCount();

                List<ChaosClientTask> clients = new ArrayList<>(totalCount);
                AtomicInteger subscribeCount = new AtomicInteger(context.getExecutionContext().thingIdStartAt());
                AtomicReference<TaskStage> stageRef = context.getStateMachine().getCurrentStateReference();

                for (int i = 0; i < totalCount; i++) {
                    if (context.isCancelled()) {
                        vertx.runOnContext(v -> context.wrapStage(getName(),
                            () -> future.complete(StageResult.failure(
                                "Task cancelled during chaos client initialization"))).run());
                        return null;
                    }
                    try {
                        MqttClientConfig mqttClientConfig = factory.create(i, subscribeCount);
                        ClientTaskConfig clientTaskConfig = context.getExecutionContext().newClientTaskConfig();

                        clients.add(new ChaosClientTask(vertx, clientTaskConfig, mqttClientConfig, stageRef));
                    } catch (Exception e) {
                        log.error("Failed to create chaos client at index {}: {}", i, e.getMessage());
                    }
                }

                context.getStageData().put(CHAOS_CLIENTS_KEY, clients);
                vertx.runOnContext(v -> context.wrapStage(getName(),
                    () -> future.complete(StageResult.success("Initialized " + clients.size() + " chaos clients")))
                    .run());
                return null;
            }
        });

        return future;
    }

    @Override
    public void onBefore(TaskPipelineContext context) {
        log.info("[Chaos] InitChaosClientsStage start, taskId={}",
            context.getExecutionContext().taskId());
    }

    @Override
    public void onAfter(TaskPipelineContext context, StageResult result) {
        log.info("[Chaos] InitChaosClientsStage end, result={}", result);
    }

    @Override
    public void onError(TaskPipelineContext context, Throwable error) {
        log.error("[Chaos] Error during chaos client initialization", error);
    }
}
