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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.eventbus.EventBus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.app.bean.ClusterNodeInfo;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.apache.bifromq.testsuite.app.bean.dto.NodeTaskAllocationRequest;
import org.apache.bifromq.testsuite.app.cluster.core.ClusterDataManager;
import org.apache.bifromq.testsuite.app.cluster.core.DefaultWeightCalculation;
import org.apache.bifromq.testsuite.app.cluster.core.NodeWeight;
import org.apache.bifromq.testsuite.app.cluster.core.TaskManagerException;
import org.apache.bifromq.testsuite.app.cluster.shared.HazelcastDataManager;
import org.apache.bifromq.testsuite.app.cluster.shared.ShareDataAddr;
import org.apache.bifromq.testsuite.app.config.LocalPortModeProperties;
import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.apache.bifromq.testsuite.config.role.NodeRoleProperties;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.pojo.TaskStateHistory;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskStateHistoryRepository;
import org.apache.bifromq.testsuite.app.eventbus.NodeQueryGateway;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.qps.ProfileQpsSpec;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckRequest;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ClusterDataManagerTest {

    private static final String TASK_ID = "test-task-123";
    private static final String NODE_ID_1 = "node-001";
    private static final String NODE_ID_2 = "node-002";
    @Mock
    private io.vertx.core.Vertx vertx;
    @Mock
    private EventBus eventBus;
    @Mock
    private DefaultWeightCalculation defaultWeightCalculation;
    @Mock
    private NodeTaskRepository nodeTaskRepository;
    @Mock
    private TaskStateHistoryRepository taskStateHistoryRepository;
    @Mock
    private HazelcastDataManager hazelcastDataManager;
    @Mock
    private LocalPortModeProperties localPortModeProperties;
    @Mock
    private NodeQueryGateway nodeQueryGateway;
    @Mock
    private NodeRoleProperties nodeRoleProperties;
    @InjectMocks
    private ClusterDataManager clusterDataManager;

    @BeforeEach
    void setUp() {
        lenient().when(vertx.isClustered()).thenReturn(false);
        lenient().when(vertx.eventBus()).thenReturn(eventBus);
        lenient().when(localPortModeProperties.toConfig()).thenReturn(new LocalPortRangeConfig());
        lenient().when(nodeRoleProperties.getNodeRole()).thenReturn(NodeRole.ALL);
    }

    

    @Test
    void testGetCurrentNodeIds_notClustered_shouldThrowException() {
        
        when(vertx.isClustered()).thenReturn(false);

        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            clusterDataManager.getCurrentNodeIds();
        });
        assertEquals("Vertx is not clustered", exception.getMessage());
    }

    

    @Test
    void testGetCurrentNodeIdCache_notClustered_shouldReturnLocalNode() {
        
        when(vertx.isClustered()).thenReturn(false);

        
        String result = clusterDataManager.getCurrentNodeIdCache();

        
        assertEquals("local-node", result);
    }

    

    @Test
    void testCalculateNodeTasks_success_shouldCalculateDistribution() {
        
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

        
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        
        assertEquals(2, result.size());
        assertTrue(result.containsKey(NODE_ID_1));
        assertTrue(result.containsKey(NODE_ID_2));
        assertEquals(TASK_ID, result.get(NODE_ID_1).getTaskId());
        assertEquals(NODE_ID_1, result.get(NODE_ID_1).getNodeId());
    }

    @Test
    void testCalculateNodeTasks_zeroTotalClientCount_shouldThrowException() {
        
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .totalClientCount(0)
            .build();

        Map<String, NodeInfo> nodeInfos = Map.of(NODE_ID_1, createNodeInfo(NODE_ID_1));

        
        TaskManagerException exception = assertThrows(TaskManagerException.class, () -> {
            clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);
        });
        assertEquals("Total client count must be greater than 0", exception.getMessage());
    }

    @Test
    void testCalculateNodeTasks_negativeTotalClientCount_shouldThrowException() {
        
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .totalClientCount(-1)
            .build();

        Map<String, NodeInfo> nodeInfos = Map.of(NODE_ID_1, createNodeInfo(NODE_ID_1));

        
        TaskManagerException exception = assertThrows(TaskManagerException.class, () -> {
            clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);
        });
        assertEquals("Total client count must be greater than 0", exception.getMessage());
    }

    @Test
    void testCalculateNodeTasks_clientsLessThanNodes_shouldAllocateAtMostOnePerNode() {
        
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

        
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        
        assertEquals(2, result.size());
        
        for (TaskConfig config : result.values()) {
            assertEquals(1, config.getTotalClientCount());
        }
    }

    @Test
    void testCalculateNodeTasks_weightBasedAllocation_shouldDistributeCorrectly() {
        
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
            BigDecimal.valueOf(10),
            Map.of(
                NODE_ID_1, new BigDecimal("7.0"),
                NODE_ID_2, new BigDecimal("3.0")
            )
        );

        when(defaultWeightCalculation.calculateWeights(any(), anyInt(), anyInt()))
            .thenReturn(nodeWeight);

        
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        
        assertEquals(2, result.size());
        
        int totalAllocated = result.values().stream()
            .mapToInt(TaskConfig::getTotalClientCount)
            .sum();
        assertEquals(100, totalAllocated);
    }

    @Test
    void testCalculateNodeTasks_thingIdStartAt_shouldSetCorrectly() {
        
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

        
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        
        
        int minThingId = result.values().stream()
            .mapToInt(TaskConfig::getThingIdStartAt)
            .min()
            .orElse(-1);
        assertEquals(startThingId, minThingId);
    }

    @Test
    void testCalculateNodeTasks_unequalWeights_shouldDistributeAccordingly() {
        
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

        
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        
        assertEquals(3, result.size());
        
        int totalAllocated = result.values().stream()
            .mapToInt(TaskConfig::getTotalClientCount)
            .sum();
        assertEquals(100, totalAllocated);
        
        assertTrue(result.get(NODE_ID_1).getTotalClientCount() >= 49);
        assertTrue(result.get(NODE_ID_2).getTotalClientCount() >= 32);
    }

    @Test
    void testCalculateNodeTasks_singleClientPerNodeAllocation() {
        
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

        
        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        
        assertEquals(3, result.size());
        
        assertEquals(1, result.get(NODE_ID_1).getTotalClientCount());
        assertEquals(1, result.get(NODE_ID_2).getTotalClientCount());
        assertEquals(1, result.get("node-003").getTotalClientCount());
    }

    @Test
    void testCalculateNodeTasks_dynamicPublishProfile_shouldSplitConservatively() {
        TaskConfig.ProfileConfig profileConfig = TaskConfig.ProfileConfig.builder()
            .dataPoints(List.of(new long[] {0, 9}, new long[] {1000, 15}))
            .totalDurationMs(1000)
            .endBehavior(ProfileQpsSpec.EndBehavior.HOLD)
            .build();
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .totalClientCount(3)
            .qpsMode(TaskConfig.QpsMode.DYNAMIC)
            .profileConfig(profileConfig)
            .build();
        Map<String, NodeInfo> nodeInfos = Map.of(
            NODE_ID_1, createNodeInfo(NODE_ID_1),
            NODE_ID_2, createNodeInfo(NODE_ID_2),
            "node-003", createNodeInfo("node-003")
        );
        when(defaultWeightCalculation.calculateWeights(any(), anyInt(), anyInt()))
            .thenReturn(new NodeWeight(BigDecimal.valueOf(3),
                Map.of(NODE_ID_1, BigDecimal.ONE, NODE_ID_2, BigDecimal.ONE, "node-003", BigDecimal.ONE)));

        Map<String, TaskConfig> result = clusterDataManager.calculateNodeTasks(mainTaskConfig, nodeInfos, 1, 1);

        for (int pointIndex = 0; pointIndex < 2; pointIndex++) {
            int finalPointIndex = pointIndex;
            long sum = result.values().stream()
                .map(TaskConfig::getPublishProfileDataPoints)
                .mapToLong(points -> points.get(finalPointIndex)[1])
                .sum();
            assertEquals(profileConfig.getDataPoints().get(pointIndex)[1], sum);
        }
    }

    

    @Test
    void testAssignCheck_clientCountMismatch_shouldThrowException() {
        
        TaskConfig taskConfig = TaskConfig.builder().taskId(TASK_ID).build();
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();
        request.setTotalClientCount(100);

        NodeTaskAllocationRequest.NodeAllocation allocation =
            new NodeTaskAllocationRequest.NodeAllocation();
        allocation.setNodeId(NODE_ID_1);
        allocation.setAllocatedClientCount(50);
        request.setNodeAllocationList(List.of(allocation));

        
        
        assertThrows(org.apache.bifromq.testsuite.web.ApiException.class, () -> {
            clusterDataManager.assignCheck(TASK_ID, taskConfig, request);
        });
    }

    @Test
    void testAssignCheck_clientCountMatch_shouldNotThrowException() {
        
        TaskConfig taskConfig = TaskConfig.builder().taskId(TASK_ID).build();
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();
        request.setTotalClientCount(100);

        NodeTaskAllocationRequest.NodeAllocation allocation =
            new NodeTaskAllocationRequest.NodeAllocation();
        allocation.setNodeId(NODE_ID_1);
        allocation.setAllocatedClientCount(100);
        request.setNodeAllocationList(List.of(allocation));

        
        
        when(hazelcastDataManager.map(any(ShareDataAddr.class)))
            .thenThrow(new IllegalStateException("No Hazelcast instances found"));

        
        assertThrows(IllegalStateException.class, () -> {
            clusterDataManager.assignCheck(TASK_ID, taskConfig, request);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAssignCheck_autoAllocation_shouldUseOnlySchedulableNodes() {
        HazelcastDataManager.IMapWrapper<String, NodeInfo> nodeMap = org.mockito.Mockito.mock(
            HazelcastDataManager.IMapWrapper.class);
        when(hazelcastDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)).thenReturn(nodeMap);
        when(nodeMap.entries()).thenReturn(CompletableFuture.completedFuture(Map.of(
            NODE_ID_1, createNodeInfo(NODE_ID_1, NodeRole.WORKER),
            NODE_ID_2, createNodeInfo(NODE_ID_2, NodeRole.CONTROL)
        )));
        when(defaultWeightCalculation.calculateWeights(any(), anyInt(), anyInt()))
            .thenReturn(new NodeWeight(BigDecimal.ONE, Map.of(NODE_ID_1, BigDecimal.ONE)));
        when(nodeTaskRepository.deleteByTaskId(TASK_ID)).thenReturn(Mono.empty());
        when(nodeTaskRepository.saveAll(any(Iterable.class)))
            .thenAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));
        mockLocalPortCapacityResponse(LocalPortCapacityCheckResponse.builder()
            .success(true)
            .taskId(TASK_ID)
            .nodeId(NODE_ID_1)
            .assignedClients(10)
            .capacity(65536)
            .localAddressCount(1)
            .sourcePortPreallocationEnabled(false)
            .build());

        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .totalClientCount(10)
            .build();

        clusterDataManager.assignCheck(TASK_ID, taskConfig, null).join();

        ArgumentCaptor<Iterable<NodeTask>> nodeTasks = ArgumentCaptor.forClass(Iterable.class);
        verify(nodeTaskRepository).saveAll(nodeTasks.capture());
        List<NodeTask> savedNodeTasks = new java.util.ArrayList<>();
        nodeTasks.getValue().forEach(savedNodeTasks::add);
        assertThat(savedNodeTasks).hasSize(1);
        assertThat(savedNodeTasks.get(0).getNodeId()).isEqualTo(NODE_ID_1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAssignCheck_manualAllocationToControlNode_shouldReject() {
        HazelcastDataManager.IMapWrapper<String, NodeInfo> nodeMap = org.mockito.Mockito.mock(
            HazelcastDataManager.IMapWrapper.class);
        when(hazelcastDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)).thenReturn(nodeMap);
        when(nodeMap.entries()).thenReturn(CompletableFuture.completedFuture(
            Map.of(NODE_ID_1, createNodeInfo(NODE_ID_1, NodeRole.CONTROL))));

        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .totalClientCount(10)
            .build();
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();
        request.setTotalClientCount(10);
        NodeTaskAllocationRequest.NodeAllocation allocation = new NodeTaskAllocationRequest.NodeAllocation();
        allocation.setNodeId(NODE_ID_1);
        allocation.setAllocatedClientCount(10);
        request.setNodeAllocationList(List.of(allocation));

        java.util.concurrent.CompletionException exception = assertThrows(
            java.util.concurrent.CompletionException.class,
            () -> clusterDataManager.assignCheck(TASK_ID, taskConfig, request).join());
        assertThat(exception.getCause().getMessage())
            .contains("Node is not schedulable")
            .contains("role=CONTROL");
    }

    @Test
    void testAssignCheck_localPortModeEnabledWithoutLocalAddresses_shouldContinueToAssignment() {
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .localAddresses(List.of())
            .build();
        when(hazelcastDataManager.map(any(ShareDataAddr.class)))
            .thenThrow(new IllegalStateException("No Hazelcast instances found"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            clusterDataManager.assignCheck(TASK_ID, taskConfig, null);
        });

        assertThat(exception.getMessage()).contains("No Hazelcast instances found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAssignCheck_localPortModeEnabledNoBusyNode_shouldNotThrowNullMapperException() {
        LocalPortRangeConfig rangeConfig = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(65535)
            .build();
        when(localPortModeProperties.toConfig()).thenReturn(rangeConfig);

        HazelcastDataManager.IMapWrapper<String, NodeInfo> nodeMap = org.mockito.Mockito.mock(
            HazelcastDataManager.IMapWrapper.class);
        when(hazelcastDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)).thenReturn(nodeMap);
        when(nodeMap.entries()).thenReturn(CompletableFuture.completedFuture(
            Map.of(NODE_ID_1, createNodeInfo(NODE_ID_1))));
        when(nodeTaskRepository.findAllByNodeId(NODE_ID_1)).thenReturn(Flux.empty());
        when(nodeTaskRepository.deleteByTaskId(TASK_ID)).thenReturn(Mono.empty());
        when(nodeTaskRepository.saveAll(any(Iterable.class)))
            .thenAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));
        mockLocalPortCapacityResponse(LocalPortCapacityCheckResponse.builder()
            .success(true)
            .taskId(TASK_ID)
            .nodeId(NODE_ID_1)
            .assignedClients(100)
            .capacity(65536)
            .localAddressCount(1)
            .sourcePortPreallocationEnabled(true)
            .startPort(10000)
            .endPort(65535)
            .build());

        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .totalClientCount(100)
            .localAddresses(List.of())
            .build();
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();
        request.setTotalClientCount(100);
        NodeTaskAllocationRequest.NodeAllocation allocation = new NodeTaskAllocationRequest.NodeAllocation();
        allocation.setNodeId(NODE_ID_1);
        allocation.setAllocatedClientCount(100);
        request.setNodeAllocationList(List.of(allocation));

        clusterDataManager.assignCheck(TASK_ID, taskConfig, request).join();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAssignCheck_shouldSaveWorkerTaskCommandSnapshot() {
        HazelcastDataManager.IMapWrapper<String, NodeInfo> nodeMap = org.mockito.Mockito.mock(
            HazelcastDataManager.IMapWrapper.class);
        when(hazelcastDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)).thenReturn(nodeMap);
        when(nodeMap.entries()).thenReturn(CompletableFuture.completedFuture(
            Map.of(NODE_ID_1, createNodeInfo(NODE_ID_1))));
        when(nodeTaskRepository.deleteByTaskId(TASK_ID)).thenReturn(Mono.empty());
        when(nodeTaskRepository.saveAll(any(Iterable.class)))
            .thenAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));
        mockLocalPortCapacityResponse(LocalPortCapacityCheckResponse.builder()
            .success(true)
            .taskId(TASK_ID)
            .nodeId(NODE_ID_1)
            .assignedClients(12)
            .capacity(65536)
            .localAddressCount(1)
            .sourcePortPreallocationEnabled(false)
            .build());

        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(org.apache.bifromq.testsuite.TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(12)
            .brokers(List.of(org.apache.bifromq.testsuite.worker.TaskBroker.builder()
                .host("localhost")
                .port(1883)
                .build()))
            .build();
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();
        request.setTotalClientCount(12);
        NodeTaskAllocationRequest.NodeAllocation allocation = new NodeTaskAllocationRequest.NodeAllocation();
        allocation.setNodeId(NODE_ID_1);
        allocation.setAllocatedClientCount(12);
        request.setNodeAllocationList(List.of(allocation));

        clusterDataManager.assignCheck(TASK_ID, taskConfig, request).join();

        ArgumentCaptor<Iterable<NodeTask>> nodeTasks = ArgumentCaptor.forClass(Iterable.class);
        verify(nodeTaskRepository).saveAll(nodeTasks.capture());
        NodeTask savedNodeTask = nodeTasks.getValue().iterator().next();
        assertThat(savedNodeTask.getWorkerTaskCommand()).isNotNull();
        assertThat(savedNodeTask.getWorkerTaskCommand().workerTaskSpec()).isNotNull();
        assertThat(savedNodeTask.getWorkerTaskCommand().workerTaskSpec().getTaskId()).isEqualTo(TASK_ID);
        assertThat(savedNodeTask.getWorkerTaskCommand().workerTaskSpec().getTotalClientCount()).isEqualTo(12);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAssignCheck_localPortCapacityInsufficient_shouldIncludeNodeCapacityReason() {
        LocalPortRangeConfig rangeConfig = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(10001)
            .build();
        when(localPortModeProperties.toConfig()).thenReturn(rangeConfig);

        HazelcastDataManager.IMapWrapper<String, NodeInfo> nodeMap = org.mockito.Mockito.mock(
            HazelcastDataManager.IMapWrapper.class);
        when(hazelcastDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)).thenReturn(nodeMap);
        when(nodeMap.entries()).thenReturn(CompletableFuture.completedFuture(
            Map.of(NODE_ID_1, createNodeInfo(NODE_ID_1))));
        when(nodeTaskRepository.findAllByNodeId(NODE_ID_1)).thenReturn(Flux.empty());
        mockLocalPortCapacityResponse(LocalPortCapacityCheckResponse.builder()
            .success(false)
            .errorMessage("Source port capacity is insufficient: nodeId=" + NODE_ID_1
                + ", assignedClients=3, capacity=2, localAddressCount=1, missingCount=1")
            .taskId(TASK_ID)
            .nodeId(NODE_ID_1)
            .assignedClients(3)
            .capacity(2)
            .localAddressCount(1)
            .sourcePortPreallocationEnabled(true)
            .startPort(10000)
            .endPort(10001)
            .missingCount(1)
            .build());

        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .totalClientCount(3)
            .localAddresses(List.of())
            .build();
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();
        request.setTotalClientCount(3);
        NodeTaskAllocationRequest.NodeAllocation allocation = new NodeTaskAllocationRequest.NodeAllocation();
        allocation.setNodeId(NODE_ID_1);
        allocation.setAllocatedClientCount(3);
        request.setNodeAllocationList(List.of(allocation));

        java.util.concurrent.CompletionException exception = assertThrows(
            java.util.concurrent.CompletionException.class,
            () -> clusterDataManager.assignCheck(TASK_ID, taskConfig, request).join());
        assertThat(exception.getCause().getMessage())
            .contains("Task assignment rejected because local source port preflight failed")
            .contains("nodeId=" + NODE_ID_1)
            .contains("assignedClients=3")
            .contains("capacity=2")
            .contains("missingCount=1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAssignCheck_localPortOccupied_shouldExcludePortsAndRecordHistory() {
        LocalPortRangeConfig rangeConfig = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(10001)
            .build();
        when(localPortModeProperties.toConfig()).thenReturn(rangeConfig);

        HazelcastDataManager.IMapWrapper<String, NodeInfo> nodeMap = org.mockito.Mockito.mock(
            HazelcastDataManager.IMapWrapper.class);
        when(hazelcastDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)).thenReturn(nodeMap);
        when(nodeMap.entries()).thenReturn(CompletableFuture.completedFuture(
            Map.of(NODE_ID_1, createNodeInfo(NODE_ID_1))));
        when(nodeTaskRepository.findAllByNodeId(NODE_ID_1)).thenReturn(Flux.empty());
        when(nodeTaskRepository.deleteByTaskId(TASK_ID)).thenReturn(Mono.empty());
        when(nodeTaskRepository.saveAll(any(Iterable.class)))
            .thenAnswer(invocation -> Flux.fromIterable(invocation.getArgument(0)));
        when(taskStateHistoryRepository.save(any(TaskStateHistory.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        mockLocalPortCapacityResponse(LocalPortCapacityCheckResponse.builder()
            .success(true)
            .taskId(TASK_ID)
            .nodeId(NODE_ID_1)
            .assignedClients(1)
            .capacity(1)
            .localAddressCount(1)
            .sourcePortPreallocationEnabled(true)
            .startPort(10000)
            .endPort(10001)
            .occupiedPortCount(1)
            .excludedPorts(List.of(10000))
            .occupiedPorts(List.of(LocalPortCapacityCheckResponse.OccupiedPort.builder()
                .localAddress("127.0.0.1")
                .port(10000)
                .state("LISTEN")
                .build()))
            .build());

        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .totalClientCount(1)
            .localAddresses(List.of())
            .build();
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();
        request.setTotalClientCount(1);
        NodeTaskAllocationRequest.NodeAllocation allocation = new NodeTaskAllocationRequest.NodeAllocation();
        allocation.setNodeId(NODE_ID_1);
        allocation.setAllocatedClientCount(1);
        request.setNodeAllocationList(List.of(allocation));

        clusterDataManager.assignCheck(TASK_ID, taskConfig, request).join();

        ArgumentCaptor<Iterable<NodeTask>> nodeTasks = ArgumentCaptor.forClass(Iterable.class);
        verify(nodeTaskRepository).saveAll(nodeTasks.capture());
        NodeTask savedNodeTask = nodeTasks.getValue().iterator().next();
        assertThat(savedNodeTask.getTaskConfig().getLocalPortRangeConfig().getExcludedPorts())
            .containsExactly(10000);

        ArgumentCaptor<TaskStateHistory> history = ArgumentCaptor.forClass(TaskStateHistory.class);
        verify(taskStateHistoryRepository).save(history.capture());
        assertThat(history.getValue().getSource()).isEqualTo("ASSIGNMENT_PREFLIGHT");
        assertThat(history.getValue().getMetadata()).containsEntry("eventType", "LOCAL_PORT_PREFLIGHT_CONFLICT");
        assertThat(history.getValue().getMetadata()).containsEntry("excludedPorts", List.of(10000));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAssignCheck_localPortModeEnabledBusyNode_shouldIncludeDetailedReason() {
        LocalPortRangeConfig rangeConfig = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(65535)
            .build();
        when(localPortModeProperties.toConfig()).thenReturn(rangeConfig);

        HazelcastDataManager.IMapWrapper<String, NodeInfo> nodeMap = org.mockito.Mockito.mock(
            HazelcastDataManager.IMapWrapper.class);
        when(hazelcastDataManager.<String, NodeInfo>map(ShareDataAddr.CLUSTER_NODE_INFO)).thenReturn(nodeMap);
        when(nodeMap.entries()).thenReturn(CompletableFuture.completedFuture(
            Map.of(NODE_ID_1, createNodeInfo(NODE_ID_1))));

        NodeTask existing = NodeTask.builder()
            .taskId("running-task")
            .nodeId(NODE_ID_1)
            .currentStage(TaskStage.ONGOING)
            .build();
        when(nodeTaskRepository.findAllByNodeId(NODE_ID_1)).thenReturn(Flux.just(existing));

        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .totalClientCount(100)
            .localAddresses(List.of())
            .build();
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();
        request.setTotalClientCount(100);
        NodeTaskAllocationRequest.NodeAllocation allocation = new NodeTaskAllocationRequest.NodeAllocation();
        allocation.setNodeId(NODE_ID_1);
        allocation.setAllocatedClientCount(100);
        request.setNodeAllocationList(List.of(allocation));

        java.util.concurrent.CompletionException exception = assertThrows(
            java.util.concurrent.CompletionException.class,
            () -> clusterDataManager.assignCheck(TASK_ID, taskConfig, request).join());
        assertThat(exception.getCause().getMessage())
            .contains("Source port preallocation is enabled")
            .contains("nodeId=" + NODE_ID_1)
            .contains("runningTaskId=running-task")
            .contains("Stop the running task or select other nodes");
    }

    @Test
    void testPrepareAssignedTaskStart_localPortOccupied_shouldPreflightAndSaveExcludedPorts() {
        LocalPortRangeConfig rangeConfig = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(10001)
            .build();
        when(localPortModeProperties.toConfig()).thenReturn(rangeConfig);
        when(nodeTaskRepository.findAllByNodeId(NODE_ID_1)).thenReturn(Flux.empty());
        when(taskStateHistoryRepository.save(any(TaskStateHistory.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        mockLocalPortCapacityResponse(LocalPortCapacityCheckResponse.builder()
            .success(true)
            .taskId(TASK_ID)
            .nodeId(NODE_ID_1)
            .assignedClients(1)
            .capacity(1)
            .localAddressCount(1)
            .sourcePortPreallocationEnabled(true)
            .startPort(10000)
            .endPort(10001)
            .occupiedPortCount(1)
            .excludedPorts(List.of(10000))
            .occupiedPorts(List.of(LocalPortCapacityCheckResponse.OccupiedPort.builder()
                .localAddress("127.0.0.1")
                .port(10000)
                .state("LISTEN")
                .build()))
            .build());

        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID_1)
            .taskConfig(TaskConfig.builder()
                .taskId(TASK_ID)
                .nodeId(NODE_ID_1)
                .totalClientCount(1)
                .localAddresses(List.of())
                .build())
            .build();
        when(nodeTaskRepository.findAllByTaskId(TASK_ID)).thenReturn(Flux.just(nodeTask));
        when(nodeTaskRepository.save(any(NodeTask.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        clusterDataManager.prepareAssignedTaskStart(TASK_ID, 123456L).join();

        ArgumentCaptor<NodeTask> savedNodeTask = ArgumentCaptor.forClass(NodeTask.class);
        verify(nodeTaskRepository).save(savedNodeTask.capture());
        assertThat(savedNodeTask.getValue().getPlannedStartAtMs()).isEqualTo(123456L);
        assertThat(savedNodeTask.getValue().getWorkerTaskCommand()).isNotNull();
        assertThat(savedNodeTask.getValue().getTaskConfig().getLocalPortRangeConfig().getExcludedPorts())
            .containsExactly(10000);

        ArgumentCaptor<TaskStateHistory> history = ArgumentCaptor.forClass(TaskStateHistory.class);
        verify(taskStateHistoryRepository).save(history.capture());
        assertThat(history.getValue().getSource()).isEqualTo("ASSIGNMENT_PREFLIGHT");
        assertThat(history.getValue().getMetadata()).containsEntry("eventType", "LOCAL_PORT_PREFLIGHT_CONFLICT");
    }

    

    private NodeInfo createNodeInfo(String nodeId) {
        return createNodeInfo(nodeId, NodeRole.WORKER);
    }

    private NodeInfo createNodeInfo(String nodeId, NodeRole nodeRole) {
        return NodeInfo.builder()
            .nodeName("test-node")
            .role(nodeRole)
            .clusterNodeInfo(createClusterNodeInfo(nodeId))
            .build();
    }

    @SuppressWarnings("unchecked")
    private void mockLocalPortCapacityResponse(LocalPortCapacityCheckResponse response) {
        when(nodeQueryGateway.checkLocalPortCapacity(any(), any(LocalPortCapacityCheckRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(response));
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
