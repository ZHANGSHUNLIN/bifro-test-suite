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

package org.apache.bifromq.testsuite.pipeline.stages;

import org.apache.bifromq.testsuite.pipeline.PipelineContext;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import io.vertx.core.Vertx;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeoutGuardStage implements PipelineStage<PipelineContext<?, ?>> {

    private final PipelineStage<PipelineContext<?, ?>> delegate;
    private final long timeout;
    private final TimeUnit timeUnit;
    private final String timeoutMessage;
    private long timerId;

    
    public TimeoutGuardStage(PipelineStage<PipelineContext<?, ?>> delegate, long timeout,
                             TimeUnit timeUnit, String timeoutMessage) {
        this.delegate = delegate;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.timeoutMessage = timeoutMessage;
    }

    public TimeoutGuardStage(PipelineStage<PipelineContext<?, ?>> delegate, long timeoutInSeconds) {
        this(delegate, timeoutInSeconds, TimeUnit.SECONDS, "Stage timed out");
    }

    @Override
    public String getName() {
        return "TimeoutGuard-" + delegate.getName();
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext<?, ?> context) {
        CompletableFuture<StageResult> result = new CompletableFuture<>();
        Vertx vertx = context.getVertx();

        timerId = vertx.setTimer(timeUnit.toMillis(timeout), id -> context.wrapDiagnosticContext(getName(), () -> {
            if (!result.isDone()) {
                log.warn("Stage {} timed out after {} {}", delegate.getName(), timeout, timeUnit);
                delegate.onTimeout(context, timeoutMessage);
                result.complete(StageResult.failure(timeoutMessage));
            }
        }).run());
        
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
    public boolean canExecute(PipelineContext<?, ?> context) {
        return delegate.canExecute(context);
    }

    @Override
    public void onBefore(PipelineContext<?, ?> context) {
        delegate.onBefore(context);
    }

    @Override
    public void onAfter(PipelineContext<?, ?> context, StageResult result) {
        delegate.onAfter(context, result);
    }

    @Override
    public void onError(PipelineContext<?, ?> context, Throwable error) {
        delegate.onError(context, error);
    }

    @Override
    public void onTimeout(PipelineContext<?, ?> context, String timeoutMsg) {
        delegate.onTimeout(context, timeoutMsg);
    }

    @Override
    public void onCancelled(PipelineContext<?, ?> context) {
        delegate.onCancelled(context);
    }

    @Override
    public CompletableFuture<Void> cancel(PipelineContext<?, ?> context) {
        context.getVertx().cancelTimer(timerId);
        return delegate.cancel(context);
    }
}
