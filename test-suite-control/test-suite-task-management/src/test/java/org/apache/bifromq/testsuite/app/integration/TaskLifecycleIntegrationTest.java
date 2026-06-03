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

package org.apache.bifromq.testsuite.app.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.apache.bifromq.testsuite.app.bean.dto.BrokerEntry;
import org.apache.bifromq.testsuite.app.bean.dto.NodeTaskAllocationRequest;
import org.apache.bifromq.testsuite.app.bean.dto.TaskRequest;
import org.apache.bifromq.testsuite.app.cluster.core.ClusterDataManager;
import org.apache.bifromq.testsuite.app.cluster.shared.HazelcastDataManager;
import org.apache.bifromq.testsuite.app.cluster.shared.ShareDataAddr;
import org.apache.bifromq.testsuite.app.task.TaskManager;
import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.app.database.pojo.MqttGroup;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.pojo.TaskStateHistory;
import org.apache.bifromq.testsuite.app.database.repository.MqttBrokerRepository;
import org.apache.bifromq.testsuite.app.database.repository.MqttGroupRepository;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskStateHistoryRepository;
import org.apache.bifromq.testsuite.app.database.service.TaskInfoMetadataService;
import org.apache.bifromq.testsuite.app.local.LocalTaskCoordinator;
import org.apache.bifromq.testsuite.app.local.TaskStateEventHandler;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.worker.TaskBroker;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.pojo.TaskStateChangeEvent;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TaskLifecycleIntegrationTest {

    
    private static final String TASK_ID = "TestTask01";
    private static final String NODE_ID = "node-001";
    private static final String NODE_NAME = "test-node";
    private static final List<TaskBroker> TEST_BROKERS = List.of(
        TaskBroker.builder().host("localhost").port(1883).build()
    );

    
    private final ConcurrentHashMap<String, TaskInfoMetadata> taskMetadataStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<NodeTask>> nodeTaskStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TaskStateHistory>> stateHistoryStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MqttBroker> brokerStore = new ConcurrentHashMap<>();

    
    @Mock
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Mock
    private NodeTaskRepository nodeTaskRepository;

    @Mock
    private TaskStateHistoryRepository taskStateHistoryRepository;

    @Mock
    private MqttBrokerRepository mqttBrokerRepository;

    @Mock
    private MqttGroupRepository mqttGroupRepository;

    @Mock
    private TaskInfoMetadataService taskInfoMetadataService;

    @Mock
    private ClusterDataManager clusterDataManager;

    @Mock
    private Vertx vertx;

    @Mock
    private EventBus eventBus;

    @Mock
    private HazelcastDataManager hazelcastDataManager;

    
    @InjectMocks
    private TaskManager taskManager;

    @InjectMocks
    private TaskStateEventHandler taskStateEventHandler;

    @InjectMocks
    private LocalTaskCoordinator localTaskCoordinator;

    @BeforeEach
    void setUp() {
        
        taskMetadataStore.clear();
        nodeTaskStore.clear();
        stateHistoryStore.clear();
        brokerStore.clear();

        
        MqttBroker broker = MqttBroker.builder()
            .id("broker-1")
            .brokerId("broker-1")
            .name("test-broker")
            .host("localhost")
            .port(1883)
            .build();
        brokerStore.put("broker-1", broker);

        
        setupRepositoryMocks();

        
        setupVertxMocks();

        
        lenient().when(clusterDataManager.getCurrentNodeIdCache()).thenReturn(NODE_ID);
        lenient().when(clusterDataManager.assignCheck(anyString(), any(TaskConfig.class), any()))
            .thenReturn(CompletableFuture.completedFuture(null));
        setupHazelcastMocks();
    }

    private void setupRepositoryMocks() {
        
        lenient().when(taskInfoMetadataRepository.findById(anyString()))
            .thenAnswer(inv -> Mono.justOrEmpty(taskMetadataStore.get(inv.getArgument(0))));

        lenient().when(taskInfoMetadataRepository.save(any(TaskInfoMetadata.class)))
            .thenAnswer(inv -> {
                TaskInfoMetadata metadata = inv.getArgument(0);
                taskMetadataStore.put(metadata.getTaskId(), metadata);
                return Mono.just(metadata);
            });

        lenient().when(taskInfoMetadataRepository.findByTaskName(anyString()))
            .thenReturn(Mono.empty());

        lenient().when(taskInfoMetadataRepository.updateTaskConfigById(anyString(), any(TaskConfig.class)))
            .thenAnswer(inv -> {
                String taskId = inv.getArgument(0);
                TaskConfig config = inv.getArgument(1);
                TaskInfoMetadata metadata = taskMetadataStore.get(taskId);
                if (metadata != null) {
                    metadata.setTaskConfig(config);
                }
                return Mono.empty();
            });

        lenient().when(taskInfoMetadataRepository.updateStartTimeById(anyString(), any()))
            .thenReturn(Mono.empty());

        lenient().when(taskInfoMetadataRepository.updateEndTimeById(anyString(), any()))
            .thenReturn(Mono.empty());

        lenient().when(taskInfoMetadataRepository.updateStageById(anyString(), anyString(), any(Instant.class)))
            .thenAnswer(inv -> {
                String taskId = inv.getArgument(0);
                String stage = inv.getArgument(1);
                TaskInfoMetadata metadata = taskMetadataStore.get(taskId);
                if (metadata != null) {
                    metadata.setCurrentStage(TaskStage.valueOf(stage));
                    metadata.getTaskConfig().setTaskWorkStage(TaskStage.valueOf(stage));
                    metadata.setStageUpdatedAt(inv.getArgument(2));
                }
                return Mono.empty();
            });

        lenient().when(taskInfoMetadataService.insertTaskInfoMetadata(any(TaskInfoMetadata.class)))
            .thenAnswer(inv -> {
                TaskInfoMetadata metadata = inv.getArgument(0);
                taskMetadataStore.put(metadata.getTaskId(), metadata);
                return Mono.just(metadata);
            });

        
        lenient().when(nodeTaskRepository.findAllByTaskId(anyString()))
            .thenAnswer(inv -> {
                List<NodeTask> list = nodeTaskStore.getOrDefault(inv.getArgument(0), List.of());
                return Flux.fromIterable(list);
            });

        lenient().when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString()))
            .thenAnswer(inv -> {
                String taskId = inv.getArgument(0);
                String nodeId = inv.getArgument(1);
                List<NodeTask> list = nodeTaskStore.getOrDefault(taskId, List.of());
                return list.stream()
                    .filter(nt -> nt.getNodeId().equals(nodeId))
                    .findFirst()
                    .map(Mono::just)
                    .orElse(Mono.empty());
            });

        lenient().when(nodeTaskRepository.save(any(NodeTask.class)))
            .thenAnswer(inv -> {
                NodeTask nodeTask = inv.getArgument(0);
                nodeTaskStore.computeIfAbsent(nodeTask.getTaskId(), k -> new ArrayList<>())
                    .add(nodeTask);
                return Mono.just(nodeTask);
            });

        lenient().when(nodeTaskRepository.findAllByNodeId(anyString()))
            .thenReturn(Flux.empty());

        
        lenient().when(mqttBrokerRepository.findAllById(anyIterable()))
            .thenAnswer(inv -> {
                Iterable<String> ids = inv.getArgument(0);
                List<MqttBroker> result = new ArrayList<>();
                for (String id : ids) {
                    if (brokerStore.containsKey(id)) {
                        result.add(brokerStore.get(id));
                    }
                }
                return Flux.fromIterable(result);
            });

        lenient().when(mqttBrokerRepository.findByBrokerId(anyString()))
            .thenAnswer(inv -> {
                String brokerId = inv.getArgument(0);
                return reactor.core.publisher.Mono.justOrEmpty(brokerStore.get(brokerId));
            });

        lenient().when(mqttGroupRepository.findById(anyString()))
            .thenAnswer(inv -> Mono.just(MqttGroup.builder()
                .id(inv.getArgument(0))
                .type("BROKER")
                .name("integration-test")
                .build()));

        
        lenient().when(taskStateHistoryRepository.save(any(TaskStateHistory.class)))
            .thenAnswer(inv -> {
                TaskStateHistory history = inv.getArgument(0);
                stateHistoryStore.computeIfAbsent(history.getTaskId(), k -> new ArrayList<>())
                    .add(history);
                return Mono.just(history);
            });
    }

    @SuppressWarnings("unchecked")
    private void setupVertxMocks() {
        lenient().when(vertx.eventBus()).thenReturn(eventBus);

        
        lenient().when(eventBus.consumer(anyString(), any(Handler.class)))
            .thenAnswer(inv -> mock(MessageConsumer.class));

        
        lenient().when(vertx.executeBlocking(any(java.util.concurrent.Callable.class)))
            .thenAnswer(inv -> {
                java.util.concurrent.Callable<Object> callable = inv.getArgument(0);
                try {
                    callable.call();
                    return io.vertx.core.Future.succeededFuture();
                } catch (Exception e) {
                    return io.vertx.core.Future.failedFuture(e);
                }
            });
    }

    @SuppressWarnings("unchecked")
    private void setupHazelcastMocks() {
        HazelcastDataManager.IMapWrapper mockWrapper = mock(HazelcastDataManager.IMapWrapper.class);
        HazelcastDataManager.KeyRef mockKeyRef = mock(HazelcastDataManager.KeyRef.class);

        lenient().when(hazelcastDataManager.map(any(ShareDataAddr.class))).thenReturn(mockWrapper);
        lenient().when(mockWrapper.key(anyString())).thenReturn(mockKeyRef);
        lenient().when(mockKeyRef.atomicCompute(any())).thenReturn(mockKeyRef);
        lenient().when(mockKeyRef.thenAccept(any())).thenReturn(mockKeyRef);
    }

    

    @Test
    void testConnTaskLifecycle_createAssignAndAggregate_shouldTransitionToShutdown() {
        
        TaskRequest request = createConnTaskRequest();
        TaskInfoMetadata createdTask = taskManager.addTask(request).block();
        assertThat(createdTask).isNotNull();
        assertThat(createdTask.getTaskId()).isNotNull();
        String taskId = createdTask.getTaskId();

        
        assertThat(createdTask.getTaskConfig().getTaskWorkStage()).isEqualTo(TaskStage.INIT);

        
        NodeTaskAllocationRequest allocationRequest = new NodeTaskAllocationRequest();
        NodeTaskAllocationRequest.NodeAllocation allocation = new NodeTaskAllocationRequest.NodeAllocation();
        allocation.setNodeId(NODE_ID);
        allocation.setAllocatedClientCount(100);
        allocationRequest.setNodeAllocationList(List.of(allocation));

        
        TaskConfig nodeConfig = copyTaskConfig(createdTask.getTaskConfig());
        nodeConfig.setNodeId(NODE_ID);
        nodeConfig.setTotalClientCount(100);
        nodeConfig.setTaskWorkStage(TaskStage.INIT);

        NodeTask nodeTask = NodeTask.builder()
            .taskId(taskId)
            .nodeId(NODE_ID)
            .nodeName(NODE_NAME)
            .taskConfig(nodeConfig)
            .currentStage(TaskStage.INIT)
            .build();
        nodeTaskStore.put(taskId, new ArrayList<>(List.of(nodeTask)));

        var assignResult = taskManager.assignTask(taskId, allocationRequest).block();
        assertThat(assignResult).isNotNull();
        assertThat(assignResult.isSuccess()).isTrue();

        
        TaskStateChangeEvent event = TaskStateChangeEvent.builder()
            .taskId(taskId)
            .fromStage(TaskStage.SHUTTING)
            .toStage(TaskStage.SHUTDOWN)
            .triggerEvent(TaskEvent.STOP)
            .timestamp(Instant.now())
            .nodeId(NODE_ID)
            .nodeName(NODE_NAME)
            .eventSeq(1)
            .build();

        invokeHandleStateChange(event);

        
        List<TaskStateHistory> histories = stateHistoryStore.get(taskId);
        assertThat(histories).isNotNull();
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getToStage()).isEqualTo(TaskStage.SHUTDOWN);

        
        NodeTask updatedNodeTask = nodeTaskRepository.findByTaskIdAndNodeId(taskId, NODE_ID).block();
        assertThat(updatedNodeTask).isNotNull();
        assertThat(updatedNodeTask.getCurrentStage()).isEqualTo(TaskStage.SHUTDOWN);

        
        TaskInfoMetadata updatedTask = taskInfoMetadataRepository.findById(taskId).block();
        assertThat(updatedTask).isNotNull();
        assertThat(updatedTask.getCurrentStage()).isEqualTo(TaskStage.SHUTDOWN);
    }

    

    @Test
    void testPubSubTaskLifecycle_createAssignAndAggregate_shouldTransitionToStopped() {
        
        TaskRequest request = createPubSubTaskRequest();
        TaskInfoMetadata createdTask = taskManager.addTask(request).block();
        assertThat(createdTask).isNotNull();
        String taskId = createdTask.getTaskId();

        assertThat(createdTask.getTaskConfig().getTaskWorkStage()).isEqualTo(TaskStage.INIT);

        
        String nodeId2 = "node-002";
        TaskConfig nodeConfig1 = copyTaskConfig(createdTask.getTaskConfig());
        nodeConfig1.setNodeId(NODE_ID);
        nodeConfig1.setTotalClientCount(50);
        nodeConfig1.setTaskWorkStage(TaskStage.INIT);

        TaskConfig nodeConfig2 = copyTaskConfig(createdTask.getTaskConfig());
        nodeConfig2.setNodeId(nodeId2);
        nodeConfig2.setTotalClientCount(50);
        nodeConfig2.setTaskWorkStage(TaskStage.INIT);

        NodeTask nodeTask1 = NodeTask.builder()
            .taskId(taskId)
            .nodeId(NODE_ID)
            .nodeName(NODE_NAME)
            .taskConfig(nodeConfig1)
            .currentStage(TaskStage.INIT)
            .build();

        NodeTask nodeTask2 = NodeTask.builder()
            .taskId(taskId)
            .nodeId(nodeId2)
            .nodeName("test-node-2")
            .taskConfig(nodeConfig2)
            .currentStage(TaskStage.INIT)
            .build();

        nodeTaskStore.put(taskId, new ArrayList<>(List.of(nodeTask1, nodeTask2)));

        
        TaskStateChangeEvent event1 = TaskStateChangeEvent.builder()
            .taskId(taskId)
            .fromStage(TaskStage.SHUTTING)
            .toStage(TaskStage.SHUTDOWN)
            .triggerEvent(TaskEvent.SHUTDOWN)
            .timestamp(Instant.now())
            .nodeId(NODE_ID)
            .eventSeq(1)
            .build();
        invokeHandleStateChange(event1);

        
        TaskStateChangeEvent event2 = TaskStateChangeEvent.builder()
            .taskId(taskId)
            .fromStage(TaskStage.SHUTTING)
            .toStage(TaskStage.STOPPED)
            .triggerEvent(TaskEvent.STOP)
            .timestamp(Instant.now())
            .nodeId(nodeId2)
            .eventSeq(2)
            .build();
        invokeHandleStateChange(event2);

        
        TaskInfoMetadata updatedTask = taskInfoMetadataRepository.findById(taskId).block();
        assertThat(updatedTask).isNotNull();
        assertThat(updatedTask.getCurrentStage()).isEqualTo(TaskStage.STOPPED);
    }

    

    @Test
    void testAggregateStage_allNodesShutdown_shouldReturnShutdown() {
        
        String taskId = "aggregate-test-1";
        List<NodeTask> nodeTasks = List.of(
            createNodeTask(taskId, NODE_ID, TaskStage.SHUTDOWN),
            createNodeTask(taskId, "node-002", TaskStage.SHUTDOWN)
        );

        TaskStage result = invokeAggregateStage(nodeTasks);
        assertThat(result).isEqualTo(TaskStage.SHUTDOWN);
    }

    @Test
    void testAggregateStage_mixedTerminalStates_shouldReturnStopped() {
        String taskId = "aggregate-test-2";
        List<NodeTask> nodeTasks = List.of(
            createNodeTask(taskId, NODE_ID, TaskStage.SHUTDOWN),
            createNodeTask(taskId, "node-002", TaskStage.STOPPED)
        );

        TaskStage result = invokeAggregateStage(nodeTasks);
        assertThat(result).isEqualTo(TaskStage.STOPPED);
    }

    @Test
    void testAggregateStage_oneNodeOngoing_shouldReturnOngoing() {
        String taskId = "aggregate-test-3";
        List<NodeTask> nodeTasks = List.of(
            createNodeTask(taskId, NODE_ID, TaskStage.ONGOING),
            createNodeTask(taskId, "node-002", TaskStage.SHUTDOWN)
        );

        TaskStage result = invokeAggregateStage(nodeTasks);
        assertThat(result).isEqualTo(TaskStage.ONGOING);
    }

    @Test
    void testAggregateStage_oneNodeShutting_shouldReturnShuttingBeforeOngoing() {
        String taskId = "aggregate-test-shutting";
        List<NodeTask> nodeTasks = List.of(
            createNodeTask(taskId, NODE_ID, TaskStage.SHUTTING),
            createNodeTask(taskId, "node-002", TaskStage.ONGOING)
        );

        TaskStage result = invokeAggregateStage(nodeTasks);
        assertThat(result).isEqualTo(TaskStage.SHUTTING);
    }

    @Test
    void testAggregateStage_oneNodeFailed_shouldReturnFailed() {
        String taskId = "aggregate-test-4";
        List<NodeTask> nodeTasks = List.of(
            createNodeTask(taskId, NODE_ID, TaskStage.FAILED),
            createNodeTask(taskId, "node-002", TaskStage.SHUTDOWN)
        );

        TaskStage result = invokeAggregateStage(nodeTasks);
        assertThat(result).isEqualTo(TaskStage.FAILED);
    }

    

    @Test
    void testDuplicateStateEvent_shouldBeIgnored() {
        
        TaskRequest request = createConnTaskRequest();
        TaskInfoMetadata createdTask = taskManager.addTask(request).block();
        String taskId = createdTask.getTaskId();

        TaskConfig nodeConfig = copyTaskConfig(createdTask.getTaskConfig());
        nodeConfig.setNodeId(NODE_ID);
        nodeConfig.setTaskWorkStage(TaskStage.INIT);

        NodeTask nodeTask = NodeTask.builder()
            .taskId(taskId)
            .nodeId(NODE_ID)
            .taskConfig(nodeConfig)
            .currentStage(TaskStage.INIT)
            .build();
        nodeTaskStore.put(taskId, new ArrayList<>(List.of(nodeTask)));

        
        TaskStateChangeEvent event = TaskStateChangeEvent.builder()
            .taskId(taskId)
            .fromStage(TaskStage.INIT)
            .toStage(TaskStage.STARTING)
            .triggerEvent(TaskEvent.START_TASK)
            .timestamp(Instant.now())
            .nodeId(NODE_ID)
            .eventSeq(5)
            .build();

        invokeHandleStateChange(event);
        invokeHandleStateChange(event); 

        
        List<TaskStateHistory> histories = stateHistoryStore.get(taskId);
        assertThat(histories).hasSize(1);
    }

    @Test
    void testAssignTask_alreadyAssigned_shouldAllowReassign() {
        
        TaskRequest request = createConnTaskRequest();
        TaskInfoMetadata createdTask = taskManager.addTask(request).block();
        String taskId = createdTask.getTaskId();

        
        createdTask.getTaskConfig().setTaskWorkStage(TaskStage.ASSIGNED);
        taskMetadataStore.put(taskId, createdTask);

        NodeTaskAllocationRequest allocationRequest = new NodeTaskAllocationRequest();
        allocationRequest.setNodeAllocationList(List.of());

        
        var result = taskManager.assignTask(taskId, allocationRequest).block();
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        verify(clusterDataManager).assignCheck(anyString(), any(TaskConfig.class), any());
    }

    

    private TaskRequest createConnTaskRequest() {
        TaskRequest request = new TaskRequest();
        request.setTaskName("CONN_Integration_Test");
        request.setTaskType(TaskConfig.TaskType.CONN);
        request.setTemplate(TaskTemplate.CONN_STANDARD.name());
        request.setTotalClientCount(100);
        request.setStressDurationInSec(10);
        request.setGroup("integration-test");
        request.setBrokers(List.of(createBrokerEntry()));
        return request;
    }

    private TaskRequest createPubSubTaskRequest() {
        TaskRequest request = new TaskRequest();
        request.setTaskName("PUBSUB_Integration_Test");
        request.setTaskType(TaskConfig.TaskType.PUBSUB);
        request.setTemplate(TaskTemplate.PUBSUB_STANDARD.name());
        request.setTotalClientCount(100);
        request.setStressDurationInSec(10);
        request.setTopic("test/topic");
        request.setGroup("integration-test");
        request.setBrokers(List.of(createBrokerEntry()));
        return request;
    }

    private BrokerEntry createBrokerEntry() {
        BrokerEntry entry = new BrokerEntry();
        entry.setBrokerId("broker-1");
        entry.setHost("localhost");
        entry.setPort(1883);
        return entry;
    }

    
    private TaskConfig copyTaskConfig(TaskConfig source) {
        TaskConfig copy = new TaskConfig();
        copy.setTaskWorkStage(source.getTaskWorkStage());
        copy.setTemplate(source.getTemplate());
        copy.setTaskId(source.getTaskId());
        copy.setNodeId(source.getNodeId());
        copy.setTaskType(source.getTaskType());
        copy.setProtocol(source.getProtocol());
        copy.setBrokers(source.getBrokers());
        copy.setTotalClientCount(source.getTotalClientCount());
        copy.setStressDurationInSec(source.getStressDurationInSec());
        copy.setTopic(source.getTopic());
        return copy;
    }

    private NodeTask createNodeTask(String taskId, String nodeId, TaskStage stage) {
        return NodeTask.builder()
            .taskId(taskId)
            .nodeId(nodeId)
            .taskConfig(TaskConfig.builder()
                .taskId(taskId)
                .nodeId(nodeId)
                .taskWorkStage(stage)
                .build())
            .currentStage(stage)
            .build();
    }

    
    private void invokeHandleStateChange(TaskStateChangeEvent event) {
        try {
            Method method =
                TaskStateEventHandler.class.getDeclaredMethod("handleStateChange", TaskStateChangeEvent.class);
            method.setAccessible(true);
            method.invoke(taskStateEventHandler, event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke handleStateChange", e);
        }
    }

    
    private TaskStage invokeAggregateStage(List<NodeTask> nodeTasks) {
        try {
            Method method = TaskStateEventHandler.class.getDeclaredMethod("aggregateStage", List.class);
            method.setAccessible(true);
            return (TaskStage) method.invoke(taskStateEventHandler, nodeTasks);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke aggregateStage", e);
        }
    }
}
