package com.baidu.duhome.cluster;

import com.baidu.duhome.bean.ClusterNodeInfo;
import com.baidu.duhome.bean.NodeInfo;
import com.baidu.duhome.bean.dto.NodeTaskAllocationRequest;
import com.baidu.duhome.bean.vo.NodeTaskAllocationVO;
import com.baidu.duhome.database.pojo.NodeTask;
import com.baidu.duhome.cluster.task.DefaultWeightCalculation;
import com.baidu.duhome.cluster.task.NodeWeight;
import com.baidu.duhome.database.repository.NodeTaskRepository;
import com.baidu.duhome.exception.ApiException;
import com.baidu.iot.test.suite.HazelcastDataManager;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.worker.TaskConfig;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.baidu.duhome.util.RuntimeUtil.getHostName;
import static com.baidu.duhome.util.RuntimeUtil.getSystemLoadAverage;

/**
 * 集群粒度的数据管理器，提供集群信息获取、节点信息管理等功能。
 */
@Slf4j
@Component
public class ClusterDataManager {

    @Resource
    private HazelcastDataManager hazelcastDataManager;

    @Resource
    private Vertx vertx;

    @Resource
    private DefaultWeightCalculation defaultWeightCalculation;

    @Resource
    private NodeTaskRepository nodeTaskRepository;

    @Value("${bifro.nodeName}")
    private String nodeName;

    private final AtomicReference<String> currentNodeIdCache = new AtomicReference<>();

    private IMap<String, NodeInfo> getClusterNodeInfoMap() {
        try {
            Set<HazelcastInstance> instances = Hazelcast.getAllHazelcastInstances();
            if (instances.isEmpty()) {
                throw new IllegalStateException("No Hazelcast instances found");
            }
            HazelcastInstance hazelcast = instances.iterator().next();
            return hazelcast.getMap(HazelcastDataManager.ShareDataAddr.CLUSTER_NODE_INFO.getAddr());
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
            // 更安全的方式获取Hazelcast实例
            Set<HazelcastInstance> instances = Hazelcast.getAllHazelcastInstances();
            if (instances.isEmpty()) {
                throw new IllegalStateException("No Hazelcast instances found");
            }

            HazelcastInstance hazelcast = instances.iterator().next();

            // 获取集群节点列表
            return hazelcast.getCluster().getMembers().stream()
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
            // 获取 Hazelcast 实例
            HazelcastInstance hazelcast = Hazelcast.getAllHazelcastInstances().iterator().next();

            // 获取本地成员（当前节点）
            Member localMember = hazelcast.getCluster().getLocalMember();

            // 获取节点ID
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

        // 如果节点数大于总客户端数，调整逻辑
        if (totalClientCount < nodeCount) {
            log.warn("Total client count {} is less than node count {}, each node will get at most 1 client",
                    totalClientCount, nodeCount);
            Map<String, TaskConfig> nodeTaskConfigs = new HashMap<>();
            int clientIdx = 0;
            for (String nodeId : nodeInfos.keySet()) {
                TaskConfig nodeTaskConfig = createTaskConfig(mainTaskConfig, nodeId, 1);
                nodeTaskConfig.setThingIdStartAt(mainTaskConfig.getThingIdStartAt() + clientIdx);
                nodeTaskConfigs.put(nodeId, nodeTaskConfig);
                if (++clientIdx >= totalClientCount) {
                    break;
                }
            }
            return nodeTaskConfigs;
        }

        // 基于权重分配客户端数量
        Map<String, Integer> allocatedClients = assignedAccordingToWeights(nodeInfos, cpuWeight, memWeight, totalClientCount);

        // 根据分配后的数值，创建最终配置
        Map<String, TaskConfig> nodeTaskConfigs = new HashMap<>();
        int currentThingIdOffset = 0;

        for (String nodeId : nodeInfos.keySet()) {
            int nodeClientCount = allocatedClients.get(nodeId);
            TaskConfig nodeTaskConfig = createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
            // 设置ThingId起始位置
            nodeTaskConfig.setThingIdStartAt(mainTaskConfig.getThingIdStartAt() + currentThingIdOffset);
            currentThingIdOffset += nodeClientCount;

            nodeTaskConfigs.put(nodeId, nodeTaskConfig);
        }

        log.info("Task allocation completed. Total clients: {}, Nodes: {}",
                totalClientCount, nodeCount);
        allocatedClients.forEach((nodeId, count) ->
                log.debug("Node {} allocated {} clients", nodeId, count));

        return nodeTaskConfigs;
    }

    private TaskConfig createTaskConfig(TaskConfig mainTaskConfig, String nodeId, int nodeClientCount) {
        TaskConfig nodeTaskConfig = TaskConfig.newInstance(mainTaskConfig);
        nodeTaskConfig.setTaskId(mainTaskConfig.getTaskId());
        nodeTaskConfig.setNodeId(nodeId);
        nodeTaskConfig.setTotalClientCount(nodeClientCount);
        return nodeTaskConfig;
    }


    private Map<String, Integer> assignedAccordingToWeights(
            Map<String, NodeInfo> nodeInfos, int cpuWeight, int memWeight, int totalClientCount) {
        NodeWeight nodeWeights = defaultWeightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);
        Map<String, Integer> allocatedClients = new HashMap<>();
        Map<String, BigDecimal> weighted = nodeWeights.weight();
        for (Map.Entry<String, BigDecimal> entry : weighted.entrySet()) {
            String nodeId = entry.getKey();
            // 计算该节点应该分配的比例
            int additionalClients = nodeWeights.weightPercentage(nodeId)
                    .multiply(new BigDecimal(totalClientCount)).intValue();
            // 确保不分配负数
            additionalClients = Math.max(additionalClients, 0);
            allocatedClients.put(nodeId, additionalClients);
        }

        // 判断是否有剩余进行最后分配
        int allocatedTotal = allocatedClients.values().stream().mapToInt(Integer::intValue).sum();
        int difference = totalClientCount - allocatedTotal;

        if (difference != 0) {
            // 根据权重调整，权重高的节点优先调整
            List<Map.Entry<String, BigDecimal>> sortedByWeight = weighted.entrySet().stream()
                    .sorted((a, b) ->
                            Double.compare(b.getValue().intValue(), a.getValue().intValue()))
                    .toList();

            for (int i = 0; i < difference; i++) {
                Map.Entry<String, BigDecimal> entry = sortedByWeight.get(i % sortedByWeight.size());
                String nodeId = entry.getKey();
                int current = allocatedClients.get(nodeId);
                // 确保调整后不小于最小值
                int newValue = current + 1;
                allocatedClients.put(nodeId, newValue);
            }
        }
        return allocatedClients;
    }

    private CompletableFuture<Void> distributeTasksToNodes2(String id, TaskConfig mainTaskConfig, NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        return hazelcastDataManager.<String, NodeInfo>map(HazelcastDataManager.ShareDataAddr.CLUSTER_NODE_INFO)
                .entries()
                .thenApplyAsync(nodeInfoMap -> {
                    Map<String, NodeInfo> newHash = new HashMap<>();
                    nodeInfoMap.forEach((k, v) -> {
                        if (v.isAlive()) {
                            newHash.put(k, v);
                        }
                    });

                    Map<String, TaskConfig> nodeTaskConfigs = new HashMap<>();
                    int currentThingIdOffset = 0;

                    List<String> notFoundNodeIdList = new ArrayList<>();
                    for (String nodeId : newHash.keySet()) {
                        Optional<NodeTaskAllocationRequest.NodeAllocation> first = nodeTaskAllocationRequest.getNodeAllocationList().stream().filter(r -> Objects.equals(r.getNodeId(), nodeId)).findFirst();
                        if (first.isPresent()) {
                            NodeTaskAllocationRequest.NodeAllocation nodeAllocation = first.get();
                            int nodeClientCount = nodeAllocation.getAllocatedClientCount();
                            TaskConfig nodeTaskConfig = createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
                            nodeTaskConfig.setThingIdStartAt(mainTaskConfig.getThingIdStartAt() + currentThingIdOffset);
                            currentThingIdOffset += nodeClientCount;
                            nodeTaskConfigs.put(nodeId, nodeTaskConfig);
                        } else {
                            notFoundNodeIdList.add(nodeId);
                        }
                    }
                    if (!notFoundNodeIdList.isEmpty()) {
                        throw new ApiException("Node not found: " + notFoundNodeIdList);
                    }

                    nodeTaskRepository.deleteByTaskId(id).block();

                    List<NodeTask> nodeTasks = new ArrayList<>();
                    nodeTaskConfigs.forEach((k, v) -> {
                        NodeTask nodeTask = new NodeTask();
                        nodeTask.setTaskId(id);
                        nodeTask.setNodeId(k);
                        nodeTask.setTaskConfig(v);
                        nodeTask.setNodeName(nodeName);
                        nodeTasks.add(nodeTask);
                    });
                    nodeTaskRepository.saveAll(nodeTasks).collectList().block();
                    return null;
                });
    }

    public CompletableFuture<Void> assignCheck(String id, TaskConfig taskConfig, NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        checkClientCount(nodeTaskAllocationRequest);
        return distributeTasksToNodes2(id, taskConfig, nodeTaskAllocationRequest);
    }

    private static void checkClientCount(NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        Integer totalClientCount = nodeTaskAllocationRequest.getTotalClientCount();
        int sumTotalClientCount = nodeTaskAllocationRequest.getNodeAllocationList().stream().mapToInt(NodeTaskAllocationRequest.NodeAllocation::getAllocatedClientCount)
                .sum();
        if (totalClientCount != sumTotalClientCount) {
            throw new ApiException("Total client count is not equal to sum total client count");
        }
    }


    public CompletableFuture<NodeTaskAllocationVO> calcuTasksToNodes(TaskConfig mainTaskConfig) {
        int totalClientCount = mainTaskConfig.getTotalClientCount();
        return hazelcastDataManager.<String, NodeInfo>map(HazelcastDataManager.ShareDataAddr.CLUSTER_NODE_INFO)
                .entries()
                .thenApply(entries -> {
                    Map<String, NodeInfo> newHash = new HashMap<>();
                    entries.forEach((k, v) -> {
                        // 过滤掉已下线或心跳包超时的节点
                        if (v.isAlive()) {
                            newHash.put(k, v);
                        }
                    });
                    List<NodeTaskAllocationVO.NodeAllocation> nodeAllocations = assignedAccordingToWeights(newHash, 1, 1, totalClientCount)
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

    /**
     * 使用 Hazelcast Map 直接存储节点信息，绕过 Vert.x AsyncMap 的序列化问题
     */
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


    public void upgradeClusterNodeTaskStage(Map<String, TaskStage> stageMap) {
        String currentNodeId = getCurrentNodeIdCache();
        hazelcastDataManager.<String, NodeInfo>map(HazelcastDataManager.ShareDataAddr.CLUSTER_NODE_INFO)
                .key(currentNodeId)
                .thenAccept((nodeInfo) -> {
                    nodeInfo.setTaskStage(stageMap);
                    hazelcastDataManager.<String, NodeInfo>map(HazelcastDataManager.ShareDataAddr.CLUSTER_NODE_INFO)
                            .key(currentNodeId).replace(nodeInfo);
                });
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
        return hazelcastDataManager.<String, NodeInfo>map(HazelcastDataManager.ShareDataAddr.CLUSTER_NODE_INFO)
                .entries();
    }

    public CompletableFuture<NodeInfo> currentNode() {
        return hazelcastDataManager.<String, NodeInfo>map(HazelcastDataManager.ShareDataAddr.CLUSTER_NODE_INFO)
                .key(getCurrentNodeIdCache())
                .future();
    }

}
