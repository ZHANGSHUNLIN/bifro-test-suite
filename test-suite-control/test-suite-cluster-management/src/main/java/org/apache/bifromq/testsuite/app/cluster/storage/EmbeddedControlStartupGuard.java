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

package org.apache.bifromq.testsuite.app.cluster.storage;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.cluster.shared.ShareDataAddr;
import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.apache.bifromq.testsuite.config.node.NodeIdentityProperties;
import org.apache.bifromq.testsuite.config.role.NodeRoleProperties;
import org.apache.bifromq.testsuite.config.storage.StorageMode;
import org.apache.bifromq.testsuite.config.storage.StorageProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmbeddedControlStartupGuard implements SmartLifecycle {

    static final String OWNER_KEY = "storage:embedded:control-owner";

    private final HazelcastInstance hazelcastInstance;
    private final NodeIdentityProperties nodeIdentityProperties;
    private final NodeRoleProperties nodeRoleProperties;
    private final StorageProperties storageProperties;
    private volatile boolean running;
    private volatile EmbeddedControlGuardState state = EmbeddedControlGuardState.notRequired();

    public EmbeddedControlStartupGuard(HazelcastInstance hazelcastInstance,
                                       NodeIdentityProperties nodeIdentityProperties,
                                       NodeRoleProperties nodeRoleProperties,
                                       StorageProperties storageProperties) {
        this.hazelcastInstance = hazelcastInstance;
        this.nodeIdentityProperties = nodeIdentityProperties;
        this.nodeRoleProperties = nodeRoleProperties;
        this.storageProperties = storageProperties;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        if (storageProperties.getMode() != StorageMode.EMBEDDED
            || !isControlCapable(nodeRoleProperties.getNodeRole())) {
            state = EmbeddedControlGuardState.notRequired();
            running = true;
            return;
        }
        String nodeId = nodeIdentityProperties.getNodeId();
        IMap<String, String> ownerMap = hazelcastInstance.getMap(ShareDataAddr.STORAGE_MODE_OWNERS.getAddr());
        String existingOwner = ownerMap.putIfAbsent(OWNER_KEY, nodeId);
        if (existingOwner == null || nodeId.equals(existingOwner)) {
            state = EmbeddedControlGuardState.claimed(nodeId);
            running = true;
            log.info("Embedded control owner claimed: nodeId={}", nodeId);
            return;
        }

        if (!isLiveControlOwner(existingOwner)) {
            String message = "Embedded control owner points to offline nodeId=" + existingOwner
                + ", current nodeId=" + nodeId + ". Automatic takeover is disabled by default.";
            if (!storageProperties.getEmbedded().isAllowControlTakeover()) {
                state = EmbeddedControlGuardState.conflict(existingOwner, message);
                throw new IllegalStateException(message);
            }
            if (ownerMap.replace(OWNER_KEY, existingOwner, nodeId)) {
                state = EmbeddedControlGuardState.claimed(nodeId);
                running = true;
                log.warn("Embedded control owner taken over: previousOwner={}, nodeId={}", existingOwner, nodeId);
                return;
            }
        }

        String message = "Embedded storage mode allows only one control-capable node. ownerNodeId="
            + existingOwner + ", currentNodeId=" + nodeId;
        state = EmbeddedControlGuardState.conflict(existingOwner, message);
        throw new IllegalStateException(message);
    }

    @Override
    public void stop() {
        releaseOwner();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    @PreDestroy
    public void releaseOwner() {
        if (state.getStatus() != EmbeddedControlGuardState.GuardStatus.CLAIMED) {
            return;
        }
        String nodeId = nodeIdentityProperties.getNodeId();
        IMap<String, String> ownerMap = hazelcastInstance.getMap(ShareDataAddr.STORAGE_MODE_OWNERS.getAddr());
        if (ownerMap.remove(OWNER_KEY, nodeId)) {
            state = EmbeddedControlGuardState.notRequired();
            log.info("Embedded control owner released: nodeId={}", nodeId);
            return;
        }
        state = EmbeddedControlGuardState.notRequired();
        log.warn("Embedded control owner was not released because it is no longer owned by nodeId={}", nodeId);
    }

    public EmbeddedControlGuardState state() {
        return state;
    }

    private boolean isLiveControlOwner(String ownerNodeId) {
        return hazelcastInstance.getCluster().getMembers().stream()
            .anyMatch(member -> ownerNodeId.equals(resolveNodeId(member)));
    }

    private static String resolveNodeId(com.hazelcast.cluster.Member member) {
        String nodeId = member.getAttribute(NodeIdentityProperties.NODE_ID_MEMBER_ATTRIBUTE);
        return nodeId == null || nodeId.isBlank() ? member.getUuid().toString() : nodeId;
    }

    private static boolean isControlCapable(NodeRole role) {
        return role == NodeRole.CONTROL || role == NodeRole.ALL;
    }
}
