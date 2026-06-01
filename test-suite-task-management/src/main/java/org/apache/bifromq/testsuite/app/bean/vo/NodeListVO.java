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

package org.apache.bifromq.testsuite.app.bean.vo;

import org.apache.bifromq.testsuite.app.bean.ClusterNodeInfo;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeListVO {

    private String nodeId;
    private String nodeName;
    private String host;
    private boolean alive;
    private long lastHeartbeatAt;
    private ClusterNodeInfo.MemoryInfo memory;
    private ClusterNodeInfo.CpuInfo cpu;

    public static NodeListVO fromNodeInfo(String nodeId, NodeInfo nodeInfo) {
        ClusterNodeInfo clusterNodeInfo = nodeInfo.getClusterNodeInfo();
        return NodeListVO.builder()
            .nodeId(nodeId)
            .nodeName(nodeInfo.getNodeName())
            .host(clusterNodeInfo != null ? clusterNodeInfo.getHost() : null)
            .alive(nodeInfo.isAlive())
            .lastHeartbeatAt(clusterNodeInfo != null ? clusterNodeInfo.getTimestamp() : 0)
            .memory(clusterNodeInfo != null ? clusterNodeInfo.getMemory() : null)
            .cpu(clusterNodeInfo != null ? clusterNodeInfo.getCpu() : null)
            .build();
    }

}
