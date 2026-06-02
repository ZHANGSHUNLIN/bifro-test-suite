/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VertxDelayedTaskScheduler implements DelayedTaskScheduler {
    private final Vertx vertx;
    private final ScheduledTaskExecutorRegistry executorRegistry;
    private final SchedulerProperties properties;
    private final SchedulerMetrics metrics;
    private final Map<String, ScheduledTaskRecord> pendingTasks = new ConcurrentHashMap<>();
    private final AtomicInteger runningTasks = new AtomicInteger();
    private volatile boolean closed;

    public VertxDelayedTaskScheduler(Vertx vertx,
                                     ScheduledTaskExecutorRegistry executorRegistry,
                                     SchedulerProperties properties,
                                     MeterRegistry meterRegistry) {
        this.vertx = vertx;
        this.executorRegistry = executorRegistry;
        this.properties = properties == null ? new SchedulerProperties() : properties;
        this.metrics = new SchedulerMetrics(meterRegistry);
        this.metrics.bindGauge("bifro_scheduler_pending_tasks", pendingTasks,
            target -> ((Map<?, ?>) target).size());
        this.metrics.bindGauge("bifro_scheduler_running_tasks", runningTasks,
            target -> ((AtomicInteger) target).get());
    }

    @Override
    public ScheduledTaskResult schedule(ScheduledTaskRequest request) {
        long now = System.currentTimeMillis();
        ScheduledTaskResult rejected = validate(request, now);
        if (rejected != null) {
            return rejected;
        }

        long delayMs = Math.max(0L, request.getDelayMs());
        long dueAtMs = now + delayMs;
        ScheduledTaskRecord record = new ScheduledTaskRecord(request, now, dueAtMs);
        ScheduledTaskRecord previous = pendingTasks.put(request.getTaskKey(), record);
        if (previous == null && pendingTasks.size() > properties.getMaxPendingTasks()) {
            pendingTasks.remove(request.getTaskKey(), record);
            metrics.rejected(request.getKind(), "capacity");
            return result(false, request, ScheduledTaskState.REJECTED, "Scheduler pending task limit exceeded",
                now, dueAtMs);
        }
        if (previous != null) {
            vertx.cancelTimer(previous.timerId);
        }
        long timerId = vertx.setTimer(delayMs, ignored -> execute(record));
        record.timerId = timerId;
        metrics.scheduled(request.getKind(), scopeName(request.getScope()), previous == null ? "accepted" : "replaced");
        metrics.delay(request.getKind(), delayMs);
        log.debug("Scheduled delayed task: key={}, kind={}, delayMs={}, dueAtMs={}",
            request.getTaskKey(), request.getKind(), delayMs, dueAtMs);
        return result(true, request, ScheduledTaskState.PENDING, null, now, dueAtMs);
    }

    @Override
    public boolean cancel(String taskKey) {
        if (taskKey == null || taskKey.isBlank()) {
            return false;
        }
        ScheduledTaskRecord record = pendingTasks.remove(taskKey);
        if (record == null) {
            metrics.cancelled(null, "not_found");
            return false;
        }
        boolean cancelled = vertx.cancelTimer(record.timerId);
        record.state = ScheduledTaskState.CANCELLED;
        metrics.cancelled(record.request.getKind(), cancelled ? "cancelled" : "already_triggered");
        return cancelled;
    }

    @Override
    public List<ScheduledTaskView> listPending() {
        return pendingTasks.values().stream()
            .sorted(Comparator.comparingLong(record -> record.dueAtMs))
            .map(ScheduledTaskRecord::view)
            .toList();
    }

    @Override
    public void close() {
        closed = true;
        pendingTasks.values().forEach(record -> vertx.cancelTimer(record.timerId));
        pendingTasks.clear();
    }

    private ScheduledTaskResult validate(ScheduledTaskRequest request, long now) {
        if (closed) {
            return reject(request, "closed", "Scheduler is closed", now);
        }
        if (!properties.isEnabled()) {
            return reject(request, "disabled", "Scheduler is disabled", now);
        }
        if (request == null) {
            return reject(null, "invalid_payload", "Scheduled task request is empty", now);
        }
        if (request.getTaskKey() == null || request.getTaskKey().isBlank()) {
            return reject(request, "invalid_payload", "Scheduled task key is required", now);
        }
        if (request.getKind() == null) {
            return reject(request, "invalid_payload", "Scheduled task kind is required", now);
        }
        if (request.getScope() == null) {
            request.setScope(ScheduledTaskScope.LOCAL);
        }
        if (request.getDelayMs() < 0 || request.getDelayMs() > properties.maxDelayMs()) {
            return reject(request, "invalid_delay", "Scheduled task delay is out of range", now);
        }
        if (executorRegistry.find(request.getKind()).isEmpty()) {
            return reject(request, "unsupported_kind", "Scheduled task kind is not registered", now);
        }
        return null;
    }

    private ScheduledTaskResult reject(ScheduledTaskRequest request, String reason, String message, long now) {
        ScheduledTaskKind kind = request == null ? null : request.getKind();
        metrics.rejected(kind, reason);
        metrics.scheduled(kind, request == null ? "UNKNOWN" : scopeName(request.getScope()), "rejected");
        return result(false, request, ScheduledTaskState.REJECTED, message, now, now);
    }

    private void execute(ScheduledTaskRecord record) {
        if (!pendingTasks.remove(record.request.getTaskKey(), record)) {
            return;
        }
        record.state = ScheduledTaskState.RUNNING;
        record.attempt++;
        runningTasks.incrementAndGet();
        long startNanos = System.nanoTime();
        ScheduledTaskExecutor executor = executorRegistry.find(record.request.getKind()).orElse(null);
        if (executor == null) {
            finish(record, startNanos, ScheduledTaskState.FAILED, "missing_executor", null);
            return;
        }
        ScheduledTaskContext context = ScheduledTaskContext.builder()
            .taskKey(record.request.getTaskKey())
            .kind(record.request.getKind())
            .targetNodeId(record.request.getTargetNodeId())
            .attempt(record.attempt)
            .payload(record.request.getPayload())
            .build();
        CompletableFuture<ScheduledTaskExecutionResult> future;
        try {
            future = executor.execute(context).toCompletableFuture();
        } catch (Exception e) {
            finish(record, startNanos, ScheduledTaskState.FAILED, "exception", e);
            return;
        }
        future.whenComplete((result, error) -> {
            if (error != null) {
                finish(record, startNanos, ScheduledTaskState.FAILED, "exception", error);
                return;
            }
            if (result != null && result.isSuccess()) {
                finish(record, startNanos, ScheduledTaskState.SUCCEEDED, "success", null);
            } else {
                finish(record, startNanos, ScheduledTaskState.FAILED,
                    result == null ? "failed" : result.getMessage(), null);
            }
        });
    }

    private void finish(ScheduledTaskRecord record,
                        long startNanos,
                        ScheduledTaskState state,
                        String outcome,
                        Throwable error) {
        record.state = state;
        runningTasks.decrementAndGet();
        metrics.executed(record.request.getKind(), outcome, System.nanoTime() - startNanos);
        if (state == ScheduledTaskState.SUCCEEDED) {
            log.debug("Delayed task completed: key={}, kind={}", record.request.getTaskKey(), record.request.getKind());
        } else {
            log.warn("Delayed task failed: key={}, kind={}, outcome={}",
                record.request.getTaskKey(), record.request.getKind(), outcome, error);
        }
    }

    private ScheduledTaskResult result(boolean accepted,
                                       ScheduledTaskRequest request,
                                       ScheduledTaskState state,
                                       String reason,
                                       long scheduledAtMs,
                                       long dueAtMs) {
        return ScheduledTaskResult.builder()
            .accepted(accepted)
            .taskKey(request == null ? null : request.getTaskKey())
            .kind(request == null ? null : request.getKind())
            .state(state)
            .reason(reason)
            .scheduledAtMs(scheduledAtMs)
            .dueAtMs(dueAtMs)
            .build();
    }

    private String scopeName(ScheduledTaskScope scope) {
        return scope == null ? ScheduledTaskScope.LOCAL.name() : scope.name();
    }

    private static class ScheduledTaskRecord {
        private final ScheduledTaskRequest request;
        private final long scheduledAtMs;
        private final long dueAtMs;
        private volatile long timerId;
        private volatile int attempt;
        private volatile ScheduledTaskState state = ScheduledTaskState.PENDING;

        private ScheduledTaskRecord(ScheduledTaskRequest request, long scheduledAtMs, long dueAtMs) {
            this.request = request;
            this.scheduledAtMs = scheduledAtMs;
            this.dueAtMs = dueAtMs;
        }

        private ScheduledTaskView view() {
            return ScheduledTaskView.builder()
                .taskKey(request.getTaskKey())
                .scope(request.getScope())
                .kind(request.getKind())
                .targetNodeId(request.getTargetNodeId())
                .state(state)
                .scheduledAtMs(scheduledAtMs)
                .dueAtMs(dueAtMs)
                .delayMs(request.getDelayMs())
                .attempt(attempt)
                .payload(request.getPayload())
                .build();
        }
    }
}
