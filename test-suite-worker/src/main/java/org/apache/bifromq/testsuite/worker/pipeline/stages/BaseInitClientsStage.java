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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.client.MqttClientConfigFactory;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;

@Slf4j
public abstract class BaseInitClientsStage implements PipelineStage<TaskPipelineContext> {

    private volatile CompletableFuture<StageResult> stageFuture;

    public TaskEvent getTriggerEvent() {
        return null;
    }

    abstract int clientSize(TaskPipelineContext context);

    abstract String clientType();

    String clientIdTypePrefix() {
        return "";
    }

    int localPortClientIndex(TaskPipelineContext context, int index) {
        return index;
    }

    abstract MqttClientTask buildClientTask(
        int index,
        Vertx vertx,
        TaskPipelineContext context,
        ClientTaskConfig clientTaskConfig,
        MqttClientConfig mqttClientConfig,
        AtomicReference<TaskStage> sAtomicReference
    );

    @Override
    public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();
        this.stageFuture = future;
        Vertx vertx = context.getVertx();

        vertx.executeBlocking(() -> {
            try (var ignored = context.enterStage(getName())) {

                Map<String, MqttClientTask> clientTaskMap = taskClientMap(context);

                MqttClientConfigFactory factory = context.getExecutionContext().mqttClientConfigFactory();

                String validationError = context.getExecutionContext().validateClientInitialization();
                if (validationError != null) {
                    log.error("Configuration validation failed: {}", validationError);
                    vertx.runOnContext(v -> context.wrapStage(getName(),
                        () -> future.complete(StageResult.failure(validationError))).run());
                    return null;
                }

                AtomicInteger subscribeCount = new AtomicInteger(context.getExecutionContext().thingIdStartAt());
                int clientTotalCount = clientSize(context);
                String typePrefix = clientIdTypePrefix();
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger failureCount = new AtomicInteger(0);
                StringBuilder errorMessages = new StringBuilder();
                String taskId = context.getExecutionContext().taskId();
                String nodeId = context.getExecutionContext().nodeId();

                MetricsHelper.gauge(BifroTaskMetric.CLIENT_PLANNED_GAUGE, clientTotalCount,
                    "taskId", taskId, "nodeId", nodeId, "clientType", clientType());

                for (int i = 0; i < clientTotalCount; i++) {
                    if (context.isCancelled()) {
                        vertx.runOnContext(v -> context.wrapStage(getName(),
                            () -> future.complete(StageResult.failure("Task cancelled during initialization"))).run());
                        return null;
                    }

                    try {
                        MqttClientConfig mqttClientConfig = typePrefix.isEmpty()
                            ? factory.create(i, localPortClientIndex(context, i), subscribeCount)
                            : factory.create(typePrefix, i, localPortClientIndex(context, i), subscribeCount);

                        ClientTaskConfig clientTaskConfig = context.getExecutionContext().newClientTaskConfig();

                        MqttClientTask clientTask =
                            buildClientTask(i, vertx, context, clientTaskConfig, mqttClientConfig,
                                context.getStateMachine().getCurrentStateReference());

                        clientTaskMap.put(mqttClientConfig.getClientId(), clientTask);
                        successCount.incrementAndGet();

                        MetricsHelper.counter(BifroTaskMetric.CLIENT_CREATED_COUNT,
                            io.micrometer.core.instrument.Tags.of(
                                "taskId", taskId,
                                "nodeId", nodeId,
                                "clientType", clientType()));
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        log.error("Failed to initialize client at index {}: {}", i, e.getMessage());
                        errorMessages.append(String.format("client[%d]: %s; ", i, e.getMessage()));

                        MetricsHelper.counter(BifroTaskMetric.CLIENT_FAILURE_COUNT,
                            io.micrometer.core.instrument.Tags.of(
                                "taskId", taskId,
                                "nodeId", nodeId,
                                "clientType", clientType(),
                                "reason", "init_failed"));
                    }
                }

                int finalSuccessCount = successCount.get();
                int finalFailureCount = failureCount.get();
                MetricsHelper.gauge(BifroTaskMetric.CLIENT_READY_GAUGE, finalSuccessCount,
                    "taskId", taskId, "nodeId", nodeId, "clientType", clientType());

                if (clientTotalCount == 0) {

                    log.info("No {} clients to initialize, skipping stage", clientType());
                    vertx.runOnContext(v -> context.wrapStage(getName(), () -> future.complete(StageResult.success(
                        String.format("No %s clients to initialize", clientType())))).run());
                } else if (finalSuccessCount == 0) {

                    log.error("All {} clients failed to initialize. Errors: {}", clientTotalCount, errorMessages);
                    vertx.runOnContext(v -> context.wrapStage(getName(), () -> future.complete(StageResult.failure(
                        String.format("All %d clients failed. First errors: %s",
                            clientTotalCount,
                            errorMessages.length() > 200 ? errorMessages.substring(0, 200) + "..." :
                                errorMessages.toString())))).run());
                } else {
                    log.info("Initialized {} clients ({} succeeded, {} failed)", clientTotalCount, finalSuccessCount,
                        finalFailureCount);
                    vertx.runOnContext(v -> context.wrapStage(getName(), () -> {
                        if (finalFailureCount > 0) {
                            future.complete(StageResult.success(
                                String.format("Initialized %d clients (%d failed)",
                                    finalSuccessCount, finalFailureCount)));
                        } else {
                            future.complete(StageResult.success(
                                String.format("Initialized %d clients", finalSuccessCount)));
                        }
                    }).run());
                }
                return null;
            }
        });

        return future;
    }

    @Override
    public void onBefore(TaskPipelineContext context) {
        log.info("Starting to initialize connection clients, taskId: {}", context.getExecutionContext().taskId());
    }

    @Override
    public void onError(TaskPipelineContext context, Throwable error) {
        log.error("Error during connection client initialization", error);
    }

    @Override
    public CompletableFuture<Void> cancel(TaskPipelineContext context) {

        CompletableFuture<StageResult> f = this.stageFuture;
        if (f != null && !f.isDone()) {
            log.debug("BaseInitClientsStage cancelled, completing future early");
            f.complete(StageResult.failure("Task cancelled during initialization"));
        }
        return CompletableFuture.completedFuture(null);
    }

    abstract Map<String, MqttClientTask> taskClientMap(TaskPipelineContext context);

}
