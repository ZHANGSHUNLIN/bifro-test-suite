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

import io.vertx.core.Vertx;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.eventbus.ClusterTaskCommandBus;
import org.apache.bifromq.testsuite.eventbus.VertxClusterTaskCommandBus;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;

@Slf4j
public class TaskFinishEventStage implements PipelineStage<TaskPipelineContext> {

    @Override
    public String getName() {
        return "TaskFinishEvent";
    }

    @Override
    public String getLabel() {
        return Messages.get("pipeline.stage.complete");
    }

    @Override
    public TaskEvent getTriggerEvent() {
        return TaskEvent.SHUTDOWN;
    }

    @Override
    public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
        String taskId = context.getExecutionContext().taskId();
        Vertx vertx = context.getVertx();

        MetricsHelper.counter(BifroTaskMetric.TASK_COMPLETE_COUNT,
            io.micrometer.core.instrument.Tags.of(
                "taskId", taskId,
                "nodeId", context.getExecutionContext().nodeId(),
                "taskType", context.getExecutionContext().taskTypeName()));

        Consumer<String> durationStopCallback = context.getOnTaskComplete();
        if (durationStopCallback != null) {
            durationStopCallback.accept("completed");
        }

        ClusterTaskCommandBus commandBus = new VertxClusterTaskCommandBus(vertx.eventBus());
        commandBus.broadcastTaskFinished(taskId, context.getExecutionContext().nodeId());

        return CompletableFuture.completedFuture(StageResult.success());
    }

    @Override
    public void onBefore(TaskPipelineContext context) {
        String taskId = context.getExecutionContext().taskId();
        log.info("TaskFinishEventStage start, taskId={}", taskId);
    }

    @Override
    public void onAfter(TaskPipelineContext context, StageResult result) {
        String taskId = context.getExecutionContext().taskId();
        log.info("TaskFinishEventStage end, taskId={}, result={}", taskId, result);
    }

    @Override
    public void onError(TaskPipelineContext context, Throwable error) {
        log.error("Error during cleanup", error);
    }
}
