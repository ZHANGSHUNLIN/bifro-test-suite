
package com.baidu.iot.test.suite.pipeline.stages;

import java.util.concurrent.CompletableFuture;

import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Stage for general cleanup operations.
 */
@Slf4j
public class GeneralCleanupStage implements PipelineStage<PipelineContext> {

    @Override
    public String getName() {
        return "GeneralCleanup";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        log.info("Executing general cleanup for task");

        // Clear stage data
        context.getStageData().clear();

        // Mark completion
        context.getCompletionFuture().complete(null);

        return CompletableFuture.completedFuture(StageResult.success("Cleanup completed"));
    }

    @Override
    public void onError(PipelineContext context, Throwable error) {
        log.error("Error during cleanup", error);
    }
}
