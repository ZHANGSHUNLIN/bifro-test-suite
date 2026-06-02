/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.scheduler;

import io.vertx.core.Vertx;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.eventbus.EventBusAddresses;

@Slf4j
public class ScheduledTaskEventBusRegistrar {
    private final Vertx vertx;
    private final DelayedTaskScheduler scheduler;
    private final String nodeId;

    public ScheduledTaskEventBusRegistrar(Vertx vertx, DelayedTaskScheduler scheduler, String nodeId) {
        this.vertx = vertx;
        this.scheduler = scheduler;
        this.nodeId = nodeId;
    }

    public void register() {
        vertx.eventBus().<ScheduledTaskRequest>consumer(
            EventBusAddresses.delayedTaskSchedule(nodeId),
            message -> message.reply(scheduler.schedule(message.body())));
        vertx.eventBus().<ScheduledTaskCancelRequest>consumer(
            EventBusAddresses.delayedTaskCancel(nodeId),
            message -> {
                ScheduledTaskCancelRequest request = message.body();
                String taskKey = request == null ? null : request.getTaskKey();
                boolean cancelled = scheduler.cancel(taskKey);
                message.reply(ScheduledTaskCancelResponse.builder()
                    .success(cancelled)
                    .taskKey(taskKey)
                    .errorMessage(cancelled ? null : "Scheduled task not found")
                    .build());
            });
        vertx.eventBus().<ScheduledTaskQueryRequest>consumer(
            EventBusAddresses.delayedTaskQuery(nodeId),
            message -> {
                ScheduledTaskQueryRequest request = message.body();
                List<ScheduledTaskView> tasks = scheduler.listPending().stream()
                    .filter(task -> request == null || request.getKind() == null || task.getKind() == request.getKind())
                    .toList();
                message.reply(ScheduledTaskQueryResponse.builder()
                    .success(true)
                    .tasks(tasks)
                    .build());
            });
        log.info("Scheduled task EventBus consumers registered for node={}", nodeId);
    }
}
