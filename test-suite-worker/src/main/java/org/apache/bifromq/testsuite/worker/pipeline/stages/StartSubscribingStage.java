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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.SubMqttClientTask;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageCancelSnapshot;
import org.apache.bifromq.testsuite.pipeline.StageExecutionScope;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;
import org.apache.bifromq.testsuite.worker.ratelimit.DynamicRateController;
import org.apache.bifromq.testsuite.worker.ratelimit.IRateLimiter;

@Slf4j
public class StartSubscribingStage implements PipelineStage<TaskPipelineContext> {

    private static final int MIN_SUBSCRIBE_DYNAMIC_QPS = 1;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile IRateLimiter rateLimiter;
    private volatile DynamicRateController dynamicRateController;

    @Override
    public String getName() {
        return "StartSubscribing";
    }

    @Override
    public String getLabel() {
        return Messages.get("pipeline.stage.startSubscribing");
    }

    @Override
    public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
        StageExecutionScope scope = context.stageScopeOrCreate(getName());
        Map<String, MqttClientTask> subClients = context.getExecutionContext().subClients();
        String taskId = context.getExecutionContext().taskId();
        if (subClients == null || subClients.isEmpty()) {
            log.info("No subscribers to subscribe, taskId={}", taskId);
            scope.complete(StageResult.success("No subscribers"));
            return scope.result();
        }
        rateLimiter = context.getExecutionContext().subscribeRateLimiter();
        scope.register("subscribe-rate-limiter", () -> {
            if (rateLimiter != null) {
                rateLimiter.dispose();
            }
        });

        MqttClientTask[] clients = subClients.values().toArray(new MqttClientTask[0]);
        String nodeId = context.getExecutionContext().nodeId();

        startDynamicQpsSchedulerIfNeeded(context);
        scope.register("subscribe-dynamic-qps", this::stopDynamicQpsScheduler);

        ConcurrentLinkedQueue<CompletableFuture<Void>> subscribeFutures = new ConcurrentLinkedQueue<>();
        log.info("Starting subscriptions: {} clients, taskId={}", clients.length, taskId);

        CompletableFuture<Void> rateLimitFuture = rateLimiter.executeWithRateLimit(clients.length, index -> {
            if (context.isCancelled()) {
                return CompletableFuture.completedFuture(null);
            }
            MqttClientTask client = clients[index];
            if (!(client instanceof SubMqttClientTask subClient)) {
                failureCount.incrementAndGet();
                CompletableFuture<Void> failedFuture = CompletableFuture.failedFuture(new IllegalStateException(
                    "Client is not subscriber: " + client.getCId()));
                CompletableFuture<Void> trackedFuture = scope.track("subscribe-" + client.getCId(), failedFuture);
                subscribeFutures.add(trackedFuture);
                return trackedFuture;
            }
            CompletableFuture<Void> subscribeFuture = subClient.subscribe()
                .thenAccept(grantQos -> successCount.incrementAndGet())
                .exceptionally(error -> {
                    failureCount.incrementAndGet();
                    log.warn("Subscribe failed, taskId={}, clientId={}, reason={}",
                        taskId, subClient.getCId(), error.getMessage(), error);
                    return null;
                });
            CompletableFuture<Void> trackedFuture = scope.track("subscribe-" + subClient.getCId(), subscribeFuture);
            subscribeFutures.add(trackedFuture);
            return trackedFuture;
        }).whenComplete((unused, error) -> {
            if (error != null) {
                stopDynamicQpsScheduler();
                MetricsHelper.freezeTimerSnapshot(BifroTaskMetric.SUBSCRIBE_LATENCY,
                    "taskId", taskId, "nodeId", nodeId);
                if (!scope.isCancelled()) {
                    scope.complete(StageResult.failure(error));
                }
                return;
            }
            CompletableFuture.allOf(subscribeFutures.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, subscribeError) -> {
                    if (!scope.isCancelled()) {
                        completeStage(taskId, nodeId, scope);
                    }
                });
        });
        scope.track("subscribe-rate-limit-dispatch", rateLimitFuture);

        return scope.result();
    }

    @Override
    public void onBefore(TaskPipelineContext context) {
        log.info("Preparing to subscribe {} clients, taskId={}",
            context.getExecutionContext().subClients().size(), context.getExecutionContext().taskId());
    }

    @Override
    public CompletableFuture<Void> cancel(TaskPipelineContext context) {
        StageExecutionScope scope = context.stageScopeOrCreate(getName());
        scope.cancel();
        StageCancelSnapshot snapshot = scope.snapshot();
        log.info("Subscribe stage cancelled, success={}, failed={}, scopeStarted={}, scopeCompleted={}, "
                + "scopePending={}",
            successCount.get(), failureCount.get(), snapshot.started(), snapshot.completed(), snapshot.pending());
        return CompletableFuture.completedFuture(null);
    }

    private void startDynamicQpsSchedulerIfNeeded(TaskPipelineContext context) {
        QpsStrategy strategy = context.getExecutionContext().subscribeQpsStrategy();
        if (strategy == null || !strategy.isDynamic()) {
            return;
        }
        dynamicRateController = new DynamicRateController("subscribe", rateLimiter, strategy,
            System.currentTimeMillis(), MIN_SUBSCRIBE_DYNAMIC_QPS);
        dynamicRateController.start();
    }

    private void stopDynamicQpsScheduler() {
        if (dynamicRateController != null) {
            dynamicRateController.stop();
        }
    }

    private void completeStage(String taskId, String nodeId, StageExecutionScope scope) {
        stopDynamicQpsScheduler();
        MetricsHelper.freezeTimerSnapshot(BifroTaskMetric.SUBSCRIBE_LATENCY,
            "taskId", taskId, "nodeId", nodeId);
        int success = successCount.get();
        int failed = failureCount.get();
        if (failed > 0 && success == 0) {
            scope.complete(StageResult.failure(
                String.format("All %d subscribers failed to subscribe", failed)));
        } else if (failed > 0) {
            scope.complete(StageResult.success(
                String.format("Subscribers subscribed: success=%d, failed=%d", success, failed)));
        } else {
            scope.complete(StageResult.success(
                String.format("Subscribers subscribed: success=%d", success)));
        }
    }
}
