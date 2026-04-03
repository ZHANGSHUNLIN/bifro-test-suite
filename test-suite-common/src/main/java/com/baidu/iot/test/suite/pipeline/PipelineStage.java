
package com.baidu.iot.test.suite.pipeline;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.TaskEvent;

/**
 * Interface for a pipeline stage.
 *
 * @param <C> pipeline context type
 */
public interface PipelineStage<C extends PipelineContext> {

    /**
     * Get the name of this stage.
     *
     * @return stage name
     */
    String getName();

    /**
     * Execute this stage.
     *
     * @param context pipeline context
     * @return CompletableFuture that completes with the stage result
     */
    CompletableFuture<StageResult> execute(C context);

    /**
     * Check if this stage can be executed.
     *
     * @param context pipeline context
     * @return true if can execute
     */
    default boolean canExecute(C context) {
        return !context.isCancelled();
    }

    /**
     * Called before executing this stage.
     *
     * @param context pipeline context
     */
    default void onBefore(C context) {
    }

    /**
     * Called after executing this stage.
     *
     * @param context pipeline context
     * @param result  stage execution result
     */
    default void onAfter(C context, StageResult result) {
    }

    /**
     * Called when an error occurs during execution.
     *
     * @param context pipeline context
     * @param error   the error
     */
    default void onError(C context, Throwable error) {
    }

    /**
     * Called when this stage times out.
     *
     * @param context        pipeline context
     * @param timeoutMessage timeout message
     */
    default void onTimeout(C context, String timeoutMessage) {
    }

    /**
     * Called when this stage is cancelled.
     *
     * @param context pipeline context
     */
    default void onCancelled(C context) {
    }

    /**
     * Cancel this stage.
     *
     * @param context pipeline context
     * @return CompletableFuture that completes when cancellation is done
     */
    default CompletableFuture<Void> cancel(C context) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Create a state transition to a target stage.
     *
     * @param targetStage target state
     * @param event       triggering event
     * @param context     pipeline context
     * @return CompletableFuture that completes when transition is done
     */
    default CompletableFuture<Boolean> transitionTo(TaskStage targetStage, TaskEvent event, C context) {
        return context.getStateMachine().transition(event,
                Map.of("targetState", targetStage, "stage", getName()));
    }

    /**
     * Create a stage that wraps this one with a state transition.
     *
     * @param targetStage target state
     * @param event       triggering event
     * @return wrapped stage
     */
    default PipelineStage<C> withTransition(TaskStage targetStage, TaskEvent event) {
        return new PipelineStage<>() {
            @Override
            public String getName() {
                return PipelineStage.this.getName();
            }

            @Override
            public CompletableFuture<StageResult> execute(C context) {
                return PipelineStage.this.execute(context)
                        .thenCompose(result -> {
                            if (result.isSuccess()) {
                                return context.getStateMachine().transition(event)
                                        .thenApply(success -> {
                                            if (!success) {
                                                return StageResult.failure(
                                                        "Failed to transition to " + targetStage);
                                            }
                                            return result;
                                        });
                            }
                            return CompletableFuture.completedFuture(result);
                        });
            }

            @Override
            public boolean canExecute(C context) {
                return PipelineStage.this.canExecute(context);
            }

            @Override
            public void onBefore(C context) {
                PipelineStage.this.onBefore(context);
            }

            @Override
            public void onAfter(C context, StageResult result) {
                PipelineStage.this.onAfter(context, result);
            }

            @Override
            public void onError(C context, Throwable error) {
                PipelineStage.this.onError(context, error);
            }

            @Override
            public void onTimeout(C context, String timeoutMessage) {
                PipelineStage.this.onTimeout(context, timeoutMessage);
            }

            @Override
            public void onCancelled(C context) {
                PipelineStage.this.onCancelled(context);
            }

            @Override
            public CompletableFuture<Void> cancel(C context) {
                return PipelineStage.this.cancel(context);
            }
        };
    }
}
