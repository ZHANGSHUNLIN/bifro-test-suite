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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.ChaosClientTask;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;

@Slf4j
public class ExecuteChaosStage implements PipelineStage<TaskPipelineContext> {

    @Override
    public String getName() {
        return "ExecuteChaos";
    }

    @Override
    public String getLabel() {
        return Messages.get("pipeline.stage.execChaos");
    }

    @Override
    public TaskEvent getTriggerEvent() {
        return TaskEvent.SHUTTING;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
        List<ChaosClientTask> clients =
            (List<ChaosClientTask>) context.getStageData().get(InitChaosClientsStage.CHAOS_CLIENTS_KEY);

        if (clients == null || clients.isEmpty()) {
            log.warn("[Chaos] No chaos clients to execute, skipping");
            return CompletableFuture.completedFuture(StageResult.success("No chaos clients"));
        }

        String taskId = context.getExecutionContext().taskId();
        log.info("[Chaos] Executing chaos on {} clients, taskId={}", clients.size(), taskId);

        List<CompletableFuture<Void>> futures = new ArrayList<>(clients.size());
        for (ChaosClientTask client : clients) {
            CompletableFuture<Void> cf = client.connect()
                .thenCompose(v -> client.executeChaos())
                .thenCompose(v -> client.close())
                .exceptionally(ex -> {
                    log.warn("[Chaos] clientId={} error: {}", client.getClientId(), ex.getMessage());

                    client.close().exceptionally(closeEx -> null);
                    return null;
                });
            futures.add(cf);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> StageResult.success("Chaos executed on " + clients.size() + " clients"));
    }

    @Override
    public void onBefore(TaskPipelineContext context) {
        log.info("[Chaos] ExecuteChaosStage start, taskId={}", context.getExecutionContext().taskId());
    }

    @Override
    public void onAfter(TaskPipelineContext context, StageResult result) {
        log.info("[Chaos] ExecuteChaosStage end, taskId={}, result={}",
            context.getExecutionContext().taskId(), result);
    }

    @Override
    public void onError(TaskPipelineContext context, Throwable error) {
        log.error("[Chaos] Error during chaos execution", error);
    }
}
