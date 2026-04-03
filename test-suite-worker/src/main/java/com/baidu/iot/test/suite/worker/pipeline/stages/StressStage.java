package com.baidu.iot.test.suite.worker.pipeline.stages;

import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;
import com.baidu.iot.test.suite.worker.TaskConfig;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * stress duration time
 */
@Slf4j
public class StressStage implements PipelineStage<PipelineContext> {

    private final Vertx vertx;
    private final TaskConfig taskConfig;
    private final CompletableFuture<StageResult> stageFuture = new CompletableFuture<>();
    private Long timerId;

    public StressStage(Vertx vertx, TaskConfig taskConfig) {
        this.vertx = vertx;
        this.taskConfig = taskConfig;
    }

    @Override
    public String getName() {
        return "Stress";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {

        int stressDurationInSec = taskConfig.getStressDurationInSec();
        timerId = vertx.setTimer(stressDurationInSec * 1000L, timerId -> {
            log.info("Stress duration time up, stop connect clients");
            stageFuture.complete(StageResult.success());
        });

        return stageFuture;
    }

    @Override
    public void onBefore(PipelineContext context) {
        log.info("stress stage start");
    }

    @Override
    public void onAfter(PipelineContext context, StageResult result) {
        log.info("stress stage end");
        if (timerId != null) {
            vertx.cancelTimer(timerId);
        }
    }

    @Override
    public void onError(PipelineContext context, Throwable error) {
        log.error("Error during client stress", error);
        if (timerId != null) {
            vertx.cancelTimer(timerId);
        }
    }

    @Override
    public CompletableFuture<Void> cancel(PipelineContext context) {
        if (timerId != null) {
            vertx.cancelTimer(timerId);
        }
        if (!stageFuture.isDone()) {
            stageFuture.complete(StageResult.success());
        }
        return CompletableFuture.completedFuture(null);
    }
}
