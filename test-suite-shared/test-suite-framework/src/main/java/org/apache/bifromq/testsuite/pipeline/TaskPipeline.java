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

package org.apache.bifromq.testsuite.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TaskPipeline<C extends PipelineContext<?, ?>> {

    private final List<PipelineStage<C>> stages;
    @Getter
    private final PipelineStage<C> errorStage;
    @Getter
    private final PipelineStage<C> cancelStage;
    private final PipelineStage<C> cleanupStage;
    private final long cancelTimeoutMs;

    @Getter
    private int currentStageIndex;

    private TaskPipeline(Builder<C> builder) {
        this.stages = new ArrayList<>(builder.stages);
        this.errorStage = builder.errorStage;
        this.cancelStage = builder.cancelStage;
        this.cleanupStage = builder.cleanupStage;
        this.cancelTimeoutMs = builder.cancelTimeoutMs;
        this.currentStageIndex = 0;
    }

    public static <C extends PipelineContext<?, ?>> Builder<C> builder() {
        return new Builder<>();
    }

    public void initProgress(C context) {
        for (PipelineStage<C> stage : stages) {
            context.getPipelineProgress().add(PipelineStageSnapshot.builder()
                .key(stage.getName())
                .label(stage.getLabel())
                .visible(stage.isVisible())
                .status("PENDING")
                .build());
        }
    }

    public CompletableFuture<Void> execute(C context) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        executeStage(0, context)
            .thenAccept(v -> {
                log.info("Pipeline execution completed, stageCount={}", stages.size());
                result.complete(null);
            })
            .exceptionally(ex -> {
                log.error("Pipeline execution failed", ex);
                handleError(context, ex);
                result.completeExceptionally(ex);
                return null;
            });
        return result;
    }

    private CompletableFuture<Void> executeStage(int index, C context) {
        if (index >= stages.size()) {

            context.getCompletionFuture().complete(null);
            return CompletableFuture.completedFuture(null);
        }

        if (context.isCancelled()) {
            log.info("Pipeline is cancelled, skipping remaining stages from index {}", index);
            cancelStage.onCancelled(context);

            context.getCompletionFuture().completeExceptionally(new RuntimeException("Pipeline cancelled"));
            return CompletableFuture.completedFuture(null);
        }

        PipelineStage<C> stage = stages.get(index);
        currentStageIndex = index;

        String stageName = stage.getName();

        log.debug("Executing stage {}/{}: {}, currState={}", index + 1, stages.size(), stageName,
            context.getStateMachine().getCurrentState());

        if (!stage.canExecute(context)) {
            log.warn("Stage {} cannot execute, skipping", stageName);
            markStage(context, index, "SKIPPED", null, null);
            return executeStage(index + 1, context);
        }

        context.stageScopeOrCreate(stageName);
        markStage(context, index, "RUNNING", System.currentTimeMillis(), null);
        try (PipelineContext.ContextScope ignored = context.enterDiagnosticContext(stageName)) {
            stage.onBefore(context);
        }

        CompletableFuture<StageResult> stageFuture;
        try (PipelineContext.ContextScope ignored = context.enterDiagnosticContext(stageName)) {
            stageFuture = stage.execute(context);
        }

        return stageFuture
            .thenCompose(result -> {

                if (context.isCancelled()) {
                    log.info("Pipeline cancelled during stage {}", stageName);
                    try (PipelineContext.ContextScope ignored = context.enterDiagnosticContext(stageName)) {
                        stage.onCancelled(context);
                    }
                    markStage(context, index, "CANCELLED", null, System.currentTimeMillis());
                    return CompletableFuture.completedFuture(null);
                }

                try (PipelineContext.ContextScope ignored = context.enterDiagnosticContext(stageName)) {
                    stage.onAfter(context, result);
                }

                if (!result.isSuccess()) {
                    log.error("Stage {} failed: {}", stageName, result.getMessage());
                    markStage(context, index, "FAILED", null, System.currentTimeMillis(),
                        result.getMessage());

                    context.getStageData().put("lastError", result);

                    RuntimeException ex = new RuntimeException(
                        String.format("Stage %s failed: %s", stageName, result.getMessage()));
                    return CompletableFuture.failedFuture(ex);
                }

                log.debug("Stage {} completed successfully", stageName);
                markStage(context, index, "DONE", null, System.currentTimeMillis());

                Object triggerEvent = stage.getTriggerEvent();
                if (triggerEvent != null) {
                    return context.triggerTransition(triggerEvent)
                        .thenCompose(success -> {
                            if (!success) {
                                log.warn("State transition failed for event: {}", triggerEvent);
                            } else {
                                log.debug("State transition triggered by event: {}", triggerEvent);
                            }
                            return delayAfterStageIfNeeded(stage, context)
                                .thenCompose(v -> executeStage(index + 1, context));
                        });
                }

                return delayAfterStageIfNeeded(stage, context)
                    .thenCompose(v -> executeStage(index + 1, context));
            })
            .exceptionally(ex -> {
                log.error("Stage {} threw exception", stageName, ex);
                try (PipelineContext.ContextScope ignored = context.enterDiagnosticContext(stageName)) {
                    stage.onError(context, ex);
                }
                markStage(context, index, "FAILED", null, System.currentTimeMillis(),
                    ex.getMessage());

                throw new RuntimeException(ex);
            });
    }

    private void markStage(C context, int index, String status,
                           Long startedAt, Long endedAt) {
        markStage(context, index, status, startedAt, endedAt, null);
    }

    private void markStage(C context, int index, String status,
                           Long startedAt, Long endedAt, String failureReason) {
        List<PipelineStageSnapshot> progress = context.getPipelineProgress();
        if (index < 0 || index >= progress.size()) {
            return;
        }
        PipelineStageSnapshot existing = progress.get(index);
        PipelineStageSnapshot updated = context.enrichStageSnapshot(PipelineStageSnapshot.builder()
            .key(existing.getKey())
            .label(existing.getLabel())
            .visible(existing.isVisible())
            .status(status)
            .startedAt(startedAt != null ? startedAt : existing.getStartedAt())
            .endedAt(endedAt != null ? endedAt : existing.getEndedAt())
            .failureReason(failureReason)
            .build(), context.stageScope(existing.getKey()));
        context.updateStageSnapshot(index, updated);
        context.onStageEvent(updated);
    }

    private void handleError(C context, Throwable ex) {
        context.getStageData().put("error", ex);

        if (errorStage != null) {
            errorStage.execute(context)
                .whenComplete((result, e) -> {
                    log.info("Error stage completed, stageName={}", errorStage.getName());
                    context.getCompletionFuture().completeExceptionally(ex);
                });
        } else {
            context.getCompletionFuture().completeExceptionally(ex);
        }
    }

    public CompletableFuture<Void> cancel(C context) {
        log.info("Cancelling pipeline at stage {}/{}", currentStageIndex, stages.size());
        log.info("Cancelling stage : {}", context.hashCode());
        context.cancel();

        List<CompletableFuture<Void>> cancellationFutures = new ArrayList<>();

        for (int i = currentStageIndex; i < stages.size(); i++) {
            PipelineStage<C> stage = stages.get(i);
            stage.onCancelled(context);
        }

        for (int i = currentStageIndex; i < stages.size(); i++) {
            PipelineStage<C> stage = stages.get(i);
            cancellationFutures.add(boundedCancel(stage, context, cancelTimeoutMs));
        }

        if (cleanupStage != null) {
            cleanupStage.onCancelled(context);
            CompletableFuture<Void> cleanupFuture = cleanupStage.execute(context)
                .<Void>thenApply(result -> null)
                .completeOnTimeout(null, cancelTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("Cleanup stage failed", ex);
                    return null;
                });
            cancellationFutures.add(cleanupFuture);
        }

        context.getCompletionFuture().completeExceptionally(
            new RuntimeException("Pipeline cancelled"));

        return CompletableFuture.allOf(cancellationFutures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> boundedCancel(PipelineStage<C> stage, C context, long timeoutMs) {
        StageExecutionScope scope = context.stageScope(stage.getName());
        if (scope != null) {
            scope.cancel();
            StageCancelSnapshot snapshot = scope.snapshot();
            log.info("Stage scope cancelled, stage={}, started={}, completed={}, failed={}, pending={}",
                snapshot.stageName(), snapshot.started(), snapshot.completed(), snapshot.failed(),
                snapshot.pending());
            context.refreshStageDiagnostics(stage.getName());
        }
        CompletableFuture<Void> future;
        try {
            future = stage.cancel(context);
        } catch (Exception e) {
            log.warn("Stage cancel threw, stage={}", stage.getName(), e);
            return CompletableFuture.completedFuture(null);
        }
        if (future == null) {
            return CompletableFuture.completedFuture(null);
        }
        return future
            .completeOnTimeout(null, timeoutMs, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                log.warn("Stage cancel failed, stage={}", stage.getName(), ex);
                return null;
            });
    }

    public int getStageCount() {
        return stages.size();
    }

    public List<PipelineStage<C>> getStages() {
        return java.util.Collections.unmodifiableList(stages);
    }

    private CompletableFuture<Void> delayAfterStageIfNeeded(PipelineStage<C> stage, C context) {

        if (stage.getName().startsWith("StateTransition-")) {
            return CompletableFuture.completedFuture(null);
        }

        int delayInSeconds = context.getDelayAfterStageInSec();

        if (delayInSeconds <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        log.debug("Waiting {} seconds after stage '{}' for resource stabilization",
            delayInSeconds, stage.getName());

        CompletableFuture<Void> delayFuture = new CompletableFuture<>();
        context.getVertx().setTimer(delayInSeconds * 1000L,
            timerId -> context.wrapDiagnosticContext(stage.getName(), () -> delayFuture.complete(null)).run());

        return delayFuture;
    }

    public static class Builder<C extends PipelineContext<?, ?>> {
        private final List<PipelineStage<C>> stages = new ArrayList<>();
        private PipelineStage<C> errorStage;
        private PipelineStage<C> cancelStage;
        private PipelineStage<C> cleanupStage;
        private long cancelTimeoutMs = 10_000L;

        public Builder<C> addStage(PipelineStage<C> stage) {
            if (stage != null) {
                this.stages.add(stage);
            }
            return this;
        }

        public Builder<C> onError(PipelineStage<C> stage) {
            this.errorStage = stage;
            return this;
        }

        public Builder<C> onCancel(PipelineStage<C> stage) {
            this.cancelStage = stage;
            return this;
        }

        public Builder<C> onCleanup(PipelineStage<C> stage) {
            this.cleanupStage = stage;
            return this;
        }

        public Builder<C> cancelTimeoutMs(long timeoutMs) {
            this.cancelTimeoutMs = timeoutMs;
            return this;
        }

        public TaskPipeline<C> build() {
            return new TaskPipeline<>(this);
        }
    }
}
