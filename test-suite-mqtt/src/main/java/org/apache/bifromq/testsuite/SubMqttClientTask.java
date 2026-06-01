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

import static org.apache.bifromq.testsuite.constants.ConnectionStatus.CONNECTED_FAILED;

import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.utils.PayloadUtils;
import io.micrometer.core.instrument.Tags;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.Json;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class SubMqttClientTask extends MqttClientTask {

    private static final Logger SUB_LOGGER = LoggerFactory.getLogger("subLogger");

    private final WorkerExecutor workerExecutor;
    private final boolean ownsWorkerExecutor;
    private final RecentLongSet receivedIndexes = new RecentLongSet();
    private final AtomicLong subCount = new AtomicLong(0);

    @Builder
    public SubMqttClientTask(@NonNull Vertx vertx,
                             @NonNull ClientTaskConfig taskConfig,
                             @NonNull MqttClientConfig mqttClientConfig,
                             AtomicReference<TaskStage> taskStage,
                             int workerPoolSize) {
        this(vertx, taskConfig, mqttClientConfig, taskStage,
            vertx.createSharedWorkerExecutor("client-worker", resolveWorkerPoolSize(workerPoolSize)), true);
    }

    public SubMqttClientTask(@NonNull Vertx vertx,
                             @NonNull ClientTaskConfig taskConfig,
                             @NonNull MqttClientConfig mqttClientConfig,
                             AtomicReference<TaskStage> taskStage,
                             @NonNull WorkerExecutor workerExecutor) {
        this(vertx, taskConfig, mqttClientConfig, taskStage, workerExecutor, false);
    }

    private SubMqttClientTask(@NonNull Vertx vertx,
                              @NonNull ClientTaskConfig taskConfig,
                              @NonNull MqttClientConfig mqttClientConfig,
                              AtomicReference<TaskStage> taskStage,
                              @NonNull WorkerExecutor workerExecutor,
                              boolean ownsWorkerExecutor) {
        super(vertx, taskConfig, mqttClientConfig, taskStage);
        this.workerExecutor = workerExecutor;
        this.ownsWorkerExecutor = ownsWorkerExecutor;

        this.mqttClientWrapper.setMessageListener(this::handlePublishMessage);
    }

    @Override
    public CompletableFuture<Void> connect() {
        return connectWrapper(connectionStatus -> wrapClientContext("StartConnClients", () -> {
            if (connectionStatus == CONNECTED_FAILED) {
                SUB_LOGGER.warn("Failed to connect subscriber, clientId={}, topicFilters={}",
                    getCId(), Json.encode(taskConfig.getTopicFilters()));
            }
        }).run());
    }

    @Override
    public OptionalLong getMessageCount() {
        return OptionalLong.of(subCount.get());
    }

    @Override
    public CompletableFuture<Void> close() {
        receivedIndexes.clear();
        CompletableFuture<Void> result = new CompletableFuture<>();
        super.close().whenComplete((v, closeError) ->
            closeOwnedWorkerExecutor().whenComplete((ignored, workerCloseError) -> {
                receivedIndexes.clear();
                if (closeError != null) {
                    result.completeExceptionally(closeError);
                } else if (workerCloseError != null) {
                    result.completeExceptionally(workerCloseError);
                } else {
                    result.complete(null);
                }
            }));
        return result;
    }

    private CompletableFuture<Void> closeOwnedWorkerExecutor() {
        if (!ownsWorkerExecutor) {
            return CompletableFuture.completedFuture(null);
        }
        return workerExecutor.close().toCompletionStage().toCompletableFuture();
    }

    private static int resolveWorkerPoolSize(int workerPoolSize) {
        return workerPoolSize > 0 ? workerPoolSize : Runtime.getRuntime().availableProcessors() * 2;
    }

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

    public CompletableFuture<List<Integer>> subscribe() {
        log.info("Subscribe topicFilters={}", taskConfig.getTopicFilters());
        CompletableFuture<List<Integer>> result = new CompletableFuture<>();
        mqttClientWrapper.subscribe(taskConfig.getTopicFilters())
            .whenComplete((grantQosList, ex) -> {
                if (ex != null) {
                    SUB_LOGGER.warn("Subscribe error, will NOT reconnect, clientId={}, topicFilters={}",
                        getCId(), Json.encode(taskConfig.getTopicFilters()), ex);
                    close();
                    result.completeExceptionally(ex);
                } else if (grantQosList.stream().anyMatch(q -> q == MqttQoS.FAILURE.value())) {
                    String message = String.format(
                        "Failed grant subscribe, clientId=%s, topicFilters=%s, grantQos=%s",
                        getCId(), Json.encode(taskConfig.getTopicFilters()), Json.encode(grantQosList));
                    SUB_LOGGER.warn(message);
                    close();
                    result.completeExceptionally(new IllegalStateException(message));
                } else {
                    SUB_LOGGER.trace("Success to sub, clientId={}, topicFilters={}",
                        getCId(), Json.encode(taskConfig.getTopicFilters()));
                    result.complete(grantQosList);
                }
            });
        return result;
    }

    public CompletableFuture<Void> unsubscribe() {
        log.info("Unsubscribe all, clientId={}", getCId());
        return mqttClientWrapper.unsubscribeAll();
    }

    private void handlePublishMessage(byte[] payload, boolean isDup) {
        SUB_LOGGER.debug("handlePublishMessage payload length: {} , isDup: {}", payload.length, isDup);

        String taskId = taskConfig.getTaskId();
        String nodeId = taskConfig.getNodeId();
        Tags tags = Tags.of("taskId", taskId, "nodeId", nodeId);
        MetricsHelper.counter(BifroTaskMetric.MESSAGE_RECEIVED_COUNT, tags);
        subCount.incrementAndGet();

        if (!supportsMessageLatency() || !PayloadUtils.isBifroPayload(payload)) {
            SUB_LOGGER.debug("Non-bifro payload (length={}), skip latency/dedup, clientId={}",
                payload.length, getCId());
            return;
        }

        long startTime = PayloadUtils.extractTimestamp(payload);
        long latency = System.nanoTime() - startTime;
        long index = PayloadUtils.extractIndex(payload);

        workerExecutor.executeBlocking(() -> {
            try (var ignored = enterClientContext("SubMessageReceive")) {
                String clientId = getCId();
                boolean isDuplicate = !receivedIndexes.add(index);
                if (isDuplicate) {
                    MetricsHelper.counter(BifroTaskMetric.MESSAGE_DUPLICATE_COUNT, tags);
                    SUB_LOGGER.debug("Duplicate message received, clientId={}, index={}", clientId, index);
                } else {
                    MetricsHelper.recordTimeNanos(BifroTaskMetric.MESSAGE_DELIVERY_LATENCY,
                        latency, "taskId", taskId, "nodeId", nodeId);
                }
                if (taskConfig.isSendLatencyEvent()) {
                    SUB_LOGGER.debug("Message latency: {} ms, clientId={}, index={}",
                        TimeUnit.NANOSECONDS.toMillis(latency), clientId, index);
                }
                return null;
            }
        });
    }

    private boolean supportsMessageLatency() {
        return taskConfig.getPayloadMode() == null || taskConfig.getPayloadMode() == PayloadMode.BIFRO;
    }
}
