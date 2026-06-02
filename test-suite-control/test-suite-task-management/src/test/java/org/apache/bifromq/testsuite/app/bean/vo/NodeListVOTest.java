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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.bifromq.testsuite.app.bean.ClusterNodeInfo;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.junit.jupiter.api.Test;

class NodeListVOTest {

    @Test
    void fromNodeInfoShouldExposeNetworkInterfaces() {
        ClusterNodeInfo.NetworkInterfaceInfo networkInterface =
            ClusterNodeInfo.NetworkInterfaceInfo.builder()
                .name("eth0")
                .displayName("eth0")
                .up(true)
                .loopback(false)
                .virtual(false)
                .multicastSupported(true)
                .mtu(1500)
                .addresses(List.of("10.99.48.10", "fe80::1%eth0"))
                .build();
        ClusterNodeInfo clusterNodeInfo = ClusterNodeInfo.builder()
            .host("worker-host")
            .networkInterfaces(List.of(networkInterface))
            .build();
        NodeInfo nodeInfo = NodeInfo.builder()
            .nodeName("worker-1")
            .role(NodeRole.WORKER)
            .clusterNodeInfo(clusterNodeInfo)
            .build();

        NodeListVO nodeListVO = NodeListVO.fromNodeInfo("worker-1", nodeInfo);

        assertThat(nodeListVO.getNetworkInterfaces()).hasSize(1);
        assertThat(nodeListVO.getNetworkInterfaces().get(0).getName()).isEqualTo("eth0");
        assertThat(nodeListVO.getNetworkInterfaces().get(0).getAddresses())
            .containsExactly("10.99.48.10", "fe80::1%eth0");
    }

    @Test
    void fromNodeInfoShouldUseEmptyNetworkInterfacesWhenSystemInfoMissing() {
        NodeInfo nodeInfo = NodeInfo.builder()
            .nodeName("worker-1")
            .role(NodeRole.WORKER)
            .build();

        NodeListVO nodeListVO = NodeListVO.fromNodeInfo("worker-1", nodeInfo);

        assertThat(nodeListVO.getNetworkInterfaces()).isEmpty();
    }
}
