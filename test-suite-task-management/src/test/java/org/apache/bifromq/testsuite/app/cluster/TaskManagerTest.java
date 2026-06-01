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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.app.bean.PipelineStagesConfig;
import org.apache.bifromq.testsuite.app.bean.TaskDetailResponse;
import org.apache.bifromq.testsuite.app.bean.dto.BrokerEntry;
import org.apache.bifromq.testsuite.app.bean.dto.NodeTaskAllocationRequest;
import org.apache.bifromq.testsuite.app.bean.dto.TaskRequest;
import org.apache.bifromq.testsuite.app.bean.vo.NodeTaskAllocationVO;
import org.apache.bifromq.testsuite.app.bean.vo.TaskBasicInfoResponse;
import org.apache.bifromq.testsuite.app.bean.vo.TaskSubTasksResponse;
import org.apache.bifromq.testsuite.app.cluster.core.ClusterDataManager;
import org.apache.bifromq.testsuite.app.task.TaskManager;
import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.app.database.pojo.MqttGroup;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.repository.MqttBrokerRepository;
import org.apache.bifromq.testsuite.app.database.repository.MqttGroupRepository;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskMetricsSnapshotRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskStateHistoryRepository;
import org.apache.bifromq.testsuite.app.database.service.TaskInfoMetadataService;
import org.apache.bifromq.testsuite.app.database.service.TaskMetricsSnapshotService;
import org.apache.bifromq.testsuite.app.profile.TaskProfile;
import org.apache.bifromq.testsuite.app.profile.TaskProfileService;
import org.apache.bifromq.testsuite.web.ApiException;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TaskManagerTest {

    private static final String TASK_ID = "test-task-123";
    private static final String BROKER_ID = "broker-001";
    private static final String GROUP_ID = "group-001";
    @Mock
    private Vertx vertx;
    @Mock
    private ClusterDataManager clusterDataManager;
    @Mock
    private TaskInfoMetadataService taskInfoMetadataService;
    @Mock
    private TaskProfileService taskProfileService;
    @Mock
    private TaskInfoMetadataRepository taskInfoMetadataRepository;
    @Mock
    private NodeTaskRepository nodeTaskRepository;
    @Mock
    private MqttBrokerRepository mqttBrokerRepository;
    @Mock
    private MqttGroupRepository mqttGroupRepository;
    @Mock
    private PipelineStagesConfig pipelineStagesConfig;
    @Mock
    private TaskMetricsSnapshotRepository taskMetricsSnapshotRepository;
    @Mock
    private TaskMetricsSnapshotService taskMetricsSnapshotService;
    @Mock
    private TaskStateHistoryRepository taskStateHistoryRepository;
    @InjectMocks
    private TaskManager taskManager;

    @BeforeEach
    void setUp() {
        lenient().when(clusterDataManager.getCurrentNodeIdCache()).thenReturn("node-abc123");
        lenient().when(taskStateHistoryRepository.deleteByTaskId(anyString())).thenReturn(Mono.empty());
        lenient().when(taskMetricsSnapshotRepository.deleteByTaskId(anyString())).thenReturn(Mono.empty());
        lenient().when(mqttGroupRepository.findById(anyString())).thenReturn(Mono.just(MqttGroup.builder()
            .id("test-group")
            .type("BROKER")
            .name("test-group")
            .build()));
    }

    

    @Test
    void testAddTask_success_shouldSaveTaskAndReturnResponse() {
        
        TaskRequest taskRequest = createTaskRequest();

        MqttBroker mqttBroker = MqttBroker.builder()
            .brokerId(BROKER_ID)
            .host("localhost")
            .port(1883)
            .group("test-group")
            .build();

        when(mqttBrokerRepository.findAllById(anyList())).thenReturn(Flux.just(mqttBroker));
        when(mqttBrokerRepository.findByBrokerId(anyString())).thenReturn(Mono.just(mqttBroker));
        lenient().when(taskInfoMetadataRepository.findByTaskName(anyString())).thenReturn(Mono.empty());
        when(taskInfoMetadataService.insertTaskInfoMetadata(any(TaskInfoMetadata.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        
        TaskInfoMetadata result = taskManager.addTask(taskRequest).block();

        
        assertNotNull(result);
        assertEquals("Test Task", result.getTaskName());
        verify(taskInfoMetadataService).insertTaskInfoMetadata(any(TaskInfoMetadata.class));
    }

    @Test
    void testAddTask_nullRequest_shouldThrowException() {
        
        TaskRequest taskRequest = null;

        
        ApiException exception = assertThrows(ApiException.class, () -> {
            taskManager.addTask(taskRequest).block();
        });
        assertEquals("Task request cannot be null", exception.getMessage());
    }

    @Test
    void testAddTask_chaosTask_shouldThrowException() {
        TaskRequest taskRequest = createTaskRequest();
        taskRequest.setTaskType(TaskConfig.TaskType.CHAOS);
        taskRequest.setTemplate(TaskTemplate.CHAOS_STANDARD.name());

        ApiException exception = assertThrows(ApiException.class, () -> {
            taskManager.addTask(taskRequest).block();
        });

        assertEquals("error.task.chaosDeprecated", exception.getMessage());
    }

    @Test
    void testAddTask_emptyGroup_shouldThrowException() {
        
        TaskRequest taskRequest = createTaskRequest();
        taskRequest.setGroup("");

        
        ApiException exception = assertThrows(ApiException.class, () -> {
            taskManager.addTask(taskRequest).block();
        });
        assertEquals("error.task.groupNotEmpty", exception.getMessage());
    }

    @Test
    void testAddTask_nullGroup_shouldThrowException() {
        
        TaskRequest taskRequest = createTaskRequest();
        taskRequest.setGroup(null);

        
        ApiException exception = assertThrows(ApiException.class, () -> {
            taskManager.addTask(taskRequest).block();
        });
        assertEquals("error.task.groupNotEmpty", exception.getMessage());
    }

    

    @Test
    void testModifyTask_success_shouldUpdateTaskAndReturnResponse() {
        
        TaskRequest taskRequest = createTaskRequest();

        TaskConfig existingConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .taskWorkStage(TaskStage.INIT)
            .build();

        TaskInfoMetadata existingMetadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskName("Old Name")
            .taskConfig(existingConfig)
            .build();

        MqttBroker mqttBroker = MqttBroker.builder()
            .brokerId(BROKER_ID)
            .host("localhost")
            .port(1883)
            .group("test-group")
            .build();

        when(mqttBrokerRepository.findAllById(anyList())).thenReturn(Flux.just(mqttBroker));
        lenient().when(taskInfoMetadataRepository.findByTaskName(anyString())).thenReturn(Mono.empty());
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(existingMetadata));
        when(taskInfoMetadataRepository.save(any(TaskInfoMetadata.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        
        TaskInfoMetadata result = taskManager.modifyTask(TASK_ID, taskRequest).block();

        
        assertNotNull(result);
        assertEquals("Test Task", result.getTaskName());
    }

    @Test
    void testModifyTask_nullTaskId_shouldThrowException() {
        
        TaskRequest taskRequest = createTaskRequest();

        
        ApiException exception = assertThrows(ApiException.class, () -> {
            taskManager.modifyTask(null, taskRequest).block();
        });
        assertEquals("Task id cannot be null", exception.getMessage());
    }

    @Test
    void testModifyTask_chaosTask_shouldThrowException() {
        TaskRequest taskRequest = createTaskRequest();
        taskRequest.setTaskType(TaskConfig.TaskType.CHAOS);
        taskRequest.setTemplate(TaskTemplate.CHAOS_STANDARD.name());

        ApiException exception = assertThrows(ApiException.class, () -> {
            taskManager.modifyTask(TASK_ID, taskRequest).block();
        });

        assertEquals("error.task.chaosDeprecated", exception.getMessage());
    }

    @Test
    void testModifyTask_taskNotFound_shouldThrowException() {
        
        TaskRequest taskRequest = createTaskRequest();

        MqttBroker mqttBroker = MqttBroker.builder()
            .brokerId(BROKER_ID)
            .host("localhost")
            .port(1883)
            .group("test-group")
            .build();

        when(mqttBrokerRepository.findAllById(anyList())).thenReturn(Flux.just(mqttBroker));
        when(taskInfoMetadataRepository.findByTaskName("Test Task")).thenReturn(Mono.empty());
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.empty());

        
        ApiException exception = assertThrows(ApiException.class, () -> {
            taskManager.modifyTask(TASK_ID, taskRequest).block();
        });
        assertTrue(exception.getMessage().contains("Task not found"));
    }

    

    @Test
    void testDelTask_success_shouldDeleteTaskAndReturnResponse() {
        
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskWorkStage(TaskStage.INIT)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(taskConfig)
            .build();

        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(taskInfoMetadataRepository.deleteById(TASK_ID)).thenReturn(Mono.empty());
        when(nodeTaskRepository.deleteByTaskId(TASK_ID)).thenReturn(Mono.empty());
        when(taskStateHistoryRepository.deleteByTaskId(TASK_ID)).thenReturn(Mono.empty());

        
        ApiResponse<TaskDetailResponse> result = taskManager.delTask(TASK_ID).block();

        
        assertTrue(result.isSuccess());
    }

    @Test
    void testDelTask_taskNotFound_shouldReturnError() {
        
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.empty());

        
        ApiResponse<TaskDetailResponse> result = taskManager.delTask(TASK_ID).block();

        
        assertFalse(result.isSuccess());
        assertEquals("error.task.notFound", result.getMessage());
    }

    

    @Test
    void testGetTaskDetails_success_shouldReturnTaskDetails() {
        
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .taskWorkStage(TaskStage.INIT)
            .totalClientCount(100)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskName("Test Task")
            .taskConfig(taskConfig)
            .build();

        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId("node-001")
            .taskConfig(TaskConfig.builder()
                .nodeId("node-001")
                .totalClientCount(50)
                .taskType(TaskConfig.TaskType.PUBSUB)
                .build())
            .build();

        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findAllByTaskId(TASK_ID)).thenReturn(Flux.just(nodeTask));

        
        ApiResponse<TaskDetailResponse> result = taskManager.getTaskDetails(TASK_ID).block();

        
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(TASK_ID, result.getData().getTaskId());
    }

    @Test
    void testGetTaskBasicInfo_dynamicPublishProfile_shouldReturnProfileSummary() {
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .taskWorkStage(TaskStage.INIT)
            .qpsMode(TaskConfig.QpsMode.DYNAMIC)
            .profileConfig(TaskConfig.ProfileConfig.builder()
                .profileId("profile-1")
                .dataPoints(List.of(new long[] {0, 0}, new long[] {1_000, 10}))
                .build())
            .build();
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskName("Test Task")
            .taskConfig(taskConfig)
            .build();
        TaskProfile profile = TaskProfile.builder()
            .id("profile-1")
            .name("Publish Profile")
            .dataPoints(List.of(new long[] {0, 0}, new long[] {1_000, 10}))
            .totalDurationMs(1_000)
            .integral(5)
            .build();
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(taskProfileService.getTaskProfileById("profile-1")).thenReturn(Mono.just(profile));

        ApiResponse<TaskBasicInfoResponse> result = taskManager.getTaskBasicInfo(TASK_ID).block();

        assertTrue(result.isSuccess());
        assertNotNull(result.getData().getPublishProfile());
        assertEquals("Publish Profile", result.getData().getPublishProfile().getName());
        assertNotNull(result.getData().getMainTaskView());
        assertEquals(TaskStage.INIT, result.getData().getMainTaskView().taskWorkStage());
        assertEquals("profile-1", result.getData().getPublishProfile().getId());
        assertEquals(5, result.getData().getPublishProfile().getIntegral());
        assertNull(result.getData().getPublishProfile().getDataPoints());
    }

    @Test
    void testGetTaskDetails_taskNotFound_shouldReturnError() {
        
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.empty());

        
        ApiResponse<TaskDetailResponse> result = taskManager.getTaskDetails(TASK_ID).block();

        
        assertFalse(result.isSuccess());
        assertEquals("error.task.notFound", result.getMessage());
    }

    @Test
    void testGetTaskSubTasks_shouldPreferCurrentStageOverConfigStage() {
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.CONN)
            .taskWorkStage(TaskStage.SHUTTING)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskName("Test Task")
            .taskConfig(taskConfig)
            .build();

        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId("node-001")
            .currentStage(TaskStage.SHUTDOWN)
            .taskConfig(TaskConfig.builder()
                .nodeId("node-001")
                .taskType(TaskConfig.TaskType.CONN)
                .totalClientCount(50)
                .taskWorkStage(TaskStage.STARTING)
                .build())
            .build();

        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findAllByTaskId(TASK_ID)).thenReturn(Flux.just(nodeTask));

        ApiResponse<TaskSubTasksResponse> result = taskManager.getTaskSubTasks(TASK_ID).block();

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("SHUTDOWN",
            result.getData().getSubTaskDetails().get("node-001").getTaskWorkStage());
    }

    

    @Test
    void testBatchDelTask_success_shouldDeleteMultipleTasks() {
        
        List<String> taskIds = List.of(TASK_ID, "task-456");

        TaskConfig taskConfig = TaskConfig.builder()
            .taskWorkStage(TaskStage.INIT)
            .build();

        TaskInfoMetadata metadata1 = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(taskConfig)
            .build();

        TaskInfoMetadata metadata2 = TaskInfoMetadata.builder()
            .taskId("task-456")
            .taskConfig(taskConfig)
            .build();

        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata1));
        when(taskInfoMetadataRepository.findById("task-456")).thenReturn(Mono.just(metadata2));
        when(taskInfoMetadataRepository.deleteById(anyString())).thenReturn(Mono.empty());
        when(nodeTaskRepository.deleteByTaskId(anyString())).thenReturn(Mono.empty());
        when(taskStateHistoryRepository.deleteByTaskId(anyString())).thenReturn(Mono.empty());

        
        ApiResponse<String> result = taskManager.batchDelTask(taskIds).block();

        
        assertTrue(result.isSuccess());
        assertTrue(result.getData() != null);
    }

    @Test
    void testBatchDelTask_emptyList_shouldReturnError() {
        
        List<String> taskIds = Collections.emptyList();

        
        ApiResponse<String> result = taskManager.batchDelTask(taskIds).block();

        
        assertFalse(result.isSuccess());
        assertEquals("error.task.idListEmpty", result.getMessage());
    }

    @Test
    void testBatchDelTask_nullList_shouldReturnError() {
        
        List<String> taskIds = null;

        
        ApiResponse<String> result = taskManager.batchDelTask(taskIds).block();

        
        assertFalse(result.isSuccess());
        assertEquals("error.task.idListEmpty", result.getMessage());
    }

    

    @Test
    void testAssignTask_success_shouldAssignTaskToNodes() {
        
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskWorkStage(TaskStage.INIT)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(taskConfig)
            .build();

        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();
        request.setTotalClientCount(100);
        request.setNodeAllocationList(new ArrayList<>());

        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(clusterDataManager.assignCheck(eq(TASK_ID), any(TaskConfig.class), any(NodeTaskAllocationRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(taskInfoMetadataRepository.save(any(TaskInfoMetadata.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        
        ApiResponse<TaskConfig> result = taskManager.assignTask(TASK_ID, request).block();

        
        assertTrue(result.isSuccess());
        assertEquals(TaskStage.ASSIGNED, metadata.getCurrentStage());
        assertEquals(TaskStage.ASSIGNED, metadata.getTaskConfig().getTaskWorkStage());
    }

    @Test
    void testAssignTask_taskNotFound_shouldReturnError() {
        
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.empty());
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();

        
        ApiResponse<TaskConfig> result = taskManager.assignTask(TASK_ID, request).block();

        
        assertFalse(result.isSuccess());
        assertEquals("error.task.notFound", result.getMessage());
    }

    @Test
    void testAssignTask_assignCheckFailed_shouldReturnReason() {
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskWorkStage(TaskStage.INIT)
            .build();
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(taskConfig)
            .build();
        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new ApiException("Node not found or offline: [node-001]"));

        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(clusterDataManager.assignCheck(eq(TASK_ID), any(TaskConfig.class), any(NodeTaskAllocationRequest.class)))
            .thenReturn(failedFuture);

        ApiResponse<TaskConfig> result = taskManager.assignTask(TASK_ID, new NodeTaskAllocationRequest()).block();

        assertFalse(result.isSuccess());
        assertEquals("Node not found or offline: [node-001]", result.getMessage());
    }

    

    @Test
    void testCalculateNodeTaskAllocation_success_shouldCalculateAllocation() {
        
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .totalClientCount(100)
            .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(taskConfig)
            .build();

        NodeTaskAllocationVO allocationVO = new NodeTaskAllocationVO();
        allocationVO.setTotalClientCount(100);

        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(clusterDataManager.calcuTasksToNodes(any(TaskConfig.class)))
            .thenReturn(CompletableFuture.completedFuture(allocationVO));

        
        ApiResponse<NodeTaskAllocationVO> result = taskManager.calculateNodeTaskAllocation(TASK_ID).block();

        
        assertTrue(result.isSuccess());
    }

    @Test
    void testCalculateNodeTaskAllocation_taskNotFound_shouldReturnError() {
        
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.empty());

        
        ApiResponse<NodeTaskAllocationVO> result = taskManager.calculateNodeTaskAllocation(TASK_ID).block();

        
        assertFalse(result.isSuccess());
        assertEquals("error.task.notFound", result.getMessage());
    }

    @Test
    void testPrepareTaskStart_shouldSetPlannedStartAtForMainAndNodeTasks() {
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .build();
        TaskConfig nodeTaskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .nodeId("node-1")
            .build();
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(mainTaskConfig)
            .build();
        NodeTask nodeTask = NodeTask.builder()
            .taskId(TASK_ID)
            .nodeId("node-1")
            .taskConfig(nodeTaskConfig)
            .build();
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(taskInfoMetadataRepository.updateTaskConfigById(eq(TASK_ID), any(TaskConfig.class)))
            .thenReturn(Mono.empty());
        when(clusterDataManager.prepareAssignedTaskStart(eq(TASK_ID), any(Long.class)))
            .thenAnswer(invocation -> {
                Long plannedStartAtMs = invocation.getArgument(1);
                nodeTask.setPlannedStartAtMs(plannedStartAtMs);
                nodeTask.setWorkerTaskCommand(
                    org.apache.bifromq.testsuite.worker.WorkerTaskCommand.fromTaskConfig(
                        nodeTask.getTaskConfig(), plannedStartAtMs));
                return CompletableFuture.completedFuture(null);
            });

        taskManager.prepareTaskStart(TASK_ID).block();

        assertNotNull(metadata.getPlannedStartAtMs());
        assertEquals(TaskStage.STARTING, metadata.getCurrentStage());
        assertEquals(TaskStage.STARTING, metadata.getTaskConfig().getTaskWorkStage());
        assertNotNull(nodeTask.getPlannedStartAtMs());
        assertEquals(metadata.getPlannedStartAtMs(), nodeTask.getPlannedStartAtMs());
        assertEquals(metadata.getPlannedStartAtMs(),
            nodeTask.getWorkerTaskCommand().workerTaskSpec().getPlannedStartAtMs());
        verify(clusterDataManager).prepareAssignedTaskStart(eq(TASK_ID), any(Long.class));
    }

    @Test
    void testAssignTask_dynamicPublishProfile_shouldUseProfileDurationAsStressDuration() {
        TaskConfig.ProfileConfig profileConfig = TaskConfig.ProfileConfig.builder()
            .profileId("profile-1")
            .build();
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId(TASK_ID)
            .taskType(TaskConfig.TaskType.PUBSUB)
            .taskWorkStage(TaskStage.INIT)
            .qpsMode(TaskConfig.QpsMode.DYNAMIC)
            .profileConfig(profileConfig)
            .stressDurationInSec(60)
            .build();
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(taskConfig)
            .build();
        TaskProfile profile = TaskProfile.builder()
            .id("profile-1")
            .dataPoints(List.of(new long[] {0, 0}, new long[] {12_300, 100}))
            .totalDurationMs(12_300)
            .build();
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(taskProfileService.getTaskProfileById("profile-1")).thenReturn(Mono.just(profile));
        when(clusterDataManager.assignCheck(eq(TASK_ID), any(TaskConfig.class), any(NodeTaskAllocationRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(taskInfoMetadataRepository.save(any(TaskInfoMetadata.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        ApiResponse<TaskConfig> result = taskManager.assignTask(TASK_ID, new NodeTaskAllocationRequest()).block();

        assertTrue(result.isSuccess());
        assertEquals(TaskStage.ASSIGNED, metadata.getCurrentStage());
        assertEquals(TaskStage.ASSIGNED, result.getData().getTaskWorkStage());
        assertEquals(13, result.getData().getStressDurationInSec());
        assertEquals(12_300, result.getData().getProfileConfig().getTotalDurationMs());
    }

    

    private TaskRequest createTaskRequest() {
        TaskRequest request = new TaskRequest();
        request.setTaskName("Test Task");
        request.setTaskType(TaskConfig.TaskType.PUBSUB);

        BrokerEntry broker = new BrokerEntry();
        broker.setBrokerId(BROKER_ID);
        broker.setHost("localhost");
        broker.setPort(1883);
        request.setBrokers(List.of(broker));

        request.setTotalClientCount(100);
        request.setGroup("test-group");

        return request;
    }

    

    @Test
    void testGetAllTask_withGroupFilter_shouldCallServiceWithGroup() {
        
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskInfoMetadata> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(taskInfoMetadataService.findByFilters(anyString(), anyString(), eq(GROUP_ID), isNull(),
            any(Pageable.class)))
            .thenReturn(Mono.just(page));

        
        Page<TaskInfoMetadata> result = taskManager.getAllTask("Test Task", "CONN", GROUP_ID, null, pageable).block();

        
        assertNotNull(result);
        verify(taskInfoMetadataService).findByFilters(eq("Test Task"), eq("CONN"), eq(GROUP_ID), isNull(),
            eq(pageable));
    }

    @Test
    void testGetAllTask_withOnlyGroupFilter_shouldCallServiceWithGroup() {
        
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskInfoMetadata> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(taskInfoMetadataService.findByFilters(isNull(), isNull(), eq(GROUP_ID), isNull(), any(Pageable.class)))
            .thenReturn(Mono.just(page));

        
        Page<TaskInfoMetadata> result = taskManager.getAllTask(null, null, GROUP_ID, null, pageable).block();

        
        assertNotNull(result);
        verify(taskInfoMetadataService).findByFilters(isNull(), isNull(), eq(GROUP_ID), isNull(), eq(pageable));
    }

    @Test
    void testGetAllTask_withTaskNameAndTaskTypeAndGroup_shouldCallServiceWithAllFilters() {
        
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskInfoMetadata> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(
            taskInfoMetadataService.findByFilters(anyString(), anyString(), anyString(), isNull(), any(Pageable.class)))
            .thenReturn(Mono.just(page));

        
        Page<TaskInfoMetadata> result = taskManager.getAllTask("Test", "PUBSUB", GROUP_ID, null, pageable).block();

        
        assertNotNull(result);
        verify(taskInfoMetadataService).findByFilters(eq("Test"), eq("PUBSUB"), eq(GROUP_ID), isNull(), eq(pageable));
    }

    @Test
    void testGetAllTask_withEmptyFilters_shouldCallFindAll() {
        
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskInfoMetadata> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(taskInfoMetadataService.findAll(any(Pageable.class))).thenReturn(Mono.just(page));

        
        Page<TaskInfoMetadata> result = taskManager.getAllTask(pageable).block();

        
        assertNotNull(result);
        verify(taskInfoMetadataService).findAll(eq(pageable));
    }

    @Test
    void testGetAllTask_withTaskNameAndTypeOnly_shouldCallFindByFilters() {
        
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskInfoMetadata> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(taskInfoMetadataService.findByFilters(anyString(), anyString(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(Mono.just(page));

        
        Page<TaskInfoMetadata> result = taskManager.getAllTask("Test", "CONN", null, null, pageable).block();

        
        assertNotNull(result);
        verify(taskInfoMetadataService).findByFilters(eq("Test"), eq("CONN"), isNull(), isNull(), eq(pageable));
    }
}
