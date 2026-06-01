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

import static org.apache.bifromq.testsuite.app.util.RuntimeUtil.getHostName;
import static org.apache.bifromq.testsuite.app.util.RuntimeUtil.getSystemLoadAverage;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.bean.ClusterNodeInfo;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.apache.bifromq.testsuite.app.bean.dto.NodeTaskAllocationRequest;
import org.apache.bifromq.testsuite.app.bean.vo.NodeTaskAllocationVO;
import org.apache.bifromq.testsuite.app.cluster.shared.HazelcastDataManager;
import org.apache.bifromq.testsuite.app.cluster.shared.ShareDataAddr;
import org.apache.bifromq.testsuite.app.config.LocalPortModeProperties;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.pojo.TaskStateHistory;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskStateHistoryRepository;
import org.apache.bifromq.testsuite.app.eventbus.NodeQueryGateway;
import org.apache.bifromq.testsuite.app.task.config.LocalPortPreflightSpec;
import org.apache.bifromq.testsuite.app.task.config.NodeTaskAllocationPlanner;
import org.apache.bifromq.testsuite.app.task.config.NodeTaskAssignment;
import org.apache.bifromq.testsuite.app.task.runtime.TaskRuntimeStates;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.web.ApiException;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskCommand;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckRequest;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ClusterDataManager {

    private static final long LOCAL_PORT_CAPACITY_CHECK_TIMEOUT_MS = 5000L;

    private final AtomicReference<String> currentNodeIdCache = new AtomicReference<>();
    @Resource
    private HazelcastDataManager hazelcastDataManager;
    @Resource
    private Vertx vertx;
    @Resource
    private HazelcastInstance hazelcastInstance;
    @Resource
    private DefaultWeightCalculation defaultWeightCalculation;
    @Resource
    private NodeTaskRepository nodeTaskRepository;
    @Resource
    private TaskStateHistoryRepository taskStateHistoryRepository;
    @Resource
    private LocalPortModeProperties localPortModeProperties;
    @Resource
    private NodeQueryGateway nodeQueryGateway;
    @Value("${bifro.nodeName}")
    private String nodeName;

    private static void checkClientCount(NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        if (nodeTaskAllocationRequest == null) {
            return;
        }
        Integer totalClientCount = nodeTaskAllocationRequest.getTotalClientCount();
        if (totalClientCount == null) {
            return;
        }
        int sumTotalClientCount = nodeTaskAllocationRequest.getNodeAllocationList().stream()
            .mapToInt(NodeTaskAllocationRequest.NodeAllocation::getAllocatedClientCount)
            .sum();
        if (totalClientCount != sumTotalClientCount) {
            throw new ApiException("Total client count is not equal to sum total client count");
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
            || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank() ? current == null ? "unknown" : current.toString() : message;
    }

    private IMap<String, NodeInfo> getClusterNodeInfoMap() {
        try {
            return hazelcastInstance.getMap(ShareDataAddr.CLUSTER_NODE_INFO.getAddr());
        } catch (Exception e) {
            log.error("Failed to get Hazelcast map", e);
            throw new RuntimeException(e);
        }
    }

    public Set<String> getCurrentNodeIds() {
        if (!vertx.isClustered()) {
            throw new IllegalStateException("Vertx is not clustered");
        }
        try {
            return hazelcastInstance.getCluster().getMembers().stream()
                .map(member -> member.getUuid().toString())
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Failed to get current node IDs", e);
            return Set.of();
        }
    }

    public String getCurrentNodeIdCache() {
        if (!vertx.isClustered()) {
            return "local-node";
        }
        String currentNodeId = currentNodeIdCache.get();
        if (currentNodeId != null) {
            return currentNodeId;
        }

        try {
            Member localMember = hazelcastInstance.getCluster().getLocalMember();
            currentNodeIdCache.set(localMember.getUuid().toString());
            return currentNodeIdCache.get();

        } catch (Exception e) {
            log.error("Failed to get current node ID", e);
            return null;
        }
    }

    public Map<String, TaskConfig> calculateNodeTasks(
        TaskConfig mainTaskConfig, Map<String, NodeInfo> nodeInfos, int cpuWeight, int memWeight) {

        int totalClientCount = mainTaskConfig.getTotalClientCount();
        int nodeCount = nodeInfos.size();

        if (totalClientCount <= 0) {
            throw new TaskManagerException("Total client count must be greater than 0");
        }

        if (totalClientCount < nodeCount) {
            log.warn("Total client count {} is less than node count {}, each node will get at most 1 client",
                totalClientCount, nodeCount);
            return NodeTaskAllocationPlanner.toSingleClientNodeTaskConfigs(
                mainTaskConfig, new ArrayList<>(nodeInfos.keySet()));
        }
        Map<String, Integer> allocatedClients =
            assignedAccordingToWeights(nodeInfos, cpuWeight, memWeight, totalClientCount);

        List<String> orderedNodeIds = new ArrayList<>(nodeInfos.keySet());
        List<NodeTaskAssignment> assignments = orderedNodeIds.stream()
            .map(nodeId -> new NodeTaskAssignment(nodeId, allocatedClients.get(nodeId)))
            .toList();
        Map<String, TaskConfig> nodeTaskConfigs =
            NodeTaskAllocationPlanner.toNodeTaskConfigs(mainTaskConfig, assignments);

        log.info("Task allocation completed. Total clients: {}, Nodes: {}",
            totalClientCount, nodeCount);
        allocatedClients.forEach((nodeId, count) ->
            log.debug("Node {} allocated {} clients", nodeId, count));

        return nodeTaskConfigs;
    }

    private Map<String, Integer> assignedAccordingToWeights(
        Map<String, NodeInfo> nodeInfos, int cpuWeight, int memWeight, int totalClientCount) {
        NodeWeight nodeWeights = defaultWeightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);
        Map<String, Integer> allocatedClients = new HashMap<>();
        Map<String, BigDecimal> weighted = nodeWeights.weight();
        for (Map.Entry<String, BigDecimal> entry : weighted.entrySet()) {
            String nodeId = entry.getKey();

            int additionalClients = nodeWeights.weightPercentage(nodeId)
                .multiply(new BigDecimal(totalClientCount)).intValue();

            additionalClients = Math.max(additionalClients, 0);
            allocatedClients.put(nodeId, additionalClients);
        }
        int allocatedTotal = allocatedClients.values().stream().mapToInt(Integer::intValue).sum();
        int difference = totalClientCount - allocatedTotal;

        if (difference != 0) {

            List<Map.Entry<String, BigDecimal>> sortedByWeight = weighted.entrySet().stream()
                .sorted((a, b) ->
                    Double.compare(b.getValue().intValue(), a.getValue().intValue()))
                .toList();

            for (int i = 0; i < difference; i++) {
                Map.Entry<String, BigDecimal> entry = sortedByWeight.get(i % sortedByWeight.size());
                String nodeId = entry.getKey();
                int current = allocatedClients.get(nodeId);

                int newValue = current + 1;
                allocatedClients.put(nodeId, newValue);
            }
        }
        return allocatedClients;
    }

    private List<NodeTaskAssignment> toAssignments(List<Map.Entry<String, Integer>> allocationEntries) {
        return allocationEntries.stream()
            .map(entry -> new NodeTaskAssignment(entry.getKey(), entry.getValue()))
            .toList();
    }

    private CompletableFuture<Void> distributeTasksToNodes(String id, TaskConfig mainTaskConfig,
                                                           NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        return hazelcastDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)
            .entries()
            .thenComposeAsync(nodeInfoMap -> {
                Map<String, NodeInfo> newHash = new HashMap<>();
                nodeInfoMap.forEach((k, v) -> {
                    if (v.isAlive()) {
                        newHash.put(k, v);
                    }
                });

                Map<String, TaskConfig> nodeTaskConfigs = new HashMap<>();
                List<String> notFoundNodeIdList = new ArrayList<>();
                if (nodeTaskAllocationRequest == null || nodeTaskAllocationRequest.getNodeAllocationList() == null) {
                    log.info("No allocation request, auto-calculating distribution");
                    Map<String, Integer> autoAlloc =
                        assignedAccordingToWeights(newHash, 1, 1, mainTaskConfig.getTotalClientCount());
                    List<Map.Entry<String, Integer>> autoAllocEntries = new ArrayList<>(autoAlloc.entrySet());

                    autoAllocEntries.removeIf(e -> e.getValue() <= 0);
                    nodeTaskConfigs.putAll(NodeTaskAllocationPlanner.toNodeTaskConfigs(
                        mainTaskConfig, toAssignments(autoAllocEntries)));
                } else {
                    List<NodeTaskAllocationRequest.NodeAllocation> validAllocs =
                        nodeTaskAllocationRequest.getNodeAllocationList()
                            .stream().filter(a -> a.getAllocatedClientCount() > 0 && newHash.containsKey(a.getNodeId()))
                            .collect(java.util.stream.Collectors.toList());
                    for (NodeTaskAllocationRequest.NodeAllocation allocation
                        : nodeTaskAllocationRequest.getNodeAllocationList()) {
                        String nodeId = allocation.getNodeId();
                        int nodeClientCount = allocation.getAllocatedClientCount();

                        if (nodeClientCount <= 0) {
                            log.debug("Skipping node {} with 0 allocated clients", nodeId);
                            continue;
                        }
                        if (!newHash.containsKey(nodeId)) {
                            notFoundNodeIdList.add(nodeId);
                        }
                    }
                    if (!notFoundNodeIdList.isEmpty()) {
                        throw new ApiException("Node not found or offline: " + notFoundNodeIdList);
                    }
                    nodeTaskConfigs.putAll(NodeTaskAllocationPlanner.toNodeTaskConfigs(
                        mainTaskConfig, validAllocs.stream()
                            .map(allocation -> new NodeTaskAssignment(
                                allocation.getNodeId(), allocation.getAllocatedClientCount()))
                            .toList()));
                }

                String taskId = mainTaskConfig.getTaskId();
                LocalPortRangeConfig localPortConfig = currentLocalPortModeConfig();

                List<NodeTask> nodeTasks = new ArrayList<>();
                nodeTaskConfigs.forEach((k, v) -> {
                    applyLocalPortModeConfig(v, localPortConfig);
                    NodeTask nodeTask = new NodeTask();
                    nodeTask.setTaskId(taskId);
                    nodeTask.setNodeId(k);
                    nodeTask.setTaskConfig(v);
                    nodeTask.setWorkerTaskCommand(WorkerTaskCommand.fromTaskConfig(v, nodeTask.getPlannedStartAtMs()));
                    NodeInfo nodeInfo = nodeInfoMap.get(k);
                    nodeTask.setNodeName(nodeInfo != null ? nodeInfo.getNodeName() : k);
                    nodeTasks.add(nodeTask);
                });

                CompletableFuture<Void> busyCheck = localPortConfig.isEnabled()
                    ? rejectIfLocalPortModeNodeBusy(taskId, nodeTaskConfigs.keySet())
                    : CompletableFuture.completedFuture(null);
                return busyCheck
                    .thenCompose(ignored -> handleLocalPortCapacityPreflight(taskId, nodeTaskConfigs))
                    .thenCompose(ignored -> nodeTaskRepository.deleteByTaskId(taskId)
                        .thenMany(nodeTaskRepository.saveAll(nodeTasks))
                        .then()
                        .toFuture());
            });
    }

    public CompletableFuture<Void> assignCheck(
        String id, TaskConfig taskConfig, NodeTaskAllocationRequest nodeTaskAllocationRequest) {

        if (nodeTaskAllocationRequest != null) {
            checkClientCount(nodeTaskAllocationRequest);
        }
        return distributeTasksToNodes(id, taskConfig, nodeTaskAllocationRequest);
    }

    public CompletableFuture<Void> prepareAssignedTaskStart(String taskId, long plannedStartAtMs) {
        LocalPortRangeConfig localPortConfig = currentLocalPortModeConfig();
        return nodeTaskRepository.findAllByTaskId(taskId)
            .collectList()
            .flatMap(nodeTasks -> {
                if (nodeTasks.isEmpty()) {
                    return Mono.empty();
                }
                Map<String, TaskConfig> nodeTaskConfigs = new HashMap<>();
                for (NodeTask nodeTask : nodeTasks) {
                    TaskConfig taskConfig = nodeTask.getTaskConfig();
                    if (taskConfig == null) {
                        return Mono.error(new ApiException(
                            "Node task config is missing: taskId=" + taskId + ", nodeId=" + nodeTask.getNodeId()));
                    }
                    applyLocalPortModeConfig(taskConfig, localPortConfig);
                    nodeTaskConfigs.put(nodeTask.getNodeId(), taskConfig);
                }

                CompletableFuture<Void> busyCheck = localPortConfig.isEnabled()
                    ? rejectIfLocalPortModeNodeBusy(taskId, nodeTaskConfigs.keySet())
                    : CompletableFuture.completedFuture(null);
                return Mono.fromFuture(busyCheck
                        .thenCompose(ignored -> handleLocalPortCapacityPreflight(taskId, nodeTaskConfigs)))
                    .thenMany(Flux.fromIterable(nodeTasks))
                    .flatMap(nodeTask -> {
                        TaskRuntimeStates.applyNodePlannedStart(nodeTask, plannedStartAtMs);
                        nodeTask.setWorkerTaskCommand(
                            WorkerTaskCommand.fromTaskConfig(nodeTask.getTaskConfig(), plannedStartAtMs));
                        return nodeTaskRepository.save(nodeTask);
                    })
                    .then();
            })
            .toFuture();
    }

    private void applyLocalPortModeConfig(TaskConfig taskConfig) {
        if (localPortModeProperties == null || taskConfig == null) {
            return;
        }
        applyLocalPortModeConfig(taskConfig, currentLocalPortModeConfig());
    }

    private void applyLocalPortModeConfig(TaskConfig taskConfig, LocalPortRangeConfig config) {
        if (taskConfig == null || config == null) {
            return;
        }
        taskConfig.setLocalPortRangeConfig(config);
        if (!config.isEnabled()) {
            return;
        }
        if (taskConfig.isEnableAutoMultiAddress()) {
            if (taskConfig.getLocalAddresses() == null || taskConfig.getLocalAddresses().isEmpty()) {
                log.info("Local port mode uses auto multi-address discovery, taskId={}, nodeId={}",
                    taskConfig.getTaskId(), taskConfig.getNodeId());
            }
        } else {
            log.info("Local port mode uses primary local address only, taskId={}, nodeId={}",
                taskConfig.getTaskId(), taskConfig.getNodeId());
        }
    }

    private LocalPortRangeConfig currentLocalPortModeConfig() {
        return localPortModeProperties == null ? new LocalPortRangeConfig() : localPortModeProperties.toConfig();
    }

    private CompletableFuture<Void> rejectIfLocalPortModeNodeBusy(String taskId, Collection<String> nodeIds) {
        return Flux.fromIterable(nodeIds)
            .flatMap(nodeId -> nodeTaskRepository.findAllByNodeId(nodeId)
                .filter(existing -> !taskId.equals(existing.getTaskId()))
                .filter(this::isActiveNodeTask)
                .next()
                .map(existing -> "nodeId=" + nodeId + ", runningTaskId=" + existing.getTaskId()))
            .collectList()
            .doOnNext(reasons -> {
                if (!reasons.isEmpty()) {
                    throw new ApiException("Source port preallocation is enabled, so each node can run only one task "
                        + "at a time. Busy nodes: " + reasons
                        + ". Stop the running task or select other nodes before assigning taskId=" + taskId);
                }
            })
            .then()
            .toFuture();
    }

    private boolean isActiveNodeTask(NodeTask nodeTask) {
        if (nodeTask == null) {
            return false;
        }
        return !TaskRuntimeStates.isTerminal(TaskRuntimeStates.nodeStage(nodeTask));
    }

    private CompletableFuture<Void> handleLocalPortCapacityPreflight(String taskId,
                                                                     Map<String, TaskConfig> nodeTaskConfigs) {
        if (nodeTaskConfigs == null || nodeTaskConfigs.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Map<String, LocalPortPreflightSpec> preflightSpecs = nodeTaskConfigs.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> LocalPortPreflightSpec.fromTaskConfig(entry.getKey(), entry.getValue())
            ));
        List<CompletableFuture<LocalPortCapacityCheckResponse>> checks = preflightSpecs.values().stream()
            .map(spec -> requestLocalPortCapacityCheck(taskId, spec))
            .toList();
        CompletableFuture<Void> allChecks = CompletableFuture.allOf(checks.toArray(new CompletableFuture[0]));
        return allChecks.thenApply(ignored -> {
            List<LocalPortCapacityCheckResponse> responses = checks.stream()
                .map(CompletableFuture::join)
                .toList();
            responses.forEach(response -> applyLocalPortPreflightResult(taskId, nodeTaskConfigs, response));
            List<String> errors = responses.stream()
                .filter(response -> response == null || !response.isSuccess())
                .map(this::formatLocalPortCapacityError)
                .toList();
            if (!errors.isEmpty()) {
                throw new ApiException("Task assignment rejected because local source port preflight failed: "
                    + String.join("; ", errors));
            }
            return null;
        });
    }

    private void applyLocalPortPreflightResult(String taskId, Map<String, TaskConfig> nodeTaskConfigs,
                                               LocalPortCapacityCheckResponse response) {
        if (response == null
            || (isEmpty(response.getOccupiedPorts()) && isEmpty(response.getExcludedPorts()))) {
            return;
        }
        TaskConfig taskConfig = nodeTaskConfigs.get(response.getNodeId());
        if (taskConfig == null) {
            return;
        }
        LocalPortRangeConfig config = taskConfig.getLocalPortRangeConfig() == null
            ? new LocalPortRangeConfig()
            : taskConfig.getLocalPortRangeConfig().normalized();
        Set<Integer> excludedPorts = new LinkedHashSet<>(config.getExcludedPorts());
        if (response.getExcludedPorts() != null && !response.getExcludedPorts().isEmpty()) {
            excludedPorts.addAll(response.getExcludedPorts());
        } else {
            response.getOccupiedPorts().stream()
                .map(LocalPortCapacityCheckResponse.OccupiedPort::getPort)
                .forEach(excludedPorts::add);
        }
        config.setExcludedPorts(excludedPorts.stream().sorted().toList());
        taskConfig.setLocalPortRangeConfig(config.normalized());
        recordLocalPortPreflightEvent(taskId, response, taskConfig.getLocalPortRangeConfig());
    }

    private boolean isEmpty(Collection<?> values) {
        return values == null || values.isEmpty();
    }

    private void recordLocalPortPreflightEvent(String taskId, LocalPortCapacityCheckResponse response,
                                               LocalPortRangeConfig config) {
        if (taskStateHistoryRepository == null) {
            return;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventType", "LOCAL_PORT_PREFLIGHT_CONFLICT");
        metadata.put("message", "Local source port preflight found occupied ports; allocator will skip them.");
        metadata.put("portRange", response.getStartPort() + "-" + response.getEndPort());
        metadata.put("assignedClients", response.getAssignedClients());
        metadata.put("capacityAfterExclusion", response.getCapacity());
        metadata.put("reservedFallbackPortsPerAddress", response.getReservedFallbackPortsPerAddress());
        metadata.put("occupiedPortCount", response.getOccupiedPortCount());
        metadata.put("excludedPorts", config.getExcludedPorts());
        metadata.put("occupiedPorts", response.getOccupiedPorts().stream()
            .map(port -> port.getLocalAddress() + ":" + port.getPort() + "(" + port.getState() + ")")
            .toList());
        TaskStateHistory history = TaskStateHistory.builder()
            .taskId(taskId)
            .nodeId(response.getNodeId())
            .timestamp(Instant.now())
            .source("ASSIGNMENT_PREFLIGHT")
            .errorMessage("Local source port conflicts detected and excluded from allocation.")
            .metadata(metadata)
            .eventSeq(-Math.abs(System.nanoTime()))
            .build();
        taskStateHistoryRepository.save(history)
            .doOnError(e -> log.warn("Failed to save local port preflight event, taskId={}, nodeId={}",
                taskId, response.getNodeId(), e))
            .onErrorResume(e -> Mono.empty())
            .block();
    }

    private CompletableFuture<LocalPortCapacityCheckResponse> requestLocalPortCapacityCheck(
        String taskId, LocalPortPreflightSpec spec) {
        String nodeId = spec.nodeId();
        LocalPortRangeConfig portRangeConfig = spec.localPortRangeConfig();
        LocalPortCapacityCheckRequest request = LocalPortCapacityCheckRequest.builder()
            .taskId(taskId)
            .nodeId(nodeId)
            .assignedClients(spec.assignedClients())
            .multiAddressEnabled(spec.multiAddressEnabled())
            .sourcePortPreallocationEnabled(portRangeConfig.isEnabled())
            .startPort(portRangeConfig.getStartPort())
            .endPort(portRangeConfig.getEndPort())
            .configuredLocalAddresses(spec.configuredLocalAddresses())
            .build();
        if (nodeQueryGateway == null) {
            return CompletableFuture.completedFuture(LocalPortCapacityCheckResponse.builder()
                .success(false)
                .taskId(taskId)
                .nodeId(nodeId)
                .assignedClients(spec.assignedClients())
                .multiAddressEnabled(spec.multiAddressEnabled())
                .sourcePortPreallocationEnabled(portRangeConfig.isEnabled())
                .startPort(portRangeConfig.getStartPort())
                .endPort(portRangeConfig.getEndPort())
                .errorMessage("Failed to check local port capacity on nodeId=" + nodeId
                    + ": node query gateway is not available")
                .build());
        }
        return nodeQueryGateway.checkLocalPortCapacity(nodeId, request)
            .exceptionally(e -> LocalPortCapacityCheckResponse.builder()
                .success(false)
                .taskId(taskId)
                .nodeId(nodeId)
                .assignedClients(spec.assignedClients())
                .multiAddressEnabled(spec.multiAddressEnabled())
                .sourcePortPreallocationEnabled(portRangeConfig.isEnabled())
                .errorMessage("Failed to check local port capacity on nodeId=" + nodeId + ": "
                    + rootMessage(e))
                .build());
    }

    private String formatLocalPortCapacityError(LocalPortCapacityCheckResponse response) {
        if (response == null) {
            return "node returned empty local port capacity response";
        }
        if (response.getErrorMessage() != null && !response.getErrorMessage().isBlank()) {
            return response.getErrorMessage();
        }
        return "nodeId=" + response.getNodeId()
            + ", assignedClients=" + response.getAssignedClients()
            + ", capacity=" + response.getCapacity()
            + ", localAddressCount=" + response.getLocalAddressCount()
            + ", multiAddressEnabled=" + response.isMultiAddressEnabled()
            + ", sourcePortPreallocationEnabled=" + response.isSourcePortPreallocationEnabled()
            + ", portRange=" + response.getStartPort() + "-" + response.getEndPort()
            + ", reservedFallbackPortsPerAddress=" + response.getReservedFallbackPortsPerAddress()
            + ", missingCount=" + response.getMissingCount()
            + ", occupiedPortCount=" + response.getOccupiedPortCount()
            + ", occupiedPorts=" + formatOccupiedPorts(response);
    }

    private String formatOccupiedPorts(LocalPortCapacityCheckResponse response) {
        if (response.getOccupiedPorts() == null || response.getOccupiedPorts().isEmpty()) {
            return "[]";
        }
        return response.getOccupiedPorts().stream()
            .map(port -> port.getLocalAddress() + ":" + port.getPort() + "(" + port.getState() + ")")
            .collect(Collectors.joining(", ", "[", "]"));
    }

    public CompletableFuture<NodeTaskAllocationVO> calcuTasksToNodes(TaskConfig mainTaskConfig) {
        int totalClientCount = mainTaskConfig.getTotalClientCount();
        return hazelcastDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)
            .entries()
            .thenApply(entries -> {
                Map<String, NodeInfo> newHash = new HashMap<>();
                entries.forEach((k, v) -> {

                    if (v.isAlive()) {
                        newHash.put(k, v);
                    }
                });
                List<NodeTaskAllocationVO.NodeAllocation> nodeAllocations =
                    assignedAccordingToWeights(newHash, 1, 1, totalClientCount)
                        .entrySet().stream().map(r -> {
                            NodeTaskAllocationVO.NodeAllocation nodeAllocation = new NodeTaskAllocationVO.NodeAllocation();
                            nodeAllocation.setNodeId(r.getKey());
                            nodeAllocation.setAllocatedClientCount(r.getValue());
                            return nodeAllocation;
                        }).toList();
                NodeTaskAllocationVO nodeTaskAllocationCalculationVO = new NodeTaskAllocationVO();
                nodeTaskAllocationCalculationVO.setTotalClientCount(totalClientCount);
                nodeTaskAllocationCalculationVO.setNodeAllocationList(nodeAllocations);
                return nodeTaskAllocationCalculationVO;
            });
    }

    public void regClusterNodeInfoDirect(String nodeName) {
        try {
            IMap<String, NodeInfo> map = getClusterNodeInfoMap();
            ClusterNodeInfo systemInfo = getSystemInfo();
            log.debug("add cluster node info using Hazelcast IMap: {}", systemInfo);
            NodeInfo nodeInfo = NodeInfo.builder()
                .nodeName(nodeName)
                .nextPing(System.currentTimeMillis())
                .clusterNodeInfo(systemInfo).build();
            map.put(getCurrentNodeIdCache(), nodeInfo);
        } catch (Exception e) {
            log.error("Failed to register cluster node info using Hazelcast IMap", e);
        }
    }

    private ClusterNodeInfo getSystemInfo() {
        Runtime runtime = Runtime.getRuntime();

        ClusterNodeInfo info = new ClusterNodeInfo();
        info.setNodeId(getCurrentNodeIdCache());
        info.setHost(getHostName());
        info.setTimestamp(System.currentTimeMillis());

        ClusterNodeInfo.MemoryInfo memory = new ClusterNodeInfo.MemoryInfo();
        memory.setMax(runtime.maxMemory());
        memory.setTotal(runtime.totalMemory());
        memory.setFree(runtime.freeMemory());
        memory.setUsed(memory.getTotal() - memory.getFree());
        info.setMemory(memory);

        ClusterNodeInfo.CpuInfo cpu = new ClusterNodeInfo.CpuInfo();
        cpu.setProcessors(runtime.availableProcessors());
        cpu.setLoadAverage(getSystemLoadAverage());
        info.setCpu(cpu);

        return info;
    }

    public CompletableFuture<Map<String, NodeInfo>> allNodes() {
        return hazelcastDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)
            .entries();
    }

    public CompletableFuture<Map<String, NodeInfo>> allMemberNodes() {
        return allNodes();
    }

    public CompletableFuture<NodeInfo> currentNode() {
        return hazelcastDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)
            .key(getCurrentNodeIdCache())
            .future();
    }

}
