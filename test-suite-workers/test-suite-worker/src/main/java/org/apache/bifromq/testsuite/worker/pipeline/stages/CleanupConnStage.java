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

import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.Constants;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;
import org.apache.bifromq.testsuite.worker.ratelimit.DynamicRateController;
import org.apache.bifromq.testsuite.worker.ratelimit.FiniteDispatchPlan;
import org.apache.bifromq.testsuite.worker.ratelimit.FiniteDispatchScheduler;
import org.apache.bifromq.testsuite.worker.ratelimit.IRateLimiter;

@Slf4j
public class CleanupConnStage implements PipelineStage<TaskPipelineContext> {

    private static final long CLIENT_CLOSE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    private final String clientTag;
    private final long clientCloseTimeoutMs;
    private final AtomicLong stageStartMs = new AtomicLong(0);

    private DynamicRateController dynamicRateController;
    private FiniteDispatchScheduler finiteDispatchScheduler;

    public CleanupConnStage(String clientTag) {
        this(clientTag, CLIENT_CLOSE_TIMEOUT_MS);
    }

    CleanupConnStage(String clientTag, long clientCloseTimeoutMs) {
        this.clientTag = clientTag;
        this.clientCloseTimeoutMs = clientCloseTimeoutMs;
    }

    @Override
    public String getName() {
        return "CleanupConn";
    }

    @Override
    public String getLabel() {
        return Messages.get("pipeline.stage.cleanup");
    }

    @Override
    public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
        return doCleanup(context);
    }

    private CompletableFuture<StageResult> doCleanup(TaskPipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();

        String taskId = context.getExecutionContext().taskId();
        String nodeId = context.getExecutionContext().nodeId();
        Map<String, MqttClientTask> clientTaskMap = resolveClientMap(context);

        if (clientTaskMap == null || clientTaskMap.isEmpty()) {
            log.info("No clients to cleanup for taskId={}, clientTag={}", taskId, clientTag);
            closeSharedSubWorkerExecutor(context);
            future.complete(StageResult.success("No clients to cleanup"));
            return future;
        }
        IRateLimiter rateLimiter = context.getExecutionContext().disconnectRateLimiter();

        MqttClientTask[] clients = clientTaskMap.values().toArray(new MqttClientTask[0]);
        CloseStats closeStats = new CloseStats();
        Queue<CompletableFuture<Void>> closeFutures = new ConcurrentLinkedQueue<>();
        log.info("Starting cleanup of {} clients for taskId={}, clientTag={}",
            clients.length, taskId, clientTag);

        FiniteDispatchPlan dispatchPlan = context.getExecutionContext().disconnectDispatchPlan(clients.length);
        if (dispatchPlan != null) {
            runFiniteDispatch(context, future, clientTaskMap, clients, taskId, dispatchPlan, closeStats);
            return future;
        }

        QpsStrategy disconnectQpsStrategy = context.getExecutionContext().disconnectQpsStrategy();
        if (disconnectQpsStrategy != null && disconnectQpsStrategy.isDynamic()) {
            stageStartMs.set(System.currentTimeMillis());
            long timeOriginMs = context.getExecutionContext().dynamicQpsTimeOriginMs(stageStartMs.get(), getName());
            dynamicRateController = new DynamicRateController("disconnect", rateLimiter, disconnectQpsStrategy,
                timeOriginMs, 0);
            dynamicRateController.start();
        }

        rateLimiter.executeWithRateLimit(clients.length, index -> {
            MqttClientTask client = clients[index];
            CompletableFuture<Void> closeFuture = closeClient(client, taskId, nodeId, closeStats);
            closeFutures.add(closeFuture);
            return closeFuture;
        }).whenComplete((__, error) -> {
            stopDynamicQpsScheduler();
            if (error != null) {
                log.error("Failed to cleanup clients for taskId={}, clientTag={}",
                    taskId, clientTag, error);
                future.complete(StageResult.failure(error));
                return;
            }
            CompletableFuture.allOf(closeFutures.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, closeError) ->
                    completeCleanup(context, future, clientTaskMap, clients.length, taskId, closeStats));
        });

        return future;
    }

    private void runFiniteDispatch(TaskPipelineContext context, CompletableFuture<StageResult> future,
                                   Map<String, MqttClientTask> clientTaskMap, MqttClientTask[] clients,
                                   String taskId, FiniteDispatchPlan dispatchPlan, CloseStats closeStats) {
        log.info("Starting finite cleanup dispatch: clients={}, planned={}, duration={}ms, taskId={}, clientTag={}",
            clients.length, dispatchPlan.plannedTotalCount(), dispatchPlan.durationMs(), taskId, clientTag);
        finiteDispatchScheduler = new FiniteDispatchScheduler(
            getName(), "disconnects", clients.length, dispatchPlan,
            index -> {
                if (context.isCancelled()) {
                    return CompletableFuture.completedFuture(null);
                }
                MqttClientTask client = clients[index];
                return closeClient(client, taskId, context.getExecutionContext().nodeId(), closeStats);
            },
            () -> completeCleanup(context, future, clientTaskMap, clients.length, taskId, closeStats)
        );
        finiteDispatchScheduler.start();
    }

    private CompletableFuture<Void> closeClient(MqttClientTask client, String taskId, String nodeId,
                                                CloseStats closeStats) {
        CompletableFuture<Void> closeFuture;
        try {
            closeFuture = client.close();
        } catch (Exception e) {
            closeStats.failureCount.incrementAndGet();
            log.warn("Failed to close client {}, taskId={}: {}", client.getCId(), taskId, e.getMessage());
            MetricsHelper.counter(BifroTaskMetric.CLIENT_CLOSE_EXCEPTION_COUNT,
                Tags.of("taskId", taskId, "nodeId", nodeId, "clientType", clientTag));
            return CompletableFuture.completedFuture(null);
        }
        if (closeFuture == null) {
            closeStats.successCount.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
        return closeFuture.orTimeout(clientCloseTimeoutMs, TimeUnit.MILLISECONDS)
            .handle((ignored, error) -> {
                if (error == null) {
                    closeStats.successCount.incrementAndGet();
                    return null;
                }
                Throwable cause = unwrap(error);
                if (cause instanceof TimeoutException) {
                    closeStats.timeoutCount.incrementAndGet();
                    log.warn("Timed out closing client {}, taskId={}, timeoutMs={}",
                        client.getCId(), taskId, clientCloseTimeoutMs);
                } else {
                    closeStats.failureCount.incrementAndGet();
                    log.warn("Failed to close client {}, taskId={}: {}",
                        client.getCId(), taskId, cause.getMessage());
                }
                MetricsHelper.counter(BifroTaskMetric.CLIENT_CLOSE_EXCEPTION_COUNT,
                    Tags.of("taskId", taskId, "nodeId", nodeId, "clientType", clientTag));
                return null;
            });
    }

    private void completeCleanup(TaskPipelineContext context, CompletableFuture<StageResult> future,
                                 Map<String, MqttClientTask> clientTaskMap, int clientCount, String taskId,
                                 CloseStats closeStats) {
        log.info("Cleanup completed: {} clients for taskId={}, clientTag={}, closeSuccess={}, closeFailure={}, "
                + "closeTimeout={}",
            clientCount, taskId, clientTag, closeStats.successCount.get(), closeStats.failureCount.get(),
            closeStats.timeoutCount.get());
        clientTaskMap.clear();
        closeSharedSubWorkerExecutor(context);
        future.complete(StageResult.success(String.format(
            "Cleanup completed for %d clients, closeSuccess=%d, closeFailure=%d, closeTimeout=%d",
            clientCount, closeStats.successCount.get(), closeStats.failureCount.get(), closeStats.timeoutCount.get())));
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private void stopDynamicQpsScheduler() {
        if (dynamicRateController != null) {
            dynamicRateController.stop();
        }
    }

    private void stopFiniteDispatchScheduler() {
        if (finiteDispatchScheduler != null) {
            finiteDispatchScheduler.stop();
        }
    }

    @Override
    public void onBefore(TaskPipelineContext context) {
        Map<String, MqttClientTask> clientTaskMap = resolveClientMap(context);
        int clientCount = (clientTaskMap != null) ? clientTaskMap.size() : 0;
        log.info("Starting cleanup of {} connection clients, clientTag={}",
            clientCount, clientTag);
    }

    @Override
    public void onAfter(TaskPipelineContext context, StageResult result) {
        log.info("Cleanup completed: {}", result.getMessage());
    }

    @Override
    public void onError(TaskPipelineContext context, Throwable error) {
        String taskId = context.getExecutionContext().taskId();
        log.error("Error during cleanup for taskId={}, clientTag={}: {}",
            taskId, clientTag, error.getMessage());
    }

    @Override
    public void onCancelled(TaskPipelineContext context) {
        log.info("Cleanup triggered on cancellation for clientTag={}", clientTag);
    }

    @Override
    public CompletableFuture<Void> cancel(TaskPipelineContext context) {
        return execute(context).thenApply(result -> null);
    }

    private Map<String, MqttClientTask> resolveClientMap(TaskPipelineContext context) {
        if (Constants.CONN_CLIENT_TAG.equals(clientTag)) {
            return context.getExecutionContext().connClients();
        } else if (Constants.PUB_CLIENT_TAG.equals(clientTag)) {
            return context.getExecutionContext().pubClients();
        } else if (Constants.SUB_CLIENT_TAG.equals(clientTag)) {
            return context.getExecutionContext().subClients();
        }
        return null;
    }

    private void closeSharedSubWorkerExecutor(TaskPipelineContext context) {
        if (Constants.SUB_CLIENT_TAG.equals(clientTag)) {
            context.closeSubWorkerExecutor();
        }
    }

    private static final class CloseStats {
        private final AtomicInteger successCount = new AtomicInteger();
        private final AtomicInteger failureCount = new AtomicInteger();
        private final AtomicInteger timeoutCount = new AtomicInteger();
    }
}
