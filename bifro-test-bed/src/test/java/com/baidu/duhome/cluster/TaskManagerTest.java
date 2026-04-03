package com.baidu.duhome.cluster;

import com.baidu.duhome.bean.ApiResponse;
import com.baidu.duhome.bean.TaskDetailResponse;
import com.baidu.duhome.bean.dto.BrokerEntry;
import com.baidu.duhome.bean.dto.NodeTaskAllocationRequest;
import com.baidu.duhome.bean.dto.TaskRequest;
import com.baidu.duhome.bean.vo.NodeTaskAllocationVO;
import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.duhome.database.pojo.NodeTask;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.repository.MqttBrokerRepository;
import com.baidu.duhome.database.repository.NodeTaskRepository;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import com.baidu.duhome.database.service.TaskInfoMetadataService;
import com.baidu.duhome.exception.ApiException;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.worker.TaskConfig;
import io.vertx.core.Vertx;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

/**
 * TaskManager 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TaskManagerTest {

    @Mock
    private Vertx vertx;

    @Mock
    private ClusterDataManager clusterDataManager;

    @Mock
    private TaskInfoMetadataService taskInfoMetadataService;

    @Mock
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Mock
    private NodeTaskRepository nodeTaskRepository;

    @Mock
    private MqttBrokerRepository mqttBrokerRepository;

    @InjectMocks
    private TaskManager taskManager;

    private static final String TASK_ID = "test-task-123";
    private static final String BROKER_ID = "broker-001";
    private static final String GROUP_ID = "group-001";

    @BeforeEach
    void setUp() {
        lenient().when(clusterDataManager.getCurrentNodeIdCache()).thenReturn("node-abc123");
    }

    // ==================== 测试: addTask ====================

    @Test
    void testAddTask_success_shouldSaveTaskAndReturnResponse() {
        // given
        TaskRequest taskRequest = createTaskRequest();

        MqttBroker mqttBroker = MqttBroker.builder()
                .brokerId(BROKER_ID)
                .host("localhost")
                .port(1883)
                .group("test-group")
                .build();

        when(mqttBrokerRepository.findAllById(anyList())).thenReturn(Flux.just(mqttBroker));
        when(taskInfoMetadataService.insertTaskInfoMetadata(any(TaskInfoMetadata.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // when
        TaskInfoMetadata result = taskManager.addTask(taskRequest).block();

        // then
        assertNotNull(result);
        assertEquals("Test Task", result.getTaskName());
        verify(taskInfoMetadataService).insertTaskInfoMetadata(any(TaskInfoMetadata.class));
    }

    @Test
    void testAddTask_nullRequest_shouldThrowException() {
        // given
        TaskRequest taskRequest = null;

        // when & then
        ApiException exception = assertThrows(ApiException.class, () -> {
            taskManager.addTask(taskRequest).block();
        });
        assertEquals("Task request cannot be null", exception.getMessage());
    }

    // ==================== 测试: modifyTask ====================

    @Test
    void testModifyTask_success_shouldUpdateTaskAndReturnResponse() {
        // given
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
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(existingMetadata));
        when(taskInfoMetadataRepository.save(any(TaskInfoMetadata.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // when
        TaskInfoMetadata result = taskManager.modifyTask(TASK_ID, taskRequest).block();

        // then
        assertNotNull(result);
        assertEquals("Test Task", result.getTaskName());
    }

    @Test
    void testModifyTask_nullTaskId_shouldThrowException() {
        // given
        TaskRequest taskRequest = createTaskRequest();

        // when & then
        ApiException exception = assertThrows(ApiException.class, () -> {
            taskManager.modifyTask(null, taskRequest).block();
        });
        assertEquals("Task id cannot be null", exception.getMessage());
    }

    @Test
    void testModifyTask_taskNotFound_shouldThrowException() {
        // given
        TaskRequest taskRequest = createTaskRequest();

        MqttBroker mqttBroker = MqttBroker.builder()
                .brokerId(BROKER_ID)
                .host("localhost")
                .port(1883)
                .group("test-group")
                .build();

        when(mqttBrokerRepository.findAllById(anyList())).thenReturn(Flux.just(mqttBroker));
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.empty());

        // when & then
        ApiException exception = assertThrows(ApiException.class, () -> {
            taskManager.modifyTask(TASK_ID, taskRequest).block();
        });
        assertTrue(exception.getMessage().contains("Task not found"));
    }

    // ==================== 测试: delTask ====================

    @Test
    void testDelTask_success_shouldDeleteTaskAndReturnResponse() {
        // given
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

        // when
        ApiResponse<TaskDetailResponse> result = taskManager.delTask(TASK_ID).block();

        // then
        assertTrue(result.isSuccess());
    }

    @Test
    void testDelTask_taskNotFound_shouldReturnError() {
        // given
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.empty());

        // when
        ApiResponse<TaskDetailResponse> result = taskManager.delTask(TASK_ID).block();

        // then
        assertFalse(result.isSuccess());
        assertEquals("任务不存在", result.getMessage());
    }

    // ==================== 测试: getTaskDetails ====================

    @Test
    void testGetTaskDetails_success_shouldReturnTaskDetails() {
        // given
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
                        .build())
                .build();

        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findAllByTaskId(TASK_ID)).thenReturn(Flux.just(nodeTask));

        // when
        ApiResponse<TaskDetailResponse> result = taskManager.getTaskDetails(TASK_ID).block();

        // then
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(TASK_ID, result.getData().getTaskId());
    }

    @Test
    void testGetTaskDetails_taskNotFound_shouldReturnError() {
        // given
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.empty());

        // when
        ApiResponse<TaskDetailResponse> result = taskManager.getTaskDetails(TASK_ID).block();

        // then
        assertFalse(result.isSuccess());
        assertEquals("任务不存在", result.getMessage());
    }

    // ==================== 测试: batchDelTask ====================

    @Test
    void testBatchDelTask_success_shouldDeleteMultipleTasks() {
        // given
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

        // when
        ApiResponse<String> result = taskManager.batchDelTask(taskIds).block();

        // then
        assertTrue(result.isSuccess());
        assertTrue(result.getData().contains("成功删除"));
    }

    @Test
    void testBatchDelTask_emptyList_shouldReturnError() {
        // given
        List<String> taskIds = Collections.emptyList();

        // when
        ApiResponse<String> result = taskManager.batchDelTask(taskIds).block();

        // then
        assertFalse(result.isSuccess());
        assertEquals("任务ID列表不能为空", result.getMessage());
    }

    @Test
    void testBatchDelTask_nullList_shouldReturnError() {
        // given
        List<String> taskIds = null;

        // when
        ApiResponse<String> result = taskManager.batchDelTask(taskIds).block();

        // then
        assertFalse(result.isSuccess());
        assertEquals("任务ID列表不能为空", result.getMessage());
    }

    // ==================== 测试: assignTask ====================

    @Test
    void testAssignTask_success_shouldAssignTaskToNodes() {
        // given
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
        when(taskInfoMetadataRepository.updateTaskConfigById(eq(TASK_ID), any(TaskConfig.class)))
                .thenReturn(Mono.empty());

        // when
        ApiResponse<TaskConfig> result = taskManager.assignTask(TASK_ID, request).block();

        // then
        assertTrue(result.isSuccess());
    }

    @Test
    void testAssignTask_taskNotFound_shouldReturnError() {
        // given
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.empty());
        NodeTaskAllocationRequest request = new NodeTaskAllocationRequest();

        // when
        ApiResponse<TaskConfig> result = taskManager.assignTask(TASK_ID, request).block();

        // then
        assertFalse(result.isSuccess());
        assertEquals("任务不存在", result.getMessage());
    }

    // ==================== 测试: calculateNodeTaskAllocation ====================

    @Test
    void testCalculateNodeTaskAllocation_success_shouldCalculateAllocation() {
        // given
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

        // when
        ApiResponse<NodeTaskAllocationVO> result = taskManager.calculateNodeTaskAllocation(TASK_ID).block();

        // then
        assertTrue(result.isSuccess());
    }

    @Test
    void testCalculateNodeTaskAllocation_taskNotFound_shouldReturnError() {
        // given
        when(taskInfoMetadataRepository.findById(TASK_ID)).thenReturn(Mono.empty());

        // when
        ApiResponse<NodeTaskAllocationVO> result = taskManager.calculateNodeTaskAllocation(TASK_ID).block();

        // then
        assertFalse(result.isSuccess());
        assertEquals("任务不存在", result.getMessage());
    }

    // ==================== 辅助方法 ====================

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

    // ==================== 测试: getAllTask with group ====================

    @Test
    void testGetAllTask_withGroupFilter_shouldCallServiceWithGroup() {
        // given
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskInfoMetadata> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(taskInfoMetadataService.findByFilters(anyString(), anyString(), eq(GROUP_ID), any(Pageable.class)))
                .thenReturn(Mono.just(page));

        // when
        Page<TaskInfoMetadata> result = taskManager.getAllTask("Test Task", "CONN", GROUP_ID, pageable).block();

        // then
        assertNotNull(result);
        verify(taskInfoMetadataService).findByFilters(eq("Test Task"), eq("CONN"), eq(GROUP_ID), eq(pageable));
    }

    @Test
    void testGetAllTask_withOnlyGroupFilter_shouldCallServiceWithGroup() {
        // given
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskInfoMetadata> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(taskInfoMetadataService.findByFilters(isNull(), isNull(), eq(GROUP_ID), any(Pageable.class)))
                .thenReturn(Mono.just(page));

        // when
        Page<TaskInfoMetadata> result = taskManager.getAllTask(null, null, GROUP_ID, pageable).block();

        // then
        assertNotNull(result);
        verify(taskInfoMetadataService).findByFilters(isNull(), isNull(), eq(GROUP_ID), eq(pageable));
    }

    @Test
    void testGetAllTask_withTaskNameAndTaskTypeAndGroup_shouldCallServiceWithAllFilters() {
        // given
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskInfoMetadata> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(taskInfoMetadataService.findByFilters(anyString(), anyString(), anyString(), any(Pageable.class)))
                .thenReturn(Mono.just(page));

        // when
        Page<TaskInfoMetadata> result = taskManager.getAllTask("Test", "PUBSUB", GROUP_ID, pageable).block();

        // then
        assertNotNull(result);
        verify(taskInfoMetadataService).findByFilters(eq("Test"), eq("PUBSUB"), eq(GROUP_ID), eq(pageable));
    }

    @Test
    void testGetAllTask_withEmptyFilters_shouldCallFindAll() {
        // given
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskInfoMetadata> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(taskInfoMetadataService.findAll(any(Pageable.class))).thenReturn(Mono.just(page));

        // when
        Page<TaskInfoMetadata> result = taskManager.getAllTask(pageable).block();

        // then
        assertNotNull(result);
        verify(taskInfoMetadataService).findAll(eq(pageable));
    }

    @Test
    void testGetAllTask_withTaskNameAndTypeOnly_shouldCallFindByFilters() {
        // given
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskInfoMetadata> page = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(taskInfoMetadataService.findByFilters(anyString(), anyString(), isNull(), any(Pageable.class)))
                .thenReturn(Mono.just(page));

        // when
        Page<TaskInfoMetadata> result = taskManager.getAllTask("Test", "CONN", null, pageable).block();

        // then
        assertNotNull(result);
        verify(taskInfoMetadataService).findByFilters(eq("Test"), eq("CONN"), isNull(), eq(pageable));
    }
}
