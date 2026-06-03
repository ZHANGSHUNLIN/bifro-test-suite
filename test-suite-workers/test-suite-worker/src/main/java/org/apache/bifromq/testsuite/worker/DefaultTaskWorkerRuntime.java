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

package org.apache.bifromq.testsuite.worker;

import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.config.role.ConditionalOnWorkerPlane;
import org.apache.bifromq.testsuite.metric.NodeMetricsRequest;
import org.apache.bifromq.testsuite.metric.NodeMetricsResponse;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryRequest;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryResponse;
import org.apache.bifromq.testsuite.worker.pojo.TaskMetricsCleanupRequest;
import org.apache.bifromq.testsuite.worker.pojo.TaskMetricsCleanupResponse;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWorkerPlane
public class DefaultTaskWorkerRuntime implements TaskWorkerRuntime {

    private final ClientQueryService clientQueryService = new ClientQueryService();
    private final MetricsQueryService metricsQueryService = new MetricsQueryService();

    @Override
    public TaskWorker create(Vertx vertx, WorkerTaskCommand command) {
        return TaskWorkerFactory.create(vertx, WorkerTaskCommandPlanMapper.toWorkerPlanSpec(command));
    }

    @Override
    public Map<String, TaskStage> runningTaskStages(Map<String, TaskWorker> runningTaskMap) {
        return runningTaskMap.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().getTaskState()));
    }

    @Override
    public ClientQueryResponse queryClients(ClientQueryRequest request, TaskWorker taskWorker) {
        if (taskWorker instanceof BaseTaskWorker baseWorker) {
            return clientQueryService.query(request, baseWorker.getClientTaskMap(request.getClientType()));
        }
        return ClientQueryResponse.builder()
            .success(true)
            .clients(List.of())
            .total(0)
            .page(request.getPage())
            .size(request.getSize())
            .totalPages(0)
            .build();
    }

    @Override
    public NodeMetricsResponse queryMetrics(NodeMetricsRequest request) {
        return metricsQueryService.query(request);
    }

    @Override
    public TaskMetricsCleanupResponse cleanupMetrics(TaskMetricsCleanupRequest request) {
        return metricsQueryService.cleanup(request);
    }
}
