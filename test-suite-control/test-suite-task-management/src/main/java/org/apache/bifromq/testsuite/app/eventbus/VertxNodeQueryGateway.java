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

package org.apache.bifromq.testsuite.app.eventbus;

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import java.util.concurrent.CompletableFuture;
import org.apache.bifromq.testsuite.eventbus.EventBusAddresses;
import org.apache.bifromq.testsuite.eventbus.EventBusRequestKind;
import org.apache.bifromq.testsuite.eventbus.VertxEventBusClient;
import org.apache.bifromq.testsuite.metric.NodeMetricsRequest;
import org.apache.bifromq.testsuite.metric.NodeMetricsResponse;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryRequest;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryResponse;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckRequest;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckResponse;
import org.apache.bifromq.testsuite.worker.pojo.TaskMetricsCleanupRequest;
import org.apache.bifromq.testsuite.worker.pojo.TaskMetricsCleanupResponse;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnControlPlane
public class VertxNodeQueryGateway implements NodeQueryGateway {

    private final VertxEventBusClient client;

    public VertxNodeQueryGateway(VertxEventBusClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<NodeMetricsResponse> queryMetrics(NodeMetricsRequest request) {
        return client.request(EventBusAddresses.nodeMetrics(request.getNodeId()),
            request, EventBusRequestKind.NODE_METRICS);
    }

    @Override
    public CompletableFuture<ClientQueryResponse> queryClients(String nodeId, ClientQueryRequest request) {
        return client.request(EventBusAddresses.nodeClients(nodeId), request, EventBusRequestKind.CLIENT_QUERY);
    }

    @Override
    public CompletableFuture<LocalPortCapacityCheckResponse> checkLocalPortCapacity(
        String nodeId, LocalPortCapacityCheckRequest request) {
        return client.request(EventBusAddresses.localPortCapacity(nodeId),
            request, EventBusRequestKind.LOCAL_PORT_CAPACITY);
    }

    @Override
    public CompletableFuture<TaskMetricsCleanupResponse> cleanupTaskMetrics(
        String nodeId, TaskMetricsCleanupRequest request) {
        return client.request(EventBusAddresses.taskMetricsCleanup(nodeId),
            request, EventBusRequestKind.TASK_METRICS_CLEANUP);
    }
}
