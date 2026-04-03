
package com.baidu.iot.test.suite.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.baidu.iot.test.suite.TaskEvent;
import com.baidu.iot.test.suite.TaskStage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipeline for orchestrating task stages.
 *
 * @param <C> pipeline context type
 */
@Slf4j
public class TaskPipeline<C extends PipelineContext> {

    private final List<PipelineStage<C>> stages;
    private final PipelineStage<C> errorStage;
    private final PipelineStage<C> cancelStage;
    private final PipelineStage<C> cleanupStage;
    /**
     * -- GETTER --
     * Get current stage index.
     *
     */
    @Getter
    private int currentStageIndex;

    private TaskPipeline(Builder<C> builder) {
        this.stages = new ArrayList<>(builder.stages);
        this.errorStage = builder.errorStage;
        this.cancelStage = builder.cancelStage;
        this.cleanupStage = builder.cleanupStage;
        this.currentStageIndex = 0;
    }

    /**
     * Execute all stages in the pipeline.
     *
     * @param context pipeline context
     * @return CompletableFuture that completes when all stages are done
     */
    public CompletableFuture<Void> execute(C context) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        executeStage(0, context)
                .thenAccept(v -> {
                    log.info("Pipeline execution completed");
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

    /**
     * Execute a specific stage and continue to next.
     *
     * @param index   stage index
     * @param context pipeline context
     * @return CompletableFuture that completes when this stage and all following stages are done
     */
    private CompletableFuture<Void> executeStage(int index, C context) {
        if (index >= stages.size()) {
            // All stages completed
            context.getCompletionFuture().complete(null);
            return CompletableFuture.completedFuture(null);
        }

        // Check if pipeline is cancelled
        if (context.isCancelled()) {
            log.info("Pipeline is cancelled, skipping remaining stages from index {}", index);
            cancelStage.onCancelled(context);

            context.getCompletionFuture().completeExceptionally(new RuntimeException("Pipeline cancelled"));
            return CompletableFuture.completedFuture(null);
        }

        PipelineStage<C> stage = stages.get(index);
        currentStageIndex = index;

        log.debug("Executing stage {}/{}: {}, currState={}", index + 1, stages.size(), stage.getName(),
                context.getStateMachine().getCurrentState());

        if (!stage.canExecute(context)) {
            log.warn("Stage {} cannot execute, skipping", stage.getName());
            return executeStage(index + 1, context);
        }

        stage.onBefore(context);

        return stage.execute(context)
                .thenCompose(result -> {
                    // Check if cancelled during execution
                    if (context.isCancelled()) {
                        log.info("Pipeline cancelled during stage {}", stage.getName());
                        stage.onCancelled(context);
                        return CompletableFuture.completedFuture(null);
                    }

                    stage.onAfter(context, result);

                    if (!result.isSuccess()) {
                        log.error("Stage {} failed: {}", stage.getName(), result.getMessage());
                        // Store error result in context
                        context.getStageData().put("lastError", result);
                        // Continue to next stage or handle error
                        return executeStage(index + 1, context);
                    }

                    log.debug("Stage {} completed successfully", stage.getName());
                    return executeStage(index + 1, context);
                })
                .exceptionally(ex -> {
                    log.error("Stage {} threw exception", stage.getName(), ex);
                    stage.onError(context, ex);
                    // Continue to next stage despite error
                    return null;
                });
    }

    /**
     * Handle pipeline error.
     *
     * @param context pipeline context
     * @param ex      the error
     */
    private void handleError(C context, Throwable ex) {
        context.getStageData().put("error", ex);

        if (errorStage != null) {
            errorStage.execute(context)
                    .whenComplete((result, e) -> {
                        log.info("Error stage completed");
                        context.getCompletionFuture().completeExceptionally(ex);
                    });
        } else {
            context.getCompletionFuture().completeExceptionally(ex);
        }
    }

    /**
     * Cancel the pipeline.
     *
     * @param context pipeline context
     * @return CompletableFuture that completes when cancellation is done
     */
    public CompletableFuture<Void> cancel(C context) {
        log.info("Cancelling pipeline at stage {}/{}", currentStageIndex, stages.size());
        log.info("Cancelling stage : {}", context.hashCode());
        context.cancel();

        List<CompletableFuture<Void>> cancellationFutures = new ArrayList<>();

        // Call onCancelled for current and remaining stages
        for (int i = currentStageIndex; i < stages.size(); i++) {
            PipelineStage<C> stage = stages.get(i);
            stage.onCancelled(context);
        }

        // Cancel current and remaining stages
        for (int i = currentStageIndex; i < stages.size(); i++) {
            PipelineStage<C> stage = stages.get(i);
            CompletableFuture<Void> cancelFuture = stage.cancel(context);
            if (cancelFuture != null) {
                cancellationFutures.add(cancelFuture);
            }
        }

        // Execute cleanup stage if exists
        if (cleanupStage != null) {
            cleanupStage.onCancelled(context);
            CompletableFuture<Void> cleanupFuture = cleanupStage.execute(context)
                    .<Void>thenApply(result -> null)
                    .exceptionally(ex -> {
                        log.warn("Cleanup stage failed", ex);
                        return null;
                    });
            cancellationFutures.add(cleanupFuture);
        }

        context.getCompletionFuture().completeExceptionally(
                new RuntimeException("Pipeline cancelled"));

        // Use allOf to ensure all cancellation operations complete
        return CompletableFuture.allOf(cancellationFutures.toArray(new CompletableFuture[0]));
    }

    /**
     * Get number of stages.
     *
     * @return number of stages
     */
    public int getStageCount() {
        return stages.size();
    }

    /**
     * Create a builder for this pipeline.
     *
     * @param <C> pipeline context type
     * @return builder instance
     */
    public static <C extends PipelineContext> Builder<C> builder() {
        return new Builder<>();
    }

    /**
     * Builder for TaskPipeline.
     *
     * @param <C> pipeline context type
     */
    public static class Builder<C extends PipelineContext> {
        private final List<PipelineStage<C>> stages = new ArrayList<>();
        private PipelineStage<C> errorStage;
        private PipelineStage<C> cancelStage;
        private PipelineStage<C> cleanupStage;

        /**
         * Add a stage to the pipeline.
         *
         * @param stage stage to add
         * @return this builder
         */
        public Builder<C> addStage(PipelineStage<C> stage) {
            if (stage != null) {
                this.stages.add(stage);
            }
            return this;
        }

        /**
         * Set the error handling stage.
         *
         * @param stage error stage
         * @return this builder
         */
        public Builder<C> onError(PipelineStage<C> stage) {
            this.errorStage = stage;
            return this;
        }

        public Builder<C> onCancel(PipelineStage<C> stage) {
            this.cancelStage = stage;
            return this;
        }



        /**
         * Set the cleanup stage.
         *
         * @param stage cleanup stage
         * @return this builder
         */
        public Builder<C> onCleanup(PipelineStage<C> stage) {
            this.cleanupStage = stage;
            return this;
        }

        /**
         * Build the pipeline.
         *
         * @return configured pipeline
         */
        public TaskPipeline<C> build() {
            return new TaskPipeline<>(this);
        }
    }
}
