
package com.baidu.iot.test.suite.pipeline.stages;

import java.util.concurrent.CompletableFuture;

import com.baidu.iot.test.suite.TaskEvent;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Stage for handling errors in the pipeline.
 */
@Slf4j
public class ErrorHandlingStage implements PipelineStage<PipelineContext> {

    @Override
    public String getName() {
        return "ErrorHandling";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        Object error = context.getStageData().get("error");
        Object lastError = context.getStageData().get("lastError");

        if (error instanceof Throwable) {
            Throwable ex = (Throwable) error;
            log.error("Handling pipeline error: {}", ex.getMessage(), ex);

            // Transition to FAILED state
            context.getStateMachine()
                    .transition(TaskEvent.FAILURE)
                    .whenComplete((success, e) -> {
                        if (success) {
                            log.info("Transitioned to FAILED state");
                        }
                    });
        }

        if (lastError instanceof StageResult) {
            StageResult stageResult = (StageResult) lastError;
            log.error("Last stage failed: {}", stageResult.getMessage());
        }

        return CompletableFuture.completedFuture(StageResult.success("Error handling completed"));
    }

    @Override
    public void onError(PipelineContext context, Throwable error) {
        log.error("Error in error handling stage", error);
    }
}
