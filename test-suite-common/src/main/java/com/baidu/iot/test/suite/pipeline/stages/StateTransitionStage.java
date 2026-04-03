
package com.baidu.iot.test.suite.pipeline.stages;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.baidu.iot.test.suite.TaskEvent;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Stage that performs a state transition.
 */
@Slf4j
public class StateTransitionStage implements PipelineStage<PipelineContext> {

    private final TaskStage targetStage;
    private final TaskEvent event;

    public StateTransitionStage(TaskStage targetStage, TaskEvent event) {
        this.targetStage = targetStage;
        this.event = event;
    }

    @Override
    public String getName() {
        return "StateTransition-" + targetStage.name();
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        return context.getStateMachine()
                .transition(event, Map.of("targetState", targetStage, "stage", getName()))
                .thenApply(success -> {
                    if (success) {
                        log.info("State transitioned to {}", targetStage);
                        return StageResult.success("Transitioned to " + targetStage);
                    } else {
                        log.warn("State transition to {} failed", targetStage);
                        return StageResult.failure("Cannot transition to " + targetStage);
                    }
                });
    }

    @Override
    public boolean canExecute(PipelineContext context) {
        return !context.isCancelled() && context.getStateMachine().canTransition(event);
    }

    @Override
    public void onBefore(PipelineContext context) {
        log.debug("Preparing to transition to state: {}", targetStage);
    }

    @Override
    public void onAfter(PipelineContext context, StageResult result) {
        if (result.isSuccess()) {
            log.debug("Successfully transitioned to state: {}", targetStage);
        }
    }

    @Override
    public void onError(PipelineContext context, Throwable error) {
        log.error("Error during state transition to: {}", targetStage, error);
    }
}
