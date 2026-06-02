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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.MqttClientTask;
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
import org.apache.bifromq.testsuite.worker.ratelimit.FiniteDispatchPlan;
import org.apache.bifromq.testsuite.worker.ratelimit.FiniteDispatchScheduler;
import org.apache.bifromq.testsuite.worker.ratelimit.IRateLimiter;

@Slf4j
public abstract class BaseConnClientsStage implements PipelineStage<TaskPipelineContext> {

    private static final int MIN_CONNECT_DYNAMIC_QPS = 1;
    private static final int FULL_FAILURE_WARN_LIMIT = 10;
    private static final int PROGRESS_UPDATE_INTERVAL = 100;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger startedCount = new AtomicInteger(0);
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicLong stageStartMs = new AtomicLong(0);

    private final ConcurrentLinkedQueue<MqttClientTask> connectedClients = new ConcurrentLinkedQueue<>();
    private final Map<String, AtomicInteger> failureReasonCounters = new ConcurrentHashMap<>();

    private IRateLimiter rateLimiter;

    private DynamicRateController dynamicRateController;
    private FiniteDispatchScheduler finiteDispatchScheduler;

    private static String classifyConnectionFailure(Throwable throwable) {
        Throwable root = throwable;
        while (root != null && root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root != null && root.getMessage() != null ? root.getMessage().toLowerCase() : "";
        if (message.contains("cannot assign requested address")) {
            return "local_addr_or_ephemeral_port_exhausted";
        }
        if (message.contains("address already in use")) {
            return "local_port_address_in_use";
        }
        if (message.contains("connection refused")) {
            return "connection_refused";
        }
        if (message.contains("connect timed out") || message.contains("timeout")) {
            return "connect_timeout";
        }
        if (message.contains("no route to host")) {
            return "no_route_to_host";
        }
        return root != null ? root.getClass().getSimpleName() : "unknown";
    }

    @Override
    public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
        StageExecutionScope scope = context.stageScopeOrCreate(getName());
        Map<String, MqttClientTask> connClients = taskClientMap(context);
        rateLimiter = rateLimiterFor(context);
        scope.register("connect-rate-limiter", () -> {
            if (rateLimiter != null) {
                rateLimiter.dispose();
            }
        });
        QpsStrategy qpsStrategy = qpsStrategyFor(context);

        stageStartMs.set(System.currentTimeMillis());

        int totalClients = connClients.size();
        log.info("Starting connection: {} clients, taskId={}", totalClients,
            context.getExecutionContext().taskId());
        if (connClients.isEmpty()) {
            log.info("No clients to connect for this stage (clientTag={}), skipping. "
                    + "This is expected for pub-only or sub-only nodes in distributed mode.",
                getName());
            stopDynamicQpsScheduler();
            scope.complete(StageResult.success());
            return scope.result();
        }

        MqttClientTask[] clients = connClients.values().toArray(new MqttClientTask[0]);
        FiniteDispatchPlan dispatchPlan = connectionDispatchPlan(context, totalClients);
        if (dispatchPlan != null) {
            runFiniteDispatch(context, scope, clients, dispatchPlan);
            return scope.result();
        }

        if (qpsStrategy != null) {
            long timeOriginMs = context.getExecutionContext().dynamicQpsTimeOriginMs(
                stageStartMs.get(), getName());
            dynamicRateController = new DynamicRateController("connect", rateLimiter, qpsStrategy, timeOriginMs,
                MIN_CONNECT_DYNAMIC_QPS);
            if (dynamicRateController.start()) {
                scope.register("connect-dynamic-qps", this::stopDynamicQpsScheduler);
            }
        }

        ConcurrentLinkedQueue<CompletableFuture<Void>> connectionFutures = new ConcurrentLinkedQueue<>();
        CompletableFuture<Void> rateLimitFuture = rateLimiter.executeWithRateLimit(clients.length, index -> {
            if (context.isCancelled()) {
                log.debug("Task cancelled, skip connecting client at index {}", index);
                return CompletableFuture.completedFuture(null);
            }
            String taskId = context.getExecutionContext().taskId();
            String nodeId = context.getExecutionContext().nodeId();
            MqttClientTask clientTask = clients[index];
            log.debug("Starting client connection: {}", clientTask.getCId());
            startedCount.incrementAndGet();
            MetricsHelper.counter(BifroTaskMetric.CONNECT_ATTEMPT_COUNT, 1.0,
                "taskId", taskId, "nodeId", nodeId);
            CompletableFuture<Void> connectionFuture = clientTask.connect()
                .thenAccept(result -> {
                    log.trace("[BaseConnClientsStage] Client connected successfully, clientId={}",
                        clientTask.getCId());
                    successCount.incrementAndGet();

                    connectedClients.add(clientTask);
                })
                .exceptionally(error -> {
                    handleConnectionFailure(context, scope, clientTask, error);
                    return null;
                })
                .whenComplete((ignored, error) -> {
                    completeOneConnection(context);
                });
            CompletableFuture<Void> trackedFuture = scope.track("connect-" + clientTask.getCId(), connectionFuture);
            connectionFutures.add(trackedFuture);
            return trackedFuture;
        }).whenComplete((__, error) -> {
            if (error != null) {
                stopDynamicQpsScheduler();
                log.error("Exception in connection stage", error);
                if (!scope.isCancelled()) {
                    scope.complete(StageResult.failure(
                        Messages.get("error.worker.connException", error.getMessage(), successCount.get(),
                            failureCount.get())));
                }
                return;
            }

            CompletableFuture.allOf(connectionFutures.toArray(new CompletableFuture[0]))
                .whenComplete((ignored, connectError) -> {
                    if (!scope.isCancelled()) {
                        completeStage(totalClients, context, scope);
                    }
                });
        });
        scope.track("connect-rate-limit-dispatch", rateLimitFuture);

        return scope.result();
    }

    private void runFiniteDispatch(TaskPipelineContext context, StageExecutionScope scope,
                                   MqttClientTask[] clients, FiniteDispatchPlan dispatchPlan) {
        int totalClients = clients.length;
        log.info("Starting finite connection dispatch: clients={}, planned={}, duration={}ms, taskId={}",
            totalClients, dispatchPlan.plannedTotalCount(), dispatchPlan.durationMs(),
            context.getExecutionContext().taskId());
        finiteDispatchScheduler = new FiniteDispatchScheduler(
            getName(), "connections", totalClients, dispatchPlan,
            index -> {
                if (scope.isCancelled() || context.isCancelled()) {
                    return CompletableFuture.completedFuture(null);
                }
                CompletableFuture<Void> connectionFuture = startConnection(context, clients[index]);
                return scope.track("connect-" + clients[index].getCId(), connectionFuture);
            },
            () -> {
                if (!scope.isCancelled()) {
                    completeStage(totalClients, context, scope);
                }
            }
        );
        scope.register("connect-finite-dispatch", this::stopFiniteDispatchScheduler);
        finiteDispatchScheduler.start();
    }

    private CompletableFuture<Void> startConnection(TaskPipelineContext context, MqttClientTask clientTask) {
        String taskId = context.getExecutionContext().taskId();
        String nodeId = context.getExecutionContext().nodeId();
        StageExecutionScope scope = context.stageScopeOrCreate(getName());
        log.debug("Starting client connection: {}", clientTask.getCId());
        startedCount.incrementAndGet();
        MetricsHelper.counter(BifroTaskMetric.CONNECT_ATTEMPT_COUNT, 1.0,
            "taskId", taskId, "nodeId", nodeId);
        try {
            return clientTask.connect()
                .thenAccept(result -> {
                    log.trace("[BaseConnClientsStage] Client connected successfully, clientId={}",
                        clientTask.getCId());
                    successCount.incrementAndGet();
                    connectedClients.add(clientTask);
                })
                .exceptionally(error -> {
                    handleConnectionFailure(context, scope, clientTask, error);
                    return null;
                })
                .whenComplete((ignored, error) -> completeOneConnection(context));
        } catch (Exception error) {
            handleConnectionFailure(context, scope, clientTask, error);
            completeOneConnection(context);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void handleConnectionFailure(TaskPipelineContext context, StageExecutionScope scope,
                                         MqttClientTask clientTask, Throwable error) {
        int failureIndex = failureCount.incrementAndGet();
        String reasonType = classifyConnectionFailure(error);
        failureReasonCounters.computeIfAbsent(reasonType, k -> new AtomicInteger(0)).incrementAndGet();
        scope.recordFailureReason(reasonType);
        if (failureIndex <= FULL_FAILURE_WARN_LIMIT) {
            log.warn("Client connection failed: taskId={}, nodeId={}, stage={}, clientId={}, reasonType={}, "
                    + "reason={}",
                context.getExecutionContext().taskId(), context.getExecutionContext().nodeId(), getName(),
                clientTask.getCId(), reasonType, error.getMessage(), error);
            return;
        }
        log.debug("Client connection failed: taskId={}, nodeId={}, stage={}, clientId={}, reasonType={}, reason={}",
            context.getExecutionContext().taskId(), context.getExecutionContext().nodeId(), getName(),
            clientTask.getCId(), reasonType, error.getMessage());
    }

    private void completeOneConnection(TaskPipelineContext context) {
        int completed = completedCount.incrementAndGet();
        if (completed % PROGRESS_UPDATE_INTERVAL == 0) {
            context.refreshStageDiagnostics(getName());
        }
    }

    private FiniteDispatchPlan connectionDispatchPlan(TaskPipelineContext context, int totalClients) {
        return context.getExecutionContext().connectDispatchPlan(totalClients);
    }

    private void completeStage(int totalClients, TaskPipelineContext context, StageExecutionScope scope) {
        int success = successCount.get();
        int failed = failureCount.get();
        context.refreshStageDiagnostics(getName());
        log.info("Connection stage completed: total {}, success {}, failed {}", totalClients, success, failed);
        if (failed > 0) {
            log.warn("Connection failure summary, taskId={}, nodeId={}, stage={}, total={}, success={}, failed={}, "
                    + "reasons={}",
                context.getExecutionContext().taskId(), context.getExecutionContext().nodeId(), getName(),
                totalClients, success, failed, summarizeFailureReasons());
        }

        if (failed > 0 && success == 0) {
            scope.complete(StageResult.failure(
                Messages.get("error.worker.connFailed", failed)));
        } else if (failed > 0) {
            scope.complete(StageResult.success(
                Messages.get("error.worker.connDone", success, failed)));
        } else {
            scope.complete(StageResult.success(
                Messages.get("error.worker.connSuccess", success)));
        }
        stopDynamicQpsScheduler();
    }

    @Override
    public void onBefore(TaskPipelineContext context) {
        Map<String, MqttClientTask> connClients = taskClientMap(context);
        log.info("Preparing to connect {} clients, taskId={}", connClients.size(),
            context.getExecutionContext().taskId());
    }

    @Override
    public void onAfter(TaskPipelineContext context, StageResult result) {
        String taskId = context.getExecutionContext().taskId();
        String nodeId = context.getExecutionContext().nodeId();
        log.info("Connection stage completed: taskId={}, result={}", taskId, result.getMessage());

        MetricsHelper.freezeTimerSnapshot(BifroTaskMetric.CONNECT_LATENCY,
            "taskId", taskId, "nodeId", nodeId, "result", "success");
        MetricsHelper.freezeTimerSnapshot(BifroTaskMetric.CONNECT_LATENCY,
            "taskId", taskId, "nodeId", nodeId, "result", "failure");
    }

    @Override
    public void onError(TaskPipelineContext context, Throwable error) {
        log.error("Error in client connection stage", error);
    }

    @Override
    public CompletableFuture<Void> cancel(TaskPipelineContext context) {
        log.info("Cancelling connection stage, stopping new client connections");
        StageExecutionScope scope = context.stageScopeOrCreate(getName());
        scope.cancel();
        int pending = Math.max(0, startedCount.get() - completedCount.get());
        StageCancelSnapshot snapshot = scope.snapshot();
        log.info("Connection stage cancelled, connected={}, failed={}, pending={}, scopeStarted={}, "
                + "scopeCompleted={}, scopePending={}",
            successCount.get(), failureCount.get(), pending, snapshot.started(), snapshot.completed(),
            snapshot.pending());
        return CompletableFuture.completedFuture(null);
    }

    public List<MqttClientTask> getConnectedClients() {
        return new ArrayList<>(connectedClients);
    }

    public int getConnectedCount() {
        return successCount.get();
    }

    abstract Map<String, MqttClientTask> taskClientMap(TaskPipelineContext context);

    protected IRateLimiter rateLimiterFor(TaskPipelineContext context) {
        return context.getExecutionContext().connectRateLimiter();
    }

    protected QpsStrategy qpsStrategyFor(TaskPipelineContext context) {
        QpsStrategy strategy = context.getExecutionContext().connectQpsStrategy();

        if (strategy != null && strategy.isDynamic()) {
            return strategy;
        }
        return null;
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

    private String summarizeFailureReasons() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, AtomicInteger> entry : failureReasonCounters.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue().get());
            first = false;
        }
        return sb.toString();
    }

}
