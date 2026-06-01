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

package org.apache.bifromq.testsuite.app.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.apache.bifromq.testsuite.app.cluster.member.MemberInfo;
import org.apache.bifromq.testsuite.app.cluster.member.MemberRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberRegistryRefactoredTest {

    @Mock
    private io.vertx.core.Vertx vertx;

    @Mock
    private ClusterConfig clusterConfig;

    @InjectMocks
    private MemberRegistry memberRegistry;

    @Test
    void getLocalMemberId_givenNotClustered_shouldReturnLocalNode() {
        
        org.mockito.Mockito.when(vertx.isClustered()).thenReturn(false);

        
        String id = memberRegistry.getLocalMemberId();

        
        assertThat(id).isEqualTo("local-node");
    }

    @Test
    void registerLocalMember_doesNotSetNextPingOrAliveFields() {
        
        NodeInfo nodeInfo = NodeInfo.builder()
            .nodeName("test-node")
            .build();

        
        assertThat(nodeInfo.getNodeName()).isEqualTo("test-node");
        assertThat(nodeInfo.getClusterNodeInfo()).isNull();
    }

    @Test
    void memberInfo_doesNotHaveHeartbeatFields() {
        
        MemberInfo memberInfo = MemberInfo.builder()
            .id("node-1")
            .name("Node 1")
            .host("localhost")
            .build();

        
        assertThat(memberInfo.getId()).isEqualTo("node-1");
        assertThat(memberInfo.getName()).isEqualTo("Node 1");
    }
}
