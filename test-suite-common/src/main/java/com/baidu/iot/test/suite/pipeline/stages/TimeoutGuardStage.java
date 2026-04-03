
package com.baidu.iot.test.suite.pipeline.stages;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

/**
 * Stage that wraps another stage with a timeout guard.
 */
@Slf4j
public class TimeoutGuardStage implements PipelineStage<PipelineContext> {

    private final PipelineStage<PipelineContext> delegate;
    private final long timeout;
    private final TimeUnit timeUnit;
    private final String timeoutMessage;
    private long timerId;

    public TimeoutGuardStage(PipelineStage<PipelineContext> delegate, long timeout,
                            TimeUnit timeUnit, String timeoutMessage) {
        this.delegate = delegate;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.timeoutMessage = timeoutMessage;
    }

    public TimeoutGuardStage(PipelineStage<PipelineContext> delegate, long timeoutInSeconds) {
        this(delegate, timeoutInSeconds, TimeUnit.SECONDS, "Stage timed out");
    }

    @Override
    public String getName() {
        return "TimeoutGuard-" + delegate.getName();
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        CompletableFuture<StageResult> result = new CompletableFuture<>();
        Vertx vertx = context.getVertx();

        // Set up timeout
        timerId = vertx.setTimer(timeUnit.toMillis(timeout), id -> {
            if (!result.isDone()) {
                log.warn("Stage {} timed out after {} {}", delegate.getName(), timeout, timeUnit);
                // Call onTimeout callback
                delegate.onTimeout(context, timeoutMessage);
                result.complete(StageResult.failure(timeoutMessage));
            }
        });

        // Execute delegate stage
        delegate.execute(context)
                .whenComplete((stageResult, ex) -> {
                    vertx.cancelTimer(timerId);
                    if (ex != null) {
                        result.completeExceptionally(ex);
                    } else {
                        result.complete(stageResult);
                    }
                });

        return result;
    }

    @Override
    public boolean canExecute(PipelineContext context) {
        return delegate.canExecute(context);
    }

    @Override
    public void onBefore(PipelineContext context) {
        delegate.onBefore(context);
    }

    @Override
    public void onAfter(PipelineContext context, StageResult result) {
        delegate.onAfter(context, result);
    }

    @Override
    public void onError(PipelineContext context, Throwable error) {
        delegate.onError(context, error);
    }

    @Override
    public void onTimeout(PipelineContext context, String timeoutMessage) {
        delegate.onTimeout(context, timeoutMessage);
    }

    @Override
    public void onCancelled(PipelineContext context) {
        delegate.onCancelled(context);
    }

    @Override
    public CompletableFuture<Void> cancel(PipelineContext context) {
        context.getVertx().cancelTimer(timerId);
        return delegate.cancel(context);
    }
}
