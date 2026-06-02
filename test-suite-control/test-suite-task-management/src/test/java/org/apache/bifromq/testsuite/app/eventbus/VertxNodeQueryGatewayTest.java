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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VertxNodeQueryGatewayTest {

    private static final String NODE_ID = "node-a";

    @Mock
    private VertxEventBusClient client;

    private VertxNodeQueryGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new VertxNodeQueryGateway(client);
    }

    @Test
    void queryMetricsShouldUseNodeMetricsAddressAndKind() {
        NodeMetricsRequest request = NodeMetricsRequest.builder().nodeId(NODE_ID).taskId("task-a").build();
        when(client.<NodeMetricsResponse>request(
            EventBusAddresses.nodeMetrics(NODE_ID), request, EventBusRequestKind.NODE_METRICS))
            .thenReturn(CompletableFuture.completedFuture(NodeMetricsResponse.builder().build()));

        gateway.queryMetrics(request);

        verify(client).<NodeMetricsResponse>request(
            EventBusAddresses.nodeMetrics(NODE_ID), request, EventBusRequestKind.NODE_METRICS);
    }

    @Test
    void queryClientsShouldUseClientQueryAddressAndKind() {
        ClientQueryRequest request = ClientQueryRequest.builder().taskId("task-a").page(1).size(20).build();
        when(client.<ClientQueryResponse>request(
            EventBusAddresses.nodeClients(NODE_ID), request, EventBusRequestKind.CLIENT_QUERY))
            .thenReturn(CompletableFuture.completedFuture(ClientQueryResponse.builder().build()));

        gateway.queryClients(NODE_ID, request);

        verify(client).<ClientQueryResponse>request(
            EventBusAddresses.nodeClients(NODE_ID), request, EventBusRequestKind.CLIENT_QUERY);
    }

    @Test
    void checkLocalPortCapacityShouldUseLocalPortAddressAndKind() {
        LocalPortCapacityCheckRequest request = LocalPortCapacityCheckRequest.builder()
            .taskId("task-a")
            .nodeId(NODE_ID)
            .assignedClients(100)
            .build();
        when(client.<LocalPortCapacityCheckResponse>request(
            EventBusAddresses.localPortCapacity(NODE_ID), request, EventBusRequestKind.LOCAL_PORT_CAPACITY))
            .thenReturn(CompletableFuture.completedFuture(LocalPortCapacityCheckResponse.builder().build()));

        gateway.checkLocalPortCapacity(NODE_ID, request);

        verify(client).<LocalPortCapacityCheckResponse>request(
            EventBusAddresses.localPortCapacity(NODE_ID), request, EventBusRequestKind.LOCAL_PORT_CAPACITY);
    }
}
