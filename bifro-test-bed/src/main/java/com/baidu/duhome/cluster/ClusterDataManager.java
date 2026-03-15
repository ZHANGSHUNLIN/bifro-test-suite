package com.baidu.duhome.cluster;

import com.baidu.duhome.bean.ClusterNodeInfo;
import com.baidu.duhome.bean.CommonResp;
import com.baidu.duhome.bean.NodeInfo;
import com.baidu.duhome.bean.dto.NodeTaskAllocationRequest;
import com.baidu.duhome.bean.vo.NodeTaskAllocationVO;
import com.baidu.duhome.database.pojo.NodeTask;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.cluster.task.DefaultWeightCalculation;
import com.baidu.duhome.cluster.task.NodeWeight;
import com.baidu.duhome.database.repository.NodeTaskRepository;
import com.baidu.duhome.exception.ApiException;
import com.baidu.iot.test.suite.ShareDataAddr;
import com.baidu.iot.test.suite.ShareDataManager;
import com.baidu.iot.test.suite.worker.TaskConfig;
import com.baidu.iot.test.suite.worker.TaskStage;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.baidu.duhome.util.RuntimeUtil.getHostName;
import static com.baidu.duhome.util.RuntimeUtil.getSystemLoadAverage;

/**
 * 集群粒度的数据管理器，提供集群信息获取、节点信息管理等功能。
 */
@Slf4j
@Component
public class ClusterDataManager implements DataManager {

    @Resource
    private ShareDataManager shareDataManager;

    @Resource
    private Vertx vertx;

    @Resource
    private DefaultWeightCalculation defaultWeightCalculation;

    @Resource
    private NodeTaskRepository nodeTaskRepository;

    private final AtomicReference<String> currentNodeIdCache = new AtomicReference<>();

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

    /**
     * 异步获取总任务
     */
    public CompletableFuture<TaskInfoMetadata> getMainTask(String taskId) {
        return shareDataManager.<String, TaskInfoMetadata>map(ShareDataAddr.CLUSTER_TASK_CONFIGS)
                .key(taskId)
                .future();

    }

    public CompletableFuture<List<TaskInfoMetadata>> getAllTask() {
        return shareDataManager.<String, TaskInfoMetadata>map(ShareDataAddr.CLUSTER_TASK_CONFIGS).values();
    }

    /**
     * 异步获取子任务
     */
    public CompletableFuture<Map<String, TaskConfig>> getSubTasks(String taskId) {
        return shareDataManager.<String, Map<String, TaskConfig>>map(ShareDataAddr.NODE_TASK_CONFIGS)
                .key(taskId)
                .future();

    }

    public CompletableFuture<TaskConfig> getSubTasks(String taskId, String nodeId) {
        return shareDataManager.<String, Map<String, TaskConfig>>map(ShareDataAddr.NODE_TASK_CONFIGS)
                .key(taskId)
                .future()
                .thenApply(taskConfigMap ->
                        taskConfigMap.get(nodeId)
                );
    }


    public CompletableFuture<CommonResp> addTask(TaskInfoMetadata taskInfoMetadata) {
        TaskConfig taskConfig = taskInfoMetadata.getTaskConfig();
        String taskId = taskConfig.getTaskId();
        return shareDataManager.<String, TaskInfoMetadata>map(ShareDataAddr.CLUSTER_TASK_CONFIGS)
                .key(taskId)
                .putIfAbsent(taskInfoMetadata)
                .future()
                .thenApply(r -> CommonResp.success());
    }

    public CompletableFuture<TaskConfig> assignTask(String taskId) {
        return shareDataManager.<String, TaskInfoMetadata>map(ShareDataAddr.CLUSTER_TASK_CONFIGS)
                .key(taskId)
                .thenAccept(taskInfoMetadata -> {
                    TaskConfig taskConfig = taskInfoMetadata.getTaskConfig();
                    distributeTasksToNodes(taskConfig);
                }).future().thenApply(TaskInfoMetadata::getTaskConfig);
    }

    public void assignTask(TaskConfig taskConfig) {
        distributeTasksToNodes(taskConfig);
    }

    public CompletableFuture<TaskInfoMetadata> replaceTask(String taskId, TaskInfoMetadata taskInfoMetadata) {
        return shareDataManager.<String, TaskInfoMetadata>map(ShareDataAddr.CLUSTER_TASK_CONFIGS)
                .key(taskId)
                .replace(taskInfoMetadata)
                .future()
                .thenApply(r -> {
                    log.info("replace task: {}", r);
                    return r;
                });
    }


    private void distributeTasksToNodes(TaskConfig mainTaskConfig) {
        String taskId = mainTaskConfig.getTaskId();
        shareDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)
                .entries()
                .thenAccept(entries -> {
                    Map<String, NodeInfo> newHash = new HashMap<>();
                    entries.forEach((k, v) -> {
                        // 过滤掉已下线或心跳包超时的节点
                        if (v.isAlive()) {
                            newHash.put(k, v);
                        }
                    });
                    Map<String, TaskConfig> nodeTaskConfigs = calculateNodeTasks(mainTaskConfig, newHash, 1, 1);
                    List<NodeTask> nodeTasks = new ArrayList<>();
                    nodeTaskConfigs.forEach((k, v) -> {
                        NodeTask nodeTask = new NodeTask();
                        nodeTask.setTaskId(taskId);
                        nodeTask.setNodeId(k);
                        nodeTask.setTaskConfig(v);
                        nodeTasks.add(nodeTask);
                    });
                    nodeTaskRepository.saveAll(nodeTasks);

//                    shareDataManager.getMap(ShareDataAddr.NODE_TASK_CONFIGS).putIfAbsent(taskId, nodeTaskConfigs);
//                    log.info("Distributed task {} to {} nodes", taskId, nodeTaskConfigs.size());
//                }).whenComplete((r, e) -> {
//                    if (e != null) {
//                        log.error("Error when distribute task {} to nodes", taskId, e);
//                    }
//                });
                });
    }


    private void distributeTasksToNodes2(TaskConfig mainTaskConfig, NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        String taskId = mainTaskConfig.getTaskId();
        try {
            Map<String, NodeInfo> nodeInfoMap = shareDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)
                    .entries().get(3, TimeUnit.SECONDS);

            Map<String, NodeInfo> newHash = new HashMap<>();
            nodeInfoMap.forEach((k, v) -> {
                // 过滤掉已下线或心跳包超时的节点
                if (v.isAlive()) {
                    newHash.put(k, v);
                }
            });

            // 根据分配后的数值，创建最终配置
            Map<String, TaskConfig> nodeTaskConfigs = new HashMap<>();
            int currentThingIdOffset = 0;

            List<String> notFoundNodeIdList = new ArrayList<>();
            for (String nodeId : newHash.keySet()) {
                Optional<NodeTaskAllocationRequest.NodeAllocation> first = nodeTaskAllocationRequest.getNodeAllocationList().stream().filter(r -> Objects.equals(r.getNodeId(), nodeId)).findFirst();
                if (first.isPresent()) {
                    NodeTaskAllocationRequest.NodeAllocation nodeAllocation = first.get();
                    int nodeClientCount = nodeAllocation.getAllocatedClientCount();
                    TaskConfig nodeTaskConfig = createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
                    // 设置ThingId起始位置
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

            List<NodeTask> nodeTasks = new ArrayList<>();
            nodeTaskConfigs.forEach((k, v) -> {
                NodeTask nodeTask = new NodeTask();
                nodeTask.setTaskId(taskId);
                nodeTask.setNodeId(k);
                nodeTask.setTaskConfig(v);
                nodeTasks.add(nodeTask);
            });
            nodeTaskRepository.saveAll(nodeTasks);

            //                    shareDataManager.getMap(ShareDataAddr.NODE_TASK_CONFIGS).putIfAbsent(taskId, nodeTaskConfigs);
            //                    log.info("Distributed task {} to {} nodes", taskId, nodeTaskConfigs.size());
            //                }).whenComplete((r, e) -> {
            //                    if (e != null) {
            //                        log.error("Error when distribute task {} to nodes", taskId, e);
            //                    }
            //                });
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new ApiException("查询节点信息超时");
        }
    }

    public void assignCheck(TaskConfig taskConfig, NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        checkClientCount(nodeTaskAllocationRequest);
        distributeTasksToNodes2(taskConfig, nodeTaskAllocationRequest);
    }

    private static void checkClientCount(NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        Integer totalClientCount = nodeTaskAllocationRequest.getTotalClientCount();
        int sumTotalClientCount = nodeTaskAllocationRequest.getNodeAllocationList().stream().mapToInt(NodeTaskAllocationRequest.NodeAllocation::getAllocatedClientCount)
                .sum();
        if (totalClientCount != sumTotalClientCount) {
            throw new ApiException("Total client count is not equal to sum total client count");
        }
    }


    @SneakyThrows
    public CompletableFuture<NodeTaskAllocationVO> calcuTasksToNodes(TaskConfig mainTaskConfig) {
        int totalClientCount = mainTaskConfig.getTotalClientCount();
        return shareDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)
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

    public void regClusterNodeInfo() {

        shareDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)
                .key(getCurrentNodeIdCache())
                .putIfAbsent(() -> {
                    ClusterNodeInfo systemInfo = getSystemInfo();
                    log.debug("add cluster node info: {}", systemInfo);
                    return NodeInfo.builder()
                            .nextPing(System.currentTimeMillis())
                            .clusterNodeInfo(systemInfo).build();
                });

    }

    public void upgradeClusterNodeTaskStage(Map<String, TaskStage> stageMap) {
        String currentNodeId = getCurrentNodeIdCache();
        ShareDataManager.ShareMap<String, NodeInfo> map = shareDataManager.map(ShareDataAddr.CLUSTER_NODE_INFO);
        map.key(currentNodeId)
                .thenAccept((nodeInfo) -> {
                    nodeInfo.setTaskStage(stageMap);
                    map.key(currentNodeId).replace(nodeInfo);
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
        return shareDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)
                .entries();
    }

    public CompletableFuture<Map<Object, Object>> something(String key) {
        return shareDataManager.map(ShareDataAddr.valueOf(key))
                .entries();
    }

    public void upgradeSubTaskStage(TaskConfig taskConfig, TaskStage taskStage) {
        String taskId = taskConfig.getTaskId();

        ShareDataManager.ShareMap<String, Map<String, TaskConfig>> map = shareDataManager.<String, Map<String, TaskConfig>>map(ShareDataAddr.NODE_TASK_CONFIGS);
        map.key(taskId)
                .thenAccept((v) -> {
                    v.values().forEach(r -> r.setTaskWorkStage(taskStage));
                    map.key(taskId).putIfAbsent(v);
                });
    }

    public void upgradeMainTaskStage(String taskId, TaskStage taskStage) {
        ShareDataManager.ShareMap<String, TaskInfoMetadata> map = shareDataManager.<String, TaskInfoMetadata>map(ShareDataAddr.CLUSTER_TASK_CONFIGS);
        map.key(taskId)
                .thenAccept((metadata) -> {
                    TaskConfig taskConfig = metadata.getTaskConfig();
                    taskConfig.setTaskWorkStage(taskStage);
                    map.key(taskId).replace(metadata);
                });

    }
}
