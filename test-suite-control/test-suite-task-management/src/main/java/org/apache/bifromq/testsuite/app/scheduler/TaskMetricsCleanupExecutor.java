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

package org.apache.bifromq.testsuite.app.scheduler;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.scheduler.ScheduledTaskContext;
import org.apache.bifromq.testsuite.scheduler.ScheduledTaskExecutionResult;
import org.apache.bifromq.testsuite.scheduler.ScheduledTaskExecutor;
import org.apache.bifromq.testsuite.scheduler.ScheduledTaskKind;

@Slf4j
public class TaskMetricsCleanupExecutor implements ScheduledTaskExecutor {
    public static final String PAYLOAD_TASK_ID = "taskId";
    public static final String PAYLOAD_NODE_ID = "nodeId";

    @Override
    public ScheduledTaskKind kind() {
        return ScheduledTaskKind.TASK_METRICS_CLEANUP;
    }

    @Override
    public CompletionStage<ScheduledTaskExecutionResult> execute(ScheduledTaskContext context) {
        Map<String, String> payload = context.getPayload();
        String taskId = payload == null ? null : payload.get(PAYLOAD_TASK_ID);
        String nodeId = payload == null ? null : payload.get(PAYLOAD_NODE_ID);
        if (taskId == null || taskId.isBlank() || nodeId == null || nodeId.isBlank()) {
            return CompletableFuture.completedFuture(ScheduledTaskExecutionResult.builder()
                .success(false)
                .message("taskId and nodeId are required")
                .build());
        }
        int removedMeterCount = MetricsHelper.removeMetersForTaskNode(taskId, nodeId);
        log.info("Delayed task metrics cleanup completed, taskId={}, nodeId={}, removedMeterCount={}",
            taskId, nodeId, removedMeterCount);
        return CompletableFuture.completedFuture(ScheduledTaskExecutionResult.builder()
            .success(true)
            .message("removedMeterCount=" + removedMeterCount)
            .build());
    }
}
