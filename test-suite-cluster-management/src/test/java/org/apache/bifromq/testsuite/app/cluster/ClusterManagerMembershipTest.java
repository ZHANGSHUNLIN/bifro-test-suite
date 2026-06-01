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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.app.cluster.broadcast.EventBroadcaster;
import org.apache.bifromq.testsuite.app.cluster.member.MemberInfo;
import org.apache.bifromq.testsuite.app.cluster.member.MemberRegistry;
import org.apache.bifromq.testsuite.app.cluster.topology.ClusterTopology;
import org.apache.bifromq.testsuite.app.local.LocalTaskCoordinator;
import io.vertx.core.Future;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClusterManagerMembershipTest {

    @Mock
    private MemberRegistry memberRegistry;

    @Mock
    private ClusterConfig clusterConfig;

    @Mock
    private EventBroadcaster broadcaster;

    @Mock
    private LocalTaskCoordinator localTaskCoordinator;

    @Mock
    private io.vertx.core.Vertx vertx;

    @InjectMocks
    private ClusterManager clusterManager;

    @Test
    void refreshTopology_shouldOnlyIncludeLiveMembers() {
        
        MemberInfo node1 = MemberInfo.builder().id("node-1").name("Node1")
            .lastHeartbeat(System.currentTimeMillis()).build();
        MemberInfo node2 = MemberInfo.builder().id("node-2").name("Node2")
            .lastHeartbeat(0).build(); 

        when(memberRegistry.getAllMembers()).thenReturn(
            Future.succeededFuture(Map.of("node-1", node1, "node-2", node2)));
        lenient().when(memberRegistry.getAliveClusterMemberIds()).thenReturn(Set.of("node-1"));
        when(clusterConfig.getHeartbeatTimeout()).thenReturn(Duration.ofSeconds(30));

        
        Future<ClusterTopology> future = clusterManager.refreshTopology();

        
        ClusterTopology topology = future.result();
        assertThat(topology).isNotNull();
        assertThat(topology.getAliveMembers()).containsKey("node-1");
        assertThat(topology.getAliveMembers()).doesNotContainKey("node-2");
    }

    @Test
    void refreshTopology_givenNoLiveMembers_shouldReturnEmptyTopology() {
        
        when(memberRegistry.getAllMembers()).thenReturn(Future.succeededFuture(Map.of()));
        lenient().when(memberRegistry.getAliveClusterMemberIds()).thenReturn(Set.of());

        
        Future<ClusterTopology> future = clusterManager.refreshTopology();

        
        ClusterTopology topology = future.result();
        assertThat(topology.getMemberCount()).isZero();
    }

    @Test
    void getLocalMemberId_shouldDelegateToRegistry() {
        
        when(memberRegistry.getLocalMemberId()).thenReturn("node-local");

        
        String id = clusterManager.getLocalMemberId();

        
        assertThat(id).isEqualTo("node-local");
        verify(memberRegistry).getLocalMemberId();
    }

    @Test
    void getCurrentTopology_givenNoTopologyYet_shouldReturnEmptyTopology() {

        ClusterTopology topology = clusterManager.getCurrentTopology();

        assertThat(topology).isNotNull();
        assertThat(topology.getMembers()).isEmpty();
    }

    @Test
    void checkTimeoutNodes_shouldHandleAndRemoveStaleMemberOnce() throws Exception {
        MemberInfo staleMember = MemberInfo.builder()
            .id("node-stale")
            .name("StaleNode")
            .lastHeartbeat(System.currentTimeMillis() - Duration.ofMinutes(2).toMillis())
            .build();

        when(memberRegistry.getAllMembers()).thenReturn(Future.succeededFuture(Map.of("node-stale", staleMember)));
        when(memberRegistry.getLocalMemberId()).thenReturn("node-local");
        when(memberRegistry.getAliveClusterMemberIds()).thenReturn(Set.of("node-local"));
        when(memberRegistry.removeMember("node-stale")).thenReturn(Future.succeededFuture());
        when(clusterConfig.getHeartbeatTimeout()).thenReturn(Duration.ofSeconds(30));

        Method checkTimeoutNodes = ClusterManager.class.getDeclaredMethod("checkTimeoutNodes");
        checkTimeoutNodes.setAccessible(true);
        checkTimeoutNodes.invoke(clusterManager);

        verify(localTaskCoordinator, times(1)).handleNodeTimeout("node-stale");
        verify(memberRegistry, times(1)).removeMember("node-stale");
    }

    @Test
    void isInitialized_beforeInit_shouldReturnFalse() {

        assertThat(clusterManager.isInitialized()).isFalse();
    }
}
