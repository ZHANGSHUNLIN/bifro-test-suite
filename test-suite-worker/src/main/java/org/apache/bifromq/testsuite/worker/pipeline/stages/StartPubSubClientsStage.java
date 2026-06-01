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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.PubMqttClientTask;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;
import org.apache.bifromq.testsuite.worker.pipeline.publish.NodePublishScheduler;

@Slf4j
public class StartPubSubClientsStage implements PipelineStage<TaskPipelineContext> {

    private final Vertx vertx;

    public StartPubSubClientsStage(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public String getName() {
        return "StartPubSubClients";
    }

    @Override
    public String getLabel() {
        return Messages.get("pipeline.stage.startPubSub");
    }

    @Override
    public TaskEvent getTriggerEvent() {
        return TaskEvent.ONGOING;
    }

    @Override
    public CompletableFuture<StageResult> execute(TaskPipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();
        String taskId = context.getExecutionContext().taskId();
        String nodeId = context.getExecutionContext().nodeId();

        vertx.executeBlocking(() -> {
            try (var ignored = context.enterStage(getName())) {
                Map<String, MqttClientTask> pubClients = context.getExecutionContext().pubClients();
                Map<String, MqttClientTask> subClients = context.getExecutionContext().subClients();

                if (pubClients != null && !pubClients.isEmpty()) {
                    if (context.isCancelled()) {
                        vertx.runOnContext(v -> context.wrapStage(getName(),
                            () -> future.complete(StageResult.failure("Task cancelled"))).run());
                        return null;
                    }
                    List<PubMqttClientTask> nodePubClients = pubClients.values().stream()
                        .map(PubMqttClientTask.class::cast)
                        .toList();
                    long stageStartMs = System.currentTimeMillis();
                    long timeOriginMs = context.getExecutionContext().dynamicQpsTimeOriginMs(
                        stageStartMs, getName());
                    QpsStrategy strategy = context.getExecutionContext().publishQpsStrategy();
                    NodePublishScheduler scheduler = new NodePublishScheduler(strategy, timeOriginMs, nodePubClients);
                    context.setNodePublishScheduler(scheduler);
                    scheduler.start();
                }

                log.info("Started {} pub clients ({} sub clients already receiving)",
                    pubClients != null ? pubClients.size() : 0,
                    subClients != null ? subClients.size() : 0);

                vertx.runOnContext(v -> context.wrapStage(getName(),
                    () -> future.complete(StageResult.success("Started pub/sub clients"))).run());

            } catch (Exception e) {
                log.error("Failed to start pub/sub clients", e);
                vertx.runOnContext(v -> context.wrapStage(getName(),
                    () -> future.complete(StageResult.failure(e))).run());
            }
            return null;
        });
        return future;
    }

    @Override
    public void onBefore(TaskPipelineContext context) {
        log.info("Starting pub/sub clients, taskId={}", context.getExecutionContext().taskId());
    }
}
