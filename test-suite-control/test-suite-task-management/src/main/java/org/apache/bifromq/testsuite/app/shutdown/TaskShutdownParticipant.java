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

package org.apache.bifromq.testsuite.app.shutdown;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.cluster.core.ClusterDataManager;
import org.apache.bifromq.testsuite.app.local.LocalTaskCoordinator;
import org.apache.bifromq.testsuite.config.role.ConditionalOnWorkerPlane;
import org.apache.bifromq.testsuite.worker.pojo.TaskStopContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnWorkerPlane
public class TaskShutdownParticipant implements ShutdownParticipant {

    private final LocalTaskCoordinator localTaskCoordinator;
    private final ClusterDataManager clusterDataManager;
    private final GracefulShutdownProperties properties;

    @Override
    public String name() {
        return "local-task-shutdown";
    }

    @Override
    public int order() {
        return -100;
    }

    @Override
    public Duration timeout(GracefulShutdownProperties properties) {
        return properties.getTaskStopTimeout();
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        String nodeId = clusterDataManager.getCurrentNodeIdCache();
        TaskStopContext context = TaskStopContext.serviceShutdown(nodeId);
        localTaskCoordinator.markShuttingDown();
        if (properties.isLogPendingTasks()) {
            log.info("Local running tasks before shutdown: {}", localTaskCoordinator.runningTask());
        }
        return localTaskCoordinator.stopAllRunningTasks(context, properties.getTaskStopPerTaskTimeout())
            .orTimeout(properties.getTaskStopTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }
}
