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

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.apache.bifromq.testsuite.app.eventbus.NodeQueryGateway;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.metric.NodeMetricsRequest;
import org.apache.bifromq.testsuite.metric.NodeMetricsResponse;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnControlPlane
public class NodeMetricsService {

    @Resource
    private NodeQueryGateway nodeQueryGateway;

    @Resource
    private ClusterDataManager clusterDataManager;

    public NodeMetricsResponse queryNodeMetrics(String nodeId, String taskId, java.util.List<String> metricNames) {
        try {
            var nodeMap = clusterDataManager.allMemberNodes()
                .orTimeout(3, TimeUnit.SECONDS)
                .join();
            NodeInfo nodeInfo = nodeMap.get(nodeId);
            if (nodeInfo == null || !nodeInfo.isAlive()) {
                return NodeMetricsResponse.builder()
                    .nodeId(nodeId)
                    .success(false)
                    .errorCode("NODE_OFFLINE")
                    .errorMessage(Messages.get("error.node.offline"))
                    .timestamp(System.currentTimeMillis())
                    .counterMetrics(java.util.Collections.emptyList())
                    .timerMetrics(java.util.Collections.emptyList())
                    .build();
            }
        } catch (Exception e) {
            log.error("Failed to check node status, nodeId={}", nodeId, e);
            return NodeMetricsResponse.builder()
                .nodeId(nodeId)
                .success(false)
                .errorCode("NODE_OFFLINE")
                .errorMessage(Messages.get("error.node.offline"))
                .timestamp(System.currentTimeMillis())
                .counterMetrics(java.util.Collections.emptyList())
                .timerMetrics(java.util.Collections.emptyList())
                .build();
        }
        NodeMetricsRequest request = NodeMetricsRequest.builder()
            .nodeId(nodeId)
            .taskId(taskId)
            .metricNames(metricNames)
            .build();

        try {
            return nodeQueryGateway.queryMetrics(request).join();
        } catch (Exception e) {
            log.error("Failed to query node metrics, nodeId={}", nodeId, e);
            return NodeMetricsResponse.builder()
                .nodeId(nodeId)
                .success(false)
                .errorCode("QUERY_TIMEOUT")
                .errorMessage(Messages.get("error.node.queryTimeout"))
                .timestamp(System.currentTimeMillis())
                .counterMetrics(java.util.Collections.emptyList())
                .timerMetrics(java.util.Collections.emptyList())
                .build();
        }
    }

}
