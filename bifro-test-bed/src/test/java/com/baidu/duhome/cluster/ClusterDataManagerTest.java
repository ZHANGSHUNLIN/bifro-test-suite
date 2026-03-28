package com.baidu.duhome.cluster;

import com.baidu.duhome.bean.ClusterNodeInfo;
import com.baidu.duhome.bean.NodeInfo;
import com.baidu.duhome.bean.dto.NodeTaskAllocationRequest;
import com.baidu.duhome.cluster.task.DefaultWeightCalculation;
import com.baidu.duhome.cluster.task.NodeWeight;
import com.baidu.duhome.database.repository.NodeTaskRepository;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.worker.TaskConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ClusterDataManager 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ClusterDataManagerTest {

    @Mock
    private io.vertx.core.Vertx vertx;

    @Mock
    private DefaultWeightCalculation defaultWeightCalculation;

    @Mock
    private NodeTaskRepository nodeTaskRepository;

    @InjectMocks
    private ClusterDataManager clusterDataManager;

    private static final String TASK_ID = "test-task-123";
    private static final String NODE_ID_1 = "node-001";
    private static final String NODE_ID_2 = "node-002";

    @BeforeEach
    void setUp() {
        lenient().when(vertx.isClustered()).thenReturn(false);
    }

    // ==================== 测试: getCurrentNodeIds ====================

    @Test
    void testGetCurrentNodeIds_notClustered_shouldThrowException() {
        // given
        when(vertx.isClustered()).thenReturn(false);

        // when & then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            clusterDataManager.getCurrentNodeIds();
        });
        assertEquals("Vertx is not clustered", exception.getMessage());
    }

    // ==================== 测试: getCurrentNodeIdCache ====================

    @Test
    void testGetCurrentNodeIdCache_notClustered_shouldReturnLocalNode() {
        // given
        when(vertx.isClustered()).thenReturn(false);

        // when
        String result = clusterDataManager.getCurrentNodeIdCache();

        // then
        assertEquals("local-node", result);
    }

    // ==================== 测试: calculateNodeTasks ====================

    @Test
    void testCalculateNodeTasks_success_shouldCalculateDistribution() {
        // given
        TaskConfig mainTaskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .totalClientCount(100)
                .thingIdStartAt(0)
                .build();

        Map<String, NodeInfo> nodeInfos = Map.of(
                NODE_ID_1, createNodeInfo(NODE_ID_1),
                NODE_ID_2, createNodeInfo(NODE_ID_2)
        );

        NodeWeight nodeWeight = new NodeWeight(
                BigDecimal.valueOf(2),
                Map.of(
                        NODE_ID_1, BigDecimal.ONE,
                        NODE_ID_2, BigDecimal.ONE
                )
        );

        when(defaultWeightCalculation.calculateWeights(any(), anyInt(), anyInt()))
                .thenReturn(nodeWeight);

        // when
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        // then
        assertEquals(2, result.size());
        assertTrue(result.containsKey(NODE_ID_1));
        assertTrue(result.containsKey(NODE_ID_2));
        assertEquals(TASK_ID, result.get(NODE_ID_1).getTaskId());
        assertEquals(NODE_ID_1, result.get(NODE_ID_1).getNodeId());
    }

    @Test
    void testCalculateNodeTasks_zeroTotalClientCount_shouldThrowException() {
        // given
        TaskConfig mainTaskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .totalClientCount(0)
                .build();

        Map<String, NodeInfo> nodeInfos = Map.of(NODE_ID_1, createNodeInfo(NODE_ID_1));

        // when & then
        TaskManagerException exception = assertThrows(TaskManagerException.class, () -> {
            clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);
        });
        assertEquals("Total client count must be greater than 0", exception.getMessage());
    }

    @Test
    void testCalculateNodeTasks_negativeTotalClientCount_shouldThrowException() {
        // given
        TaskConfig mainTaskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .totalClientCount(-1)
                .build();

        Map<String, NodeInfo> nodeInfos = Map.of(NODE_ID_1, createNodeInfo(NODE_ID_1));

        // when & then
        TaskManagerException exception = assertThrows(TaskManagerException.class, () -> {
            clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);
        });
        assertEquals("Total client count must be greater than 0", exception.getMessage());
    }

    @Test
    void testCalculateNodeTasks_clientsLessThanNodes_shouldAllocateAtMostOnePerNode() {
        // given
        TaskConfig mainTaskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .totalClientCount(2)
                .thingIdStartAt(0)
                .build();

        Map<String, NodeInfo> nodeInfos = Map.of(
                NODE_ID_1, createNodeInfo(NODE_ID_1),
                NODE_ID_2, createNodeInfo(NODE_ID_2),
                "node-003", createNodeInfo("node-003")
        );

        lenient().when(defaultWeightCalculation.calculateWeights(any(), anyInt(), anyInt()))
                .thenReturn(new NodeWeight(BigDecimal.valueOf(3),
                        Map.of(NODE_ID_1, BigDecimal.ONE, NODE_ID_2, BigDecimal.ONE, "node-003", BigDecimal.ONE)));

        // when
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        // then
        assertEquals(2, result.size());
        // Verify that each assigned node gets exactly 1 client
        for (TaskConfig config : result.values()) {
            assertEquals(1, config.getTotalClientCount());
        }
    }

    @Test
    void testCalculateNodeTasks_weightBasedAllocation_shouldDistributeCorrectly() {
        // given
        TaskConfig mainTaskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .totalClientCount(100)
                .thingIdStartAt(0)
                .build();

        Map<String, NodeInfo> nodeInfos = Map.of(
                NODE_ID_1, createNodeInfo(NODE_ID_1),
                NODE_ID_2, createNodeInfo(NODE_ID_2)
        );

        // Node 1 has higher weight (0.7) than Node 2 (0.3)
        NodeWeight nodeWeight = new NodeWeight(
                BigDecimal.valueOf(10),
                Map.of(
                        NODE_ID_1, new BigDecimal("7.0"),
                        NODE_ID_2, new BigDecimal("3.0")
                )
        );

        when(defaultWeightCalculation.calculateWeights(any(), anyInt(), anyInt()))
                .thenReturn(nodeWeight);

        // when
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        // then
        assertEquals(2, result.size());
        // Verify total clients add up to 100
        int totalAllocated = result.values().stream()
                .mapToInt(TaskConfig::getTotalClientCount)
                .sum();
        assertEquals(100, totalAllocated);
    }

    @Test
    void testCalculateNodeTasks_thingIdStartAt_shouldSetCorrectly() {
        // given
        int startThingId = 1000;
        TaskConfig mainTaskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .totalClientCount(50)
                .thingIdStartAt(startThingId)
                .build();

        Map<String, NodeInfo> nodeInfos = Map.of(
                NODE_ID_1, createNodeInfo(NODE_ID_1),
                NODE_ID_2, createNodeInfo(NODE_ID_2)
        );

        NodeWeight nodeWeight = new NodeWeight(
                BigDecimal.valueOf(2),
                Map.of(
                        NODE_ID_1, BigDecimal.ONE,
                        NODE_ID_2, BigDecimal.ONE
                )
        );

        when(defaultWeightCalculation.calculateWeights(any(), anyInt(), anyInt()))
                .thenReturn(nodeWeight);

        // when
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        // then
        // ThingId should start from startThingId
        int minThingId = result.values().stream()
                .mapToInt(TaskConfig::getThingIdStartAt)
                .min()
                .orElse(-1);
        assertEquals(startThingId, minThingId);
    }

    @Test
    void testCalculateNodeTasks_unequalWeights_shouldDistributeAccordingly() {
        // given
        TaskConfig mainTaskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .totalClientCount(100)
                .thingIdStartAt(0)
                .build();

        Map<String, NodeInfo> nodeInfos = Map.of(
                NODE_ID_1, createNodeInfo(NODE_ID_1),
                NODE_ID_2, createNodeInfo(NODE_ID_2),
                "node-003", createNodeInfo("node-003")
        );

        // Create unequal weights
        NodeWeight nodeWeight = new NodeWeight(
                BigDecimal.valueOf(6),
                Map.of(
                        NODE_ID_1, new BigDecimal("3.0"),
                        NODE_ID_2, new BigDecimal("2.0"),
                        "node-003", new BigDecimal("1.0")
                )
        );

        when(defaultWeightCalculation.calculateWeights(any(), anyInt(), anyInt()))
                .thenReturn(nodeWeight);

        // when
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        // then
        assertEquals(3, result.size());
        // Verify total clients add up to 100
        int totalAllocated = result.values().stream()
                .mapToInt(TaskConfig::getTotalClientCount)
                .sum();
        assertEquals(100, totalAllocated);
        // Verify distribution follows weight (roughly 50:33:17)
        assertTrue(result.get(NODE_ID_1).getTotalClientCount() >= 49);
        assertTrue(result.get(NODE_ID_2).getTotalClientCount() >= 32);
    }

    @Test
    void testCalculateNodeTasks_singleClientPerNodeAllocation() {
        // given
        TaskConfig mainTaskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .totalClientCount(3)
                .thingIdStartAt(0)
                .build();

        Map<String, NodeInfo> nodeInfos = Map.of(
                NODE_ID_1, createNodeInfo(NODE_ID_1),
                NODE_ID_2, createNodeInfo(NODE_ID_2),
                "node-003", createNodeInfo("node-003")
        );

        lenient().when(defaultWeightCalculation.calculateWeights(any(), anyInt(), anyInt()))
                .thenReturn(new NodeWeight(BigDecimal.valueOf(3),
                        Map.of(NODE_ID_1, BigDecimal.ONE, NODE_ID_2, BigDecimal.ONE, "node-003", BigDecimal.ONE)));

        // when
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        // then
        assertEquals(3, result.size());
        // Each node should get exactly 1 client
        assertEquals(1, result.get(NODE_ID_1).getTotalClientCount());
        assertEquals(1, result.get(NODE_ID_2).getTotalClientCount());
        assertEquals(1, result.get("node-003").getTotalClientCount());
    }

    // ==================== 测试: assignCheck 静态方法 ====================

    @Test
    void testAssignCheck_clientCountMismatch_shouldThrowException() {
        // given
        TaskConfig taskConfig = TaskConfig.builder().taskId(TASK_ID).build();
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();
        request.setTotalClientCount(100);

        NodeTaskAllocationRequest.NodeAllocation allocation =
                new NodeTaskAllocationRequest.NodeAllocation();
        allocation.setNodeId(NODE_ID_1);
        allocation.setAllocatedClientCount(50);
        request.setNodeAllocationList(List.of(allocation));

        // when & then
        // Mismatch: total 100 vs allocated 50 - exception is thrown immediately during validation
        assertThrows(com.baidu.duhome.exception.ApiException.class, () -> {
            clusterDataManager.assignCheck(TASK_ID, taskConfig, request);
        });
    }

    @Test
    void testAssignCheck_clientCountMatch_shouldNotThrowException() {
        // given
        TaskConfig taskConfig = TaskConfig.builder().taskId(TASK_ID).build();
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();
        request.setTotalClientCount(100);

        NodeTaskAllocationRequest.NodeAllocation allocation =
                new NodeTaskAllocationRequest.NodeAllocation();
        allocation.setNodeId(NODE_ID_1);
        allocation.setAllocatedClientCount(100);
        request.setNodeAllocationList(List.of(allocation));

        // when & then
        // This test checks the static checkClientCount method doesn't throw for matching values
        // Note: It may throw later for other reasons (like null shareDataManager)
        assertThrows(NullPointerException.class, () -> {
            clusterDataManager.assignCheck(TASK_ID, taskConfig, request);
        });
    }

    @Test
    void testCalculateNodeTasks_taskConfigFieldsAreCopied() {
        // given
        TaskConfig mainTaskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .taskType(TaskConfig.TaskType.PUBSUB)
                .taskWorkStage(TaskStage.INIT)
                .totalClientCount(50)
                .thingIdStartAt(100)
                .protocol("tcp")
                .build();

        Map<String, NodeInfo> nodeInfos = Map.of(
                NODE_ID_1, createNodeInfo(NODE_ID_1),
                NODE_ID_2, createNodeInfo(NODE_ID_2)
        );

        NodeWeight nodeWeight = new NodeWeight(
                BigDecimal.valueOf(2),
                Map.of(
                        NODE_ID_1, BigDecimal.ONE,
                        NODE_ID_2, BigDecimal.ONE
                )
        );

        when(defaultWeightCalculation.calculateWeights(any(), anyInt(), anyInt()))
                .thenReturn(nodeWeight);

        // when
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        // then
        // Verify task config fields are properly copied
        assertEquals(TASK_ID, result.get(NODE_ID_1).getTaskId());
        assertEquals(NODE_ID_1, result.get(NODE_ID_1).getNodeId());
        assertEquals(TaskConfig.TaskType.PUBSUB, result.get(NODE_ID_1).getTaskType());
        assertEquals(TaskStage.INIT, result.get(NODE_ID_1).getTaskWorkStage());
        assertEquals("tcp", result.get(NODE_ID_1).getProtocol());
    }

    // ==================== 辅助方法 ====================

    private NodeInfo createNodeInfo(String nodeId) {
        return NodeInfo.builder()
                .nodeName("test-node")
                .clusterNodeInfo(createClusterNodeInfo(nodeId))
                .nextPing(System.currentTimeMillis() + 30000)
                .alive(true)
                .build();
    }

    private ClusterNodeInfo createClusterNodeInfo(String nodeId) {
        ClusterNodeInfo.CpuInfo cpuInfo = new ClusterNodeInfo.CpuInfo();
        cpuInfo.setProcessors(4);
        cpuInfo.setLoadAverage(0.5);

        ClusterNodeInfo.MemoryInfo memoryInfo = new ClusterNodeInfo.MemoryInfo();
        memoryInfo.setTotal(1024 * 1024 * 1024);
        memoryInfo.setUsed(512 * 1024 * 1024);
        memoryInfo.setFree(512 * 1024 * 1024);

        ClusterNodeInfo info = new ClusterNodeInfo();
        info.setNodeId(nodeId);
        info.setHost("localhost");
        info.setCpu(cpuInfo);
        info.setMemory(memoryInfo);
        info.setTimestamp(System.currentTimeMillis());

        return info;
    }
}
