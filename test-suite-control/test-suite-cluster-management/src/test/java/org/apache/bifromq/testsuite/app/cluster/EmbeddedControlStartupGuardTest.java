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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.bifromq.testsuite.app.cluster.storage.EmbeddedControlGuardState;
import org.apache.bifromq.testsuite.app.cluster.storage.EmbeddedControlStartupGuard;
import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.apache.bifromq.testsuite.config.node.NodeIdentityProperties;
import org.apache.bifromq.testsuite.config.role.NodeRoleProperties;
import org.apache.bifromq.testsuite.config.storage.StorageMode;
import org.apache.bifromq.testsuite.config.storage.StorageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EmbeddedControlStartupGuardTest {

    private static final String OWNER_KEY = "storage:embedded:control-owner";
    private HazelcastInstance hazelcastInstance;

    @AfterEach
    void tearDown() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    void run_givenEmbeddedControlAndNoOwner_shouldClaimOwner() {
        IMap<String, String> ownerMap = mockOwnerMap(null);
        EmbeddedControlStartupGuard guard = guard(ownerMap, Set.of("control-1"), "control-1", false);

        assertThatCode(() -> guard.start()).doesNotThrowAnyException();

        assertThat(guard.state().getStatus()).isEqualTo(EmbeddedControlGuardState.GuardStatus.CLAIMED);
        assertThat(guard.state().getOwnerNodeId()).isEqualTo("control-1");
    }

    @Test
    void run_givenEmbeddedControlAndLiveOtherOwner_shouldFail() {
        IMap<String, String> ownerMap = mockOwnerMap("control-2");
        EmbeddedControlStartupGuard guard = guard(ownerMap, Set.of("control-2"), "control-1", false);

        assertThatThrownBy(() -> guard.start())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("allows only one control-capable node");

        assertThat(guard.state().getStatus()).isEqualTo(EmbeddedControlGuardState.GuardStatus.CONFLICT);
    }

    @Test
    void run_givenDatabaseControl_shouldSkipGuard() {
        IMap<String, String> ownerMap = mockOwnerMap("control-2");
        EmbeddedControlStartupGuard guard =
            guard(ownerMap, Set.of("control-2"), "control-1", false, StorageMode.DATABASE);

        assertThatCode(() -> guard.start()).doesNotThrowAnyException();

        assertThat(guard.state().getStatus()).isEqualTo(EmbeddedControlGuardState.GuardStatus.NOT_REQUIRED);
    }

    @Test
    void releaseOwner_givenClaimedOwner_shouldRemoveLocalOwner() {
        IMap<String, String> ownerMap = mockOwnerMap(null);
        EmbeddedControlStartupGuard guard = guard(ownerMap, Set.of("control-1"), "control-1", false);
        guard.start();
        when(ownerMap.remove(anyString(), anyString())).thenReturn(true);

        guard.releaseOwner();

        verify(ownerMap).remove(OWNER_KEY, "control-1");
    }

    @Test
    void releaseOwner_givenGuardNotClaimed_shouldSkipRemove() {
        IMap<String, String> ownerMap = mockOwnerMap("control-2");
        EmbeddedControlStartupGuard guard =
            guard(ownerMap, Set.of("control-2"), "control-1", false, StorageMode.DATABASE);
        guard.start();

        guard.releaseOwner();

        verify(ownerMap, never()).remove(anyString(), anyString());
    }

    @Test
    void run_givenConcurrentEmbeddedControls_shouldAllowOnlyOneOwner() throws Exception {
        hazelcastInstance = Hazelcast.newHazelcastInstance(hazelcastConfig());
        EmbeddedControlStartupGuard guard1 =
            guard(hazelcastInstance, Set.of("control-1", "control-2"), "control-1", false);
        EmbeddedControlStartupGuard guard2 =
            guard(hazelcastInstance, Set.of("control-1", "control-2"), "control-2", false);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Boolean> results = executor.invokeAll(List.of(
                    runGuard(guard1),
                    runGuard(guard2)
                ))
                .stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    } catch (ExecutionException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(hazelcastInstance.<String, String>getMap("storage-mode-owners").get(OWNER_KEY))
                .isIn("control-1", "control-2");
        } finally {
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private static IMap<String, String> mockOwnerMap(String existingOwner) {
        IMap<String, String> ownerMap = mock(IMap.class);
        when(ownerMap.putIfAbsent(anyString(), anyString())).thenReturn(existingOwner);
        return ownerMap;
    }

    private static EmbeddedControlStartupGuard guard(IMap<String, String> ownerMap, Set<String> liveMemberIds,
                                                     String nodeId, boolean allowTakeover) {
        return guard(ownerMap, liveMemberIds, nodeId, allowTakeover, StorageMode.EMBEDDED);
    }

    private static EmbeddedControlStartupGuard guard(IMap<String, String> ownerMap, Set<String> liveMemberIds,
                                                     String nodeId, boolean allowTakeover, StorageMode storageMode) {
        HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
        Cluster cluster = cluster(liveMemberIds);
        when(hazelcastInstance.<String, String>getMap(anyString())).thenReturn(ownerMap);
        when(hazelcastInstance.getCluster()).thenReturn(cluster);
        return guard(hazelcastInstance, liveMemberIds, nodeId, allowTakeover, storageMode);
    }

    private static EmbeddedControlStartupGuard guard(HazelcastInstance hazelcastInstance, Set<String> liveMemberIds,
                                                     String nodeId, boolean allowTakeover) {
        return guard(hazelcastInstance, liveMemberIds, nodeId, allowTakeover, StorageMode.EMBEDDED);
    }

    private static EmbeddedControlStartupGuard guard(HazelcastInstance hazelcastInstance, Set<String> liveMemberIds,
                                                     String nodeId, boolean allowTakeover, StorageMode storageMode) {
        NodeIdentityProperties nodeIdentityProperties = mock(NodeIdentityProperties.class);
        when(nodeIdentityProperties.getNodeId()).thenReturn(nodeId);
        NodeRoleProperties nodeRoleProperties = mock(NodeRoleProperties.class);
        when(nodeRoleProperties.getNodeRole()).thenReturn(NodeRole.CONTROL);
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setMode(storageMode);
        storageProperties.getEmbedded().setAllowControlTakeover(allowTakeover);
        return new EmbeddedControlStartupGuard(
            hazelcastInstance, nodeIdentityProperties, nodeRoleProperties, storageProperties);
    }

    private static Cluster cluster(Set<String> liveMemberIds) {
        Cluster cluster = mock(Cluster.class);
        Set<Member> members = liveMemberIds.stream()
            .map(EmbeddedControlStartupGuardTest::member)
            .collect(java.util.stream.Collectors.toSet());
        when(cluster.getMembers()).thenReturn(members);
        return cluster;
    }

    private static Member member(String nodeId) {
        Member member = mock(Member.class);
        when(member.getAttribute(NodeIdentityProperties.NODE_ID_MEMBER_ATTRIBUTE)).thenReturn(nodeId);
        return member;
    }

    private static Callable<Boolean> runGuard(EmbeddedControlStartupGuard guard) {
        return () -> {
            try {
                guard.start();
                return true;
            } catch (IllegalStateException e) {
                return false;
            }
        };
    }

    private static Config hazelcastConfig() {
        Config config = new Config();
        config.setClusterName("embedded-control-guard-" + UUID.randomUUID());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        return config;
    }
}
