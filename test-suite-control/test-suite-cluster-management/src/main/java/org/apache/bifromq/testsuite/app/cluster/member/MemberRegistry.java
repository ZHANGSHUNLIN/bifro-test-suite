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

package org.apache.bifromq.testsuite.app.cluster.member;

import static org.apache.bifromq.testsuite.app.util.RuntimeUtil.getHostName;
import static org.apache.bifromq.testsuite.app.util.RuntimeUtil.getSystemLoadAverage;

import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.bean.ClusterNodeInfo;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.apache.bifromq.testsuite.app.cluster.ClusterConfig;
import org.apache.bifromq.testsuite.app.cluster.shared.ShareDataAddr;
import org.apache.bifromq.testsuite.config.node.NodeIdentityProperties;
import org.apache.bifromq.testsuite.config.role.NodeRoleProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MemberRegistry {

    private final ConcurrentHashMap<String, MemberInfo> localCache = new ConcurrentHashMap<>();
    @Resource
    private Vertx vertx;
    @Resource
    private HazelcastInstance hazelcastInstance;
    @Resource
    private ClusterConfig clusterConfig;
    @Resource
    private NodeRoleProperties nodeRoleProperties;
    @Resource
    private NodeIdentityProperties nodeIdentityProperties;

    private volatile String localMemberId;

    public Future<Void> registerLocalMember() {
        long startTime = System.currentTimeMillis();
        HazelcastInstance hazelcast = getHazelcastInstance();
        String memberId = nodeIdentityProperties.getNodeId();
        localMemberId = memberId;

        NodeInfo nodeInfo = collectNodeInfo();
        String mapName = ShareDataAddr.CLUSTER_NODE_INFO.getAddr();

        return Future.fromCompletionStage(
            hazelcast.<String, NodeInfo>getMap(mapName)
                .setAsync(memberId, nodeInfo)
                .thenAccept(v -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.info("Local member registered: id={}, name={}, elapsedMs={}",
                        memberId, nodeIdentityProperties.getNodeId(), elapsedTime);

                    MemberInfo memberInfo = convertFromNodeInfo(nodeInfo, memberId);
                    localCache.put(memberId, memberInfo);
                })
                .exceptionally(ex -> {
                    log.error("Failed to register local member", ex);
                    throw new RuntimeException(ex);
                })
        );
    }

    public Future<Map<String, MemberInfo>> getAllMembers() {
        long startTime = System.currentTimeMillis();
        HazelcastInstance hazelcast = getHazelcastInstance();
        String mapName = ShareDataAddr.CLUSTER_NODE_INFO.getAddr();
        var map = hazelcast.<String, NodeInfo>getMap(mapName);

        Set<String> keys = map.keySet();
        if (keys.isEmpty()) {
            return Future.succeededFuture(new ConcurrentHashMap<>());
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Map<String, MemberInfo> members = new ConcurrentHashMap<>();

        for (String memberId : keys) {

            CompletableFuture<Void> future = map.getAsync(memberId)
                .thenAccept(nodeInfo -> {
                    if (nodeInfo != null) {
                        MemberInfo memberInfo = convertFromNodeInfo(nodeInfo, memberId);
                        members.put(memberId, memberInfo);
                    }
                })
                .toCompletableFuture();
            futures.add(future);
        }

        return Future.fromCompletionStage(
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    localCache.putAll(members);
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.debug("Retrieved all members: count={}, elapsedMs={}", members.size(), elapsedTime);
                    return members;
                })
                .exceptionally(ex -> {
                    log.error("Failed to get all members", ex);
                    throw new RuntimeException(ex);
                })
        );
    }

    public Future<Map<String, MemberInfo>> getAliveMembers(Duration maxAge) {
        return getAllMembers()
            .map(members -> members.entrySet().stream()
                .filter(entry -> entry.getValue().isAlive(maxAge))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    public Future<Optional<MemberInfo>> getMember(String memberId) {
        MemberInfo cached = localCache.get(memberId);
        if (cached != null) {
            return Future.succeededFuture(Optional.of(cached));
        }

        long startTime = System.currentTimeMillis();
        HazelcastInstance hazelcast = getHazelcastInstance();
        String mapName = ShareDataAddr.CLUSTER_NODE_INFO.getAddr();

        return Future.fromCompletionStage(
            hazelcast.<String, NodeInfo>getMap(mapName)
                .getAsync(memberId)
                .thenCompose(nodeInfo -> {
                    Optional<MemberInfo> result;
                    if (nodeInfo != null) {
                        MemberInfo memberInfo = convertFromNodeInfo(nodeInfo, memberId);
                        localCache.put(memberId, memberInfo);
                        result = Optional.of(memberInfo);
                    } else {
                        result = Optional.empty();
                    }
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.debug("Get member: memberId={}, found={}, elapsedMs={}",
                        memberId, result.isPresent(), elapsedTime);
                    return CompletableFuture.completedFuture(result);
                })
                .exceptionally(ex -> {
                    log.error("Failed to get member: {}", memberId, ex);
                    throw new RuntimeException(ex);
                })
        );
    }

    public Future<Void> updateLocalHeartbeat() {
        return updateMemberHeartbeat(getLocalMemberId());
    }

    public Future<Void> updateMemberHeartbeat(String memberId) {
        long startTime = System.currentTimeMillis();
        HazelcastInstance hazelcast = getHazelcastInstance();
        String mapName = ShareDataAddr.CLUSTER_NODE_INFO.getAddr();

        return Future.fromCompletionStage(
            hazelcast.<String, NodeInfo>getMap(mapName)
                .getAsync(memberId)
                .thenCompose(nodeInfo -> {
                    if (nodeInfo != null) {
                        long pingTime = System.currentTimeMillis();
                        nodeInfo.setNextPing(pingTime);
                        nodeInfo.setClusterNodeInfo(collectNodeInfo().getClusterNodeInfo());
                        return hazelcast.<String, NodeInfo>getMap(mapName)
                            .setAsync(memberId, nodeInfo)
                            .thenAccept(v -> {
                                long elapsedTime = System.currentTimeMillis() - startTime;
                                log.debug("Heartbeat updated: memberId={}, pingTime={}, elapsedMs={}",
                                    memberId, pingTime, elapsedTime);

                                MemberInfo memberInfo = convertFromNodeInfo(nodeInfo, memberId);
                                localCache.put(memberId, memberInfo);
                            });
                    } else {
                        log.warn("Heartbeat update skipped: memberId={} not found in cluster map", memberId);
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .exceptionally(ex -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.error("Failed to update heartbeat: memberId={}, elapsedMs={}, error={}",
                        memberId, elapsedTime, ex.getMessage());
                    throw new RuntimeException(ex);
                })
        );
    }

    public Future<Void> unregisterLocalMember() {
        String memberId = getLocalMemberId();
        if (memberId == null) {
            return Future.succeededFuture();
        }
        return removeMember(memberId)
            .onSuccess(v -> log.info("Local member unregistered: id={}", memberId))
            .onFailure(ex -> log.error("Failed to unregister local member: id={}", memberId, ex));
    }

    public Future<Void> removeMember(String memberId) {
        long startTime = System.currentTimeMillis();
        HazelcastInstance hazelcast = getHazelcastInstance();
        String mapName = ShareDataAddr.CLUSTER_NODE_INFO.getAddr();

        return Future.fromCompletionStage(
            hazelcast.<String, NodeInfo>getMap(mapName)
                .removeAsync(memberId)
                .thenAccept(v -> {
                    localCache.remove(memberId);
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    log.info("Member removed from registry: id={}, elapsedMs={}", memberId, elapsedTime);
                })
                .exceptionally(ex -> {
                    log.error("Failed to remove member from registry: id={}", memberId, ex);
                    throw new RuntimeException(ex);
                })
        );
    }

    public java.util.Set<String> getAliveClusterMemberIds() {
        try {
            HazelcastInstance hazelcast = getHazelcastInstance();
            return hazelcast.getCluster().getMembers().stream()
                .map(MemberRegistry::resolveNodeId)
                .collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            log.error("Failed to get alive cluster member IDs", e);
            return java.util.Collections.emptySet();
        }
    }

    public String getLocalMemberId() {
        if (localMemberId != null) {
            return localMemberId;
        }

        localMemberId = nodeIdentityProperties.getNodeId();
        return localMemberId;
    }

    private NodeInfo collectNodeInfo() {
        Runtime runtime = Runtime.getRuntime();

        ClusterNodeInfo.MemoryInfo memory = new ClusterNodeInfo.MemoryInfo();
        memory.setMax(runtime.maxMemory());
        memory.setTotal(runtime.totalMemory());
        memory.setFree(runtime.freeMemory());
        memory.setUsed(memory.getTotal() - memory.getFree());

        ClusterNodeInfo.CpuInfo cpu = new ClusterNodeInfo.CpuInfo();
        cpu.setProcessors(runtime.availableProcessors());
        cpu.setLoadAverage(getSystemLoadAverage());

        ClusterNodeInfo systemInfo = new ClusterNodeInfo();
        systemInfo.setNodeId(nodeIdentityProperties.getNodeId());
        systemInfo.setHost(getHostName());
        systemInfo.setMemory(memory);
        systemInfo.setCpu(cpu);
        systemInfo.setTimestamp(System.currentTimeMillis());

        return NodeInfo.builder()
            .nodeName(nodeIdentityProperties.getNodeId())
            .role(nodeRoleProperties.getNodeRole())
            .clusterNodeInfo(systemInfo)
            .nextPing(System.currentTimeMillis())
            .alive(true)
            .build();
    }

    private MemberInfo convertFromNodeInfo(NodeInfo nodeInfo, String memberId) {
        return MemberInfo.builder()
            .id(memberId)
            .name(nodeInfo.getNodeName())
            .host(nodeInfo.getClusterNodeInfo() != null ? nodeInfo.getClusterNodeInfo().getHost() : null)
            .role(nodeInfo.getRole())
            .systemInfo(nodeInfo.getClusterNodeInfo())
            .lastHeartbeat(nodeInfo.getNextPing() != null ? nodeInfo.getNextPing() : 0)
            .registeredAt(Instant.now())
            .build();
    }

    private static String resolveNodeId(com.hazelcast.cluster.Member member) {
        String nodeId = member.getAttribute(NodeIdentityProperties.NODE_ID_MEMBER_ATTRIBUTE);
        return nodeId == null || nodeId.isBlank() ? member.getUuid().toString() : nodeId;
    }

    private HazelcastInstance getHazelcastInstance() {
        if (hazelcastInstance == null) {
            throw new IllegalStateException("Hazelcast instance is not available");
        }
        return hazelcastInstance;
    }
}
