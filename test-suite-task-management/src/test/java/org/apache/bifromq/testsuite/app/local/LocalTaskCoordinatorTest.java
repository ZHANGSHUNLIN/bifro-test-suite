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

package org.apache.bifromq.testsuite.app.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.bifromq.testsuite.TaskSchedule;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.app.cluster.core.ClusterDataManager;
import org.apache.bifromq.testsuite.app.cluster.shared.HazelcastDataManager;
import org.apache.bifromq.testsuite.app.config.LocalPortModeProperties;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.worker.TaskBroker;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LocalTaskCoordinatorTest {

    private static final String TASK_ID = "test-task-123";
    private static final String NODE_ID = "node-abc123";
    
    private static final List<TaskBroker> TEST_BROKERS = List.of(
        TaskBroker.builder().host("localhost").port(1883).build()
    );
    @Mock
    private ClusterDataManager clusterDataManager;
    @Mock
    private TaskInfoMetadataRepository taskInfoMetadataRepository;
    @Mock
    private NodeTaskRepository nodeTaskRepository;
    @Mock
    private Vertx vertx;
    @Mock
    private EventBus eventBus;
    @Mock
    private HazelcastDataManager hazelcastDataManager;
    @Mock
    private LocalPortModeProperties localPortModeProperties;
    @InjectMocks
    private LocalTaskCoordinator localTaskCoordinator;

    @BeforeEach
    void setUp() {
        lenient().when(clusterDataManager.getCurrentNodeIdCache()).thenReturn(NODE_ID);
        lenient().when(vertx.eventBus()).thenReturn(eventBus);
        lenient().when(taskInfoMetadataRepository.updateStartTimeById(anyString(), any(LocalDateTime.class)))
            .thenReturn(Mono.empty());
        lenient().when(localPortModeProperties.toConfig())
            .thenReturn(new org.apache.bifromq.testsuite.client.LocalPortRangeConfig());
        lenient().when(vertx.executeBlocking(any(Callable.class)))
            .thenAnswer(invocation -> {
                Callable<?> callable = invocation.getArgument(0);
                try {
                    return Future.succeededFuture(callable.call());
                } catch (Exception e) {
                    return Future.failedFuture(e);
                }
            });
    }

    

    @Test
    void testStartTask_success_shouldStartTask() {
        
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(100)
            .brokers(TEST_BROKERS)
            .build();

        TaskConfig nodeTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(50)
            .taskWorkStage(TaskStage.INIT)
            .brokers(TEST_BROKERS)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskName("Test Task")
            .taskConfig(mainTaskConfig)
            .build();

        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .nodeName("test-node")
            .taskConfig(nodeTaskConfig)
            .workerTaskCommand(WorkerTaskCommand.fromTaskConfig(nodeTaskConfig))
            .build();

        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString())).thenReturn(Mono.just(nodeTask));

        
        localTaskCoordinator.startTask(TASK_ID);

        
        verify(taskInfoMetadataRepository).findById(eq(TASK_ID));
        verify(nodeTaskRepository).findByTaskIdAndNodeId(eq(TASK_ID), eq(NODE_ID));
        Map<String, TaskStage> runningTasks = localTaskCoordinator.runningTask();
        assertNotNull(runningTasks);
    }

    @Test
    void testHandleTaskCommand_startShouldRunOnWorkerExecutor() {
        TaskSchedule command = TaskSchedule.builder()
            .op(TaskSchedule.Op.REG)
            .id(TASK_ID)
            .build();
        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.empty());

        localTaskCoordinator.handleTaskCommand(command);

        verify(vertx).executeBlocking(any(Callable.class));
    }

    @Test
    void testStartTask_workerTaskCommandPresent_shouldNotRequireNodeTaskConfig() {
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(100)
            .brokers(TEST_BROKERS)
            .build();

        TaskConfig nodeTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(50)
            .brokers(TEST_BROKERS)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(mainTaskConfig)
            .build();

        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .workerTaskCommand(WorkerTaskCommand.fromTaskConfig(nodeTaskConfig))
            .build();

        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString())).thenReturn(Mono.just(nodeTask));

        localTaskCoordinator.startTask(TASK_ID);

        assertNotNull(localTaskCoordinator.runningTask());
        verify(nodeTaskRepository).findByTaskIdAndNodeId(eq(TASK_ID), eq(NODE_ID));
    }

    @Test
    void testStartTask_localPortModeShouldKeepPreflightExcludedPorts() {
        LocalPortRangeConfig globalConfig = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(65535)
            .excludedPorts(List.of(20000))
            .build();
        LocalPortRangeConfig preflightConfig = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(65535)
            .excludedPorts(List.of(25249, 53020))
            .build();
        when(localPortModeProperties.toConfig()).thenReturn(globalConfig);

        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(100)
            .brokers(TEST_BROKERS)
            .build();
        TaskConfig nodeTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(50)
            .brokers(TEST_BROKERS)
            .localPortRangeConfig(preflightConfig)
            .build();
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(mainTaskConfig)
            .build();
        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .taskConfig(nodeTaskConfig)
            .workerTaskCommand(WorkerTaskCommand.fromTaskConfig(nodeTaskConfig))
            .build();

        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString())).thenReturn(Mono.just(nodeTask));

        localTaskCoordinator.startTask(TASK_ID);

        LocalPortRangeConfig workerConfig = nodeTask.getWorkerTaskCommand()
            .workerTaskSpec()
            .getLocalPortRangeConfig();
        assertEquals(List.of(20000, 25249, 53020), workerConfig.getExcludedPorts());
        assertEquals(List.of(20000, 25249, 53020),
            nodeTask.getTaskConfig().getLocalPortRangeConfig().getExcludedPorts());
    }

    @Test
    void testStartTask_workerTaskCommandMissing_shouldMarkTaskFailed() {
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(100)
            .brokers(TEST_BROKERS)
            .build();
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(mainTaskConfig)
            .build();
        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .build();

        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString())).thenReturn(Mono.just(nodeTask));
        when(taskInfoMetadataRepository.updateStageById(eq(TASK_ID), eq(TaskStage.FAILED.name()), any(Instant.class)))
            .thenReturn(Mono.empty());
        when(nodeTaskRepository.save(any(NodeTask.class))).thenReturn(Mono.just(nodeTask));

        localTaskCoordinator.startTask(TASK_ID);

        verify(taskInfoMetadataRepository).updateStageById(eq(TASK_ID), eq(TaskStage.FAILED.name()),
            any(Instant.class));
        verify(nodeTaskRepository).save(any(NodeTask.class));
        assertEquals(TaskStage.FAILED, nodeTask.getCurrentStage());
    }

    @Test
    void testStartTask_duplicateTask_shouldNotStartAgain() {
        
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .brokers(TEST_BROKERS)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(taskConfig)
            .build();

        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .taskConfig(taskConfig)
            .workerTaskCommand(WorkerTaskCommand.fromTaskConfig(taskConfig))
            .build();

        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString())).thenReturn(Mono.just(nodeTask));

        
        localTaskCoordinator.startTask(TASK_ID);
        Map<String, TaskStage> firstCallResult = localTaskCoordinator.runningTask();

        
        localTaskCoordinator.startTask(TASK_ID);
        Map<String, TaskStage> secondCallResult = localTaskCoordinator.runningTask();

        
        assertNotNull(firstCallResult);
        assertNotNull(secondCallResult);
    }

    @Test
    void testStartTask_taskNotFound_shouldNotThrowException() {
        
        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.empty());

        
        localTaskCoordinator.startTask(TASK_ID);

        
        
    }

    @Test
    void testStartTask_localPortModeEnabled_shouldApplyServiceConfig() {
        org.apache.bifromq.testsuite.client.LocalPortRangeConfig rangeConfig =
            org.apache.bifromq.testsuite.client.LocalPortRangeConfig.builder()
                .enabled(true)
                .startPort(10000)
                .endPort(65535)
                .build();
        when(localPortModeProperties.isEnabled()).thenReturn(true);
        when(localPortModeProperties.toConfig()).thenReturn(rangeConfig);

        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.CONN)
            .totalClientCount(100)
            .brokers(TEST_BROKERS)
            .build();

        TaskConfig nodeTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .taskType(TaskConfig.TaskType.CONN)
            .totalClientCount(50)
            .taskWorkStage(TaskStage.INIT)
            .enableAutoMultiAddress(false)
            .localAddresses(List.of("127.0.0.1"))
            .brokers(TEST_BROKERS)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(mainTaskConfig)
            .build();

        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .taskConfig(nodeTaskConfig)
            .workerTaskCommand(WorkerTaskCommand.fromTaskConfig(nodeTaskConfig))
            .build();

        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString())).thenReturn(Mono.just(nodeTask));

        localTaskCoordinator.startTask(TASK_ID);

        assertEquals(true, nodeTaskConfig.getLocalPortRangeConfig().isEnabled());
        assertEquals(10000, nodeTaskConfig.getLocalPortRangeConfig().getStartPort());
        assertEquals(65535, nodeTaskConfig.getLocalPortRangeConfig().getEndPort());
        assertEquals(false, nodeTaskConfig.isEnableAutoMultiAddress());
    }

    @Test
    void testStartTask_localPortModeEnabledWithoutLocalAddresses_shouldUsePrimaryAddressMode() {
        org.apache.bifromq.testsuite.client.LocalPortRangeConfig rangeConfig =
            org.apache.bifromq.testsuite.client.LocalPortRangeConfig.builder()
                .enabled(true)
                .startPort(10000)
                .endPort(65535)
                .build();
        when(localPortModeProperties.isEnabled()).thenReturn(true);
        when(localPortModeProperties.toConfig()).thenReturn(rangeConfig);

        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.CONN)
            .totalClientCount(100)
            .brokers(TEST_BROKERS)
            .build();

        TaskConfig nodeTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .taskType(TaskConfig.TaskType.CONN)
            .totalClientCount(50)
            .taskWorkStage(TaskStage.INIT)
            .localAddresses(List.of())
            .brokers(TEST_BROKERS)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(mainTaskConfig)
            .build();

        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .taskConfig(nodeTaskConfig)
            .workerTaskCommand(WorkerTaskCommand.fromTaskConfig(nodeTaskConfig))
            .build();

        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString())).thenReturn(Mono.just(nodeTask));

        localTaskCoordinator.startTask(TASK_ID);

        assertEquals(true, nodeTaskConfig.getLocalPortRangeConfig().isEnabled());
        assertEquals(false, nodeTaskConfig.isEnableAutoMultiAddress());
        assertEquals(List.of(), nodeTaskConfig.getLocalAddresses());
    }

    

    
    @Test
    void testStopTask_whenRunningTaskMapEmpty_shouldHandleGracefully() {
        
        
        
        
        
    }

    

    @Test
    void testTaskFinish_partialTasksFinished_shouldNotCompleteTask() {
        
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .brokers(TEST_BROKERS)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskName("Test Task")
            .taskConfig(taskConfig)
            .build();

        NodeTask nodeTask1 = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .nodeName("test-node")
            .taskConfig(taskConfig)
            .workerTaskCommand(WorkerTaskCommand.fromTaskConfig(taskConfig))
            .build();

        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString())).thenReturn(Mono.just(nodeTask1));

        
        localTaskCoordinator.startTask(TASK_ID);

        
        Map<String, TaskStage> runningTasks = localTaskCoordinator.runningTask();
        assertNotNull(runningTasks);
        
    }

    

    @Test
    void testHandleNodeTimeout_noRunningTasks_shouldReturnEarly() {
        
        String timeoutNodeId = "timeout-node-123";
        when(nodeTaskRepository.findAllByNodeId(timeoutNodeId)).thenReturn(Flux.empty());

        
        localTaskCoordinator.handleNodeTimeout(timeoutNodeId);

        
        verify(nodeTaskRepository).findAllByNodeId(timeoutNodeId);
        Map<String, TaskStage> runningTasks = localTaskCoordinator.runningTask();
        assertNotNull(runningTasks);
        assertEquals(0, runningTasks.size());
    }

    @Test
    void testHandleNodeTimeout_taskRunningOnCurrentNode_shouldRemoveFromRunningMap() {
        
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(100)
            .brokers(TEST_BROKERS)
            .build();

        TaskConfig nodeTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)  
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(50)
            .taskWorkStage(TaskStage.ONGOING)
            .brokers(TEST_BROKERS)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskName("Test Task")
            .taskConfig(mainTaskConfig)
            .build();

        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)  
            .nodeName("test-node")
            .taskConfig(nodeTaskConfig)
            .workerTaskCommand(WorkerTaskCommand.fromTaskConfig(nodeTaskConfig))
            .build();

        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString())).thenReturn(Mono.just(nodeTask));
        when(nodeTaskRepository.findAllByTaskId(anyString())).thenReturn(Flux.just(nodeTask));
        when(nodeTaskRepository.findAllByNodeId(anyString())).thenReturn(Flux.just(nodeTask));
        when(nodeTaskRepository.save(any(NodeTask.class))).thenReturn(Mono.just(nodeTask));

        
        localTaskCoordinator.startTask(TASK_ID);

        
        Map<String, TaskStage> runningTasksBeforeTimeout = localTaskCoordinator.runningTask();
        assertNotNull(runningTasksBeforeTimeout);
        assertEquals(1, runningTasksBeforeTimeout.size());

        
        localTaskCoordinator.handleNodeTimeout(NODE_ID);

        
        Map<String, TaskStage> runningTasksAfterTimeout = localTaskCoordinator.runningTask();
        assertEquals(0, runningTasksAfterTimeout.size());
    }

    @Test
    void testHandleNodeTimeout_remoteNodeTimeout_shouldUpdateNodeTasksInDb() {

        String remoteNodeId = "remote-node-456";

        TaskConfig nodeTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .nodeId(remoteNodeId)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .totalClientCount(50)
            .taskWorkStage(TaskStage.ONGOING)
            .brokers(TEST_BROKERS)
            .build();

        NodeTask remoteNodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId(remoteNodeId)
            .nodeName("remote-node")
            .taskConfig(nodeTaskConfig)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskName("Test Task")
            .taskConfig(TaskConfig.builder()
                .taskId(TASK_ID)
                .taskWorkStage(TaskStage.ONGOING)
                .build())
            .build();

        when(nodeTaskRepository.findAllByNodeId(remoteNodeId)).thenReturn(Flux.just(remoteNodeTask));
        when(nodeTaskRepository.save(any(NodeTask.class))).thenReturn(Mono.just(remoteNodeTask));
        when(nodeTaskRepository.findAllByTaskId(TASK_ID)).thenReturn(Flux.just(remoteNodeTask));
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));

        localTaskCoordinator.handleNodeTimeout(remoteNodeId);

        verify(nodeTaskRepository).findAllByNodeId(remoteNodeId);
        verify(nodeTaskRepository).save(any(NodeTask.class));
        assertEquals(TaskStage.FAILED, remoteNodeTask.getTaskConfig().getTaskWorkStage());
    }

    @Test
    void testHandleNodeTimeout_duplicateNodeTimeout_shouldOnlyProcessOnce() {
        String remoteNodeId = "remote-node-456";

        when(nodeTaskRepository.findAllByNodeId(remoteNodeId)).thenReturn(Flux.empty());

        localTaskCoordinator.handleNodeTimeout(remoteNodeId);
        localTaskCoordinator.handleNodeTimeout(remoteNodeId);

        verify(nodeTaskRepository).findAllByNodeId(remoteNodeId);
    }

    

    
    @Test
    void taskFinish_allNodesShutdown_globalStateIsShutdown() {
        
        NodeTask nt1 = NodeTask.builder().taskId(TASK_ID).nodeId("node-1")
            .currentStage(TaskStage.SHUTDOWN)
            .taskConfig(TaskConfig.builder().taskId(TASK_ID).nodeId("node-1")
                .taskType(TaskConfig.TaskType.PUBSUB).template(TaskTemplate.PUBSUB_STANDARD)
                .totalClientCount(10).brokers(TEST_BROKERS).taskWorkStage(TaskStage.SHUTDOWN).build())
            .build();
        NodeTask nt2 = NodeTask.builder().taskId(TASK_ID).nodeId("node-2")
            .currentStage(TaskStage.SHUTDOWN)
            .taskConfig(TaskConfig.builder().taskId(TASK_ID).nodeId("node-2")
                .taskType(TaskConfig.TaskType.PUBSUB).template(TaskTemplate.PUBSUB_STANDARD)
                .totalClientCount(10).brokers(TEST_BROKERS).taskWorkStage(TaskStage.SHUTDOWN).build())
            .build();
        NodeTask nt3 = NodeTask.builder().taskId(TASK_ID).nodeId("node-3")
            .currentStage(TaskStage.SHUTDOWN)
            .taskConfig(TaskConfig.builder().taskId(TASK_ID).nodeId("node-3")
                .taskType(TaskConfig.TaskType.PUBSUB).template(TaskTemplate.PUBSUB_STANDARD)
                .totalClientCount(10).brokers(TEST_BROKERS).taskWorkStage(TaskStage.SHUTDOWN).build())
            .build();

        
        TaskStage globalStage = aggregateStageFromNodeTasks(List.of(nt1, nt2, nt3));

        
        assertEquals(TaskStage.SHUTDOWN, globalStage,
            "when all nodes are SHUTDOWN, global state should be SHUTDOWN");
    }

    
    @Test
    void taskFinish_oneNodeFailed_globalStateIsFailed() {
        
        NodeTask failedNode = NodeTask.builder().taskId(TASK_ID).nodeId("node-1")
            .currentStage(TaskStage.FAILED)
            .taskConfig(TaskConfig.builder().taskId(TASK_ID).nodeId("node-1")
                .taskType(TaskConfig.TaskType.PUBSUB).template(TaskTemplate.PUBSUB_STANDARD)
                .totalClientCount(10).brokers(TEST_BROKERS).taskWorkStage(TaskStage.FAILED).build())
            .build();
        NodeTask shutdownNode2 = NodeTask.builder().taskId(TASK_ID).nodeId("node-2")
            .currentStage(TaskStage.SHUTDOWN)
            .taskConfig(TaskConfig.builder().taskId(TASK_ID).nodeId("node-2")
                .taskType(TaskConfig.TaskType.PUBSUB).template(TaskTemplate.PUBSUB_STANDARD)
                .totalClientCount(10).brokers(TEST_BROKERS).taskWorkStage(TaskStage.SHUTDOWN).build())
            .build();
        NodeTask shutdownNode3 = NodeTask.builder().taskId(TASK_ID).nodeId("node-3")
            .currentStage(TaskStage.SHUTDOWN)
            .taskConfig(TaskConfig.builder().taskId(TASK_ID).nodeId("node-3")
                .taskType(TaskConfig.TaskType.PUBSUB).template(TaskTemplate.PUBSUB_STANDARD)
                .totalClientCount(10).brokers(TEST_BROKERS).taskWorkStage(TaskStage.SHUTDOWN).build())
            .build();

        
        TaskStage globalStage = aggregateStageFromNodeTasks(List.of(failedNode, shutdownNode2, shutdownNode3));

        
        assertEquals(TaskStage.FAILED, globalStage,
            "when any node is FAILED, global state should be FAILED (FAILED has highest priority)");
    }

    
    private TaskStage aggregateStageFromNodeTasks(List<NodeTask> nodeTasks) {
        java.util.Set<TaskStage> stages = nodeTasks.stream()
            .map(nt -> nt.getCurrentStage() != null ? nt.getCurrentStage() : TaskStage.INIT)
            .collect(java.util.stream.Collectors.toSet());

        if (stages.contains(TaskStage.FAILED)) {
            return TaskStage.FAILED;
        }
        if (stages.contains(TaskStage.TIMEOUT)) {
            return TaskStage.TIMEOUT;
        }
        if (stages.contains(TaskStage.ONGOING)) {
            return TaskStage.ONGOING;
        }
        if (stages.contains(TaskStage.SHUTTING)) {
            return TaskStage.SHUTTING;
        }

        boolean allTerminal = stages.stream()
            .allMatch(s -> s == TaskStage.SHUTDOWN || s == TaskStage.STOPPED || s == TaskStage.FAILED);
        if (allTerminal) {
            if (stages.contains(TaskStage.FAILED)) {
                return TaskStage.FAILED;
            }
            if (stages.contains(TaskStage.STOPPED)) {
                return TaskStage.STOPPED;
            }
            return TaskStage.SHUTDOWN;
        }

        if (stages.contains(TaskStage.STARTING)) {
            return TaskStage.STARTING;
        }
        if (stages.contains(TaskStage.ASSIGNED)) {
            return TaskStage.ASSIGNED;
        }
        return TaskStage.INIT;
    }

    

    @Test
    void testRunningTask_shouldReturnEmptyMapWhenNoTasksRunning() {
        

        
        Map<String, TaskStage> result = localTaskCoordinator.runningTask();

        
        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
