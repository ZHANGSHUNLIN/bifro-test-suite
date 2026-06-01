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
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.PubMqttClientTask;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageExecutionScope;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;

@Slf4j
public class StressStage implements PipelineStage<TaskPipelineContext> {

    private final Vertx vertx;
    private Long timerId;

    public StressStage(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public String getName() {
        return "Stress";
    }

    @Override
    public String getLabel() {
        return Messages.get("pipeline.stage.stress");
    }

    public TaskEvent getTriggerEvent() {
        return TaskEvent.SHUTTING;
    }

    @Override
    public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
        StageExecutionScope scope = context.stageScopeOrCreate(getName());
        scope.register("node-publish-scheduler", () -> context.stopNodePublishScheduler());
        scope.register("pub-clients-publishing", () -> stopPubClients(context));
        int stressDurationInSec = context.getExecutionContext().stressDurationInSec();
        timerId = vertx.setTimer(stressDurationInSec * 1000L, ignored -> context.wrapStage(getName(), () -> {
            log.info("Stress duration time up, stop connect clients, taskId={}, duration={}s",
                context.getExecutionContext().taskId(), stressDurationInSec);
            if (!scope.isCancelled()) {
                scope.complete(StageResult.success());
            }
        }).run());
        scope.register("stress-duration-timer", () -> {
            if (timerId != null) {
                vertx.cancelTimer(timerId);
            }
        });
        return scope.result();
    }

    @Override
    public void onBefore(TaskPipelineContext context) {
        log.info("Stress stage start, taskId={}, duration={}s",
            context.getExecutionContext().taskId(), context.getExecutionContext().stressDurationInSec());
    }

    @Override
    public void onAfter(TaskPipelineContext context, StageResult result) {
        log.info("Stress stage end, taskId={}", context.getExecutionContext().taskId());
        if (timerId != null) {
            vertx.cancelTimer(timerId);
        }
        context.stopNodePublishScheduler();
        stopPubClients(context);
        MetricsHelper.freezeTimerSnapshots(
            context.getExecutionContext().taskId(),
            context.getExecutionContext().nodeId());
    }

    private void stopPubClients(TaskPipelineContext context) {
        var pubClients = context.getExecutionContext().pubClients();
        if (pubClients == null || pubClients.isEmpty()) {
            return;
        }
        int stopped = 0;
        for (var client : pubClients.values()) {
            if (client instanceof PubMqttClientTask pub) {
                pub.stopPublishing();
                stopped++;
            }
        }
        log.info("Stopped publishing for {} pub clients, taskId={}", stopped, context.getExecutionContext().taskId());
    }

    @Override
    public void onError(TaskPipelineContext context, Throwable error) {
        log.error("Error during client stress", error);
        if (timerId != null) {
            vertx.cancelTimer(timerId);
        }
        context.stopNodePublishScheduler();
        stopPubClients(context);
    }

    @Override
    public CompletableFuture<Void> cancel(TaskPipelineContext context) {
        context.stageScopeOrCreate(getName()).cancel();
        if (timerId != null) {
            vertx.cancelTimer(timerId);
        }
        context.stopNodePublishScheduler();
        stopPubClients(context);
        return CompletableFuture.completedFuture(null);
    }
}
