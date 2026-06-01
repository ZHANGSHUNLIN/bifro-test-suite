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

package org.apache.bifromq.testsuite.app.cluster.core;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.apache.bifromq.testsuite.app.eventbus.NodeQueryGateway;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryRequest;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryResponse;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClientInstanceService {

    @Resource
    private NodeQueryGateway nodeQueryGateway;

    @Resource
    private ClusterDataManager clusterDataManager;
    public ClientQueryResponse queryClientInstances(String nodeId, String taskId, String clientType, int page,
                                                    int size) {
        log.info("queryClientInstances called: nodeId={}, taskId={}, clientType={}", nodeId, taskId, clientType);

        try {
            var nodeMap = clusterDataManager.allMemberNodes()
                .orTimeout(3, TimeUnit.SECONDS)
                .join();
            log.info("nodeMap keys: {}, looking for nodeId: {}", nodeMap.keySet(), nodeId);
            NodeInfo nodeInfo = nodeMap.get(nodeId);
            if (nodeInfo == null || !nodeInfo.isAlive()) {
                return ClientQueryResponse.builder()
                    .success(false)
                    .errorMessage(Messages.get("error.node.offline"))
                    .clients(java.util.Collections.emptyList())
                    .total(0)
                    .page(page)
                    .size(size)
                    .totalPages(0)
                    .build();
            }
        } catch (Exception e) {
            log.error("Failed to check node status, nodeId={}", nodeId, e);
            return ClientQueryResponse.builder()
                .success(false)
                .errorMessage(Messages.get("error.node.offline"))
                .clients(java.util.Collections.emptyList())
                .total(0)
                .page(page)
                .size(size)
                .totalPages(0)
                .build();
        }
        ClientQueryRequest request = ClientQueryRequest.builder()
            .taskId(taskId)
            .clientType(clientType)
            .page(page)
            .size(size)
            .build();

        try {
            return nodeQueryGateway.queryClients(nodeId, request).join();
        } catch (Exception e) {
            log.error("Failed to query client instances, nodeId={}, taskId={}, clientType={}",
                nodeId, taskId, clientType, e);
            return ClientQueryResponse.builder()
                .success(false)
                .errorMessage(Messages.get("error.node.queryTimeout"))
                .clients(java.util.Collections.emptyList())
                .total(0)
                .page(page)
                .size(size)
                .totalPages(0)
                .build();
        }
    }
}
