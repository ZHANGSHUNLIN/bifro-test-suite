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
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;

@Slf4j
public class ErrorHandlingStage implements PipelineStage<TaskPipelineContext> {

    @Override
    public String getName() {
        return "ErrorHandling";
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
        Object error = context.getStageData().get("error");
        Object lastError = context.getStageData().get("lastError");

        if (error instanceof Throwable) {
            Throwable ex = (Throwable) error;
            log.error("Handling pipeline error: {}", ex.getMessage(), ex);

            String taskId = context.getExecutionContext().taskId();
            context.getStateMachine()
                .transition(TaskEvent.FAILURE)
                .whenComplete((success, e) -> {
                    if (success) {
                        log.info("Transitioned to FAILED state, taskId={}", taskId);
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
    public void onError(TaskPipelineContext context, Throwable error) {
        log.error("Error in error handling stage", error);
    }
}
