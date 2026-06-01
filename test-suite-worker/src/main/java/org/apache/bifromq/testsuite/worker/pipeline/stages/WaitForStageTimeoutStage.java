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

package org.apache.bifromq.testsuite.worker.pipeline.stages;

import java.util.concurrent.CompletableFuture;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageExecutionScope;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;

public class WaitForStageTimeoutStage implements PipelineStage<TaskPipelineContext> {

    private final int timeoutSec;

    public WaitForStageTimeoutStage(int timeoutSec) {
        this.timeoutSec = timeoutSec;
    }

    @Override
    public String getName() {
        return "WaitForStageTimeout";
    }

    @Override
    public String getLabel() {
        return Messages.get("pipeline.stage.waitReady");
    }

    @Override
    public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
        StageExecutionScope scope = context.stageScopeOrCreate(getName());
        long id = context.getVertx().setTimer(timeoutSec * 1000L,
            ignored -> context.wrapStage(getName(), () -> {
                if (!scope.isCancelled()) {
                    scope.complete(StageResult.success("Stage timeout reached, ready to start clients"));
                }
            }).run());
        scope.register("stage-timeout-timer", () -> context.getVertx().cancelTimer(id));
        return scope.result();
    }

    @Override
    public CompletableFuture<Void> cancel(TaskPipelineContext context) {
        context.stageScopeOrCreate(getName()).cancel();
        return CompletableFuture.completedFuture(null);
    }
}
