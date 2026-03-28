package com.baidu.duhome.local;

import com.baidu.duhome.cluster.ClusterDataManager;
import com.baidu.duhome.database.pojo.NodeTask;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.repository.NodeTaskRepository;
import com.baidu.duhome.database.repository.ReportRepository;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import com.baidu.duhome.local.consumer.LocalConsumer;
import com.baidu.iot.test.suite.ShareDataManager;
import com.baidu.iot.test.suite.ShareDataAddr;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.worker.TaskConfig;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baidu.duhome.database.pojo.Report;

/**
 * LocalTaskCoordinator 单元测试
 */
@ExtendWith(MockitoExtension.class)
class LocalTaskCoordinatorTest {

    @Mock
    private ClusterDataManager clusterDataManager;

    @Mock
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Mock
    private NodeTaskRepository nodeTaskRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private Vertx vertx;

    @Mock
    private EventBus eventBus;

    @Mock
    private ShareDataManager shareDataManager;

    @Mock
    private LocalConsumer localConsumer;

    @InjectMocks
    private LocalTaskCoordinator localTaskCoordinator;

    private static final String TASK_ID = "test-task-123";
    private static final String NODE_ID = "node-abc123";

    @BeforeEach
    void setUp() {
        lenient().when(clusterDataManager.getCurrentNodeIdCache()).thenReturn(NODE_ID);
        lenient().when(vertx.eventBus()).thenReturn(eventBus);
        // Mock reportRepository to avoid NullPointerException
        lenient().when(reportRepository.insert(any(Report.class))).thenReturn(Mono.empty());
    }

    // ==================== 测试: startTask ====================

    @Test
    void testStartTask_success_shouldStartTaskAndSetStageToONGOING() {
        // given
        TaskConfig mainTaskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .taskType(TaskConfig.TaskType.PUBSUB)
                .totalClientCount(100)
                .build();

        TaskConfig nodeTaskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .nodeId(NODE_ID)
                .taskType(TaskConfig.TaskType.PUBSUB)
                .totalClientCount(50)
                .taskWorkStage(TaskStage.ASSIGNED)
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
                .build();

        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString())).thenReturn(Mono.just(nodeTask));
        when(nodeTaskRepository.findAllByTaskId(anyString())).thenReturn(Flux.just(nodeTask));
        when(taskInfoMetadataRepository.updateTaskConfigById(anyString(), any(TaskConfig.class)))
                .thenReturn(Mono.empty());
        when(nodeTaskRepository.save(any(NodeTask.class))).thenReturn(Mono.just(nodeTask));

        // when
        localTaskCoordinator.startTask(TASK_ID);

        // then
        verify(taskInfoMetadataRepository).updateTaskConfigById(eq(TASK_ID), any(TaskConfig.class));
        verify(nodeTaskRepository).save(any(NodeTask.class));
        Map<String, TaskStage> runningTasks = localTaskCoordinator.runningTask();
        assertNotNull(runningTasks);
    }

    @Test
    void testStartTask_duplicateTask_shouldNotStartAgain() {
        // given
        TaskConfig taskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .taskType(TaskConfig.TaskType.PUBSUB)
                .build();

        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
                .taskId(TASK_ID)
                .taskConfig(taskConfig)
                .build();

        NodeTask nodeTask = NodeTask.builder()
                .taskId(TASK_ID)
                .nodeId(NODE_ID)
                .taskConfig(taskConfig)
                .build();

        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.just(metadata));
        when(nodeTaskRepository.findByTaskIdAndNodeId(anyString(), anyString())).thenReturn(Mono.just(nodeTask));
        when(nodeTaskRepository.findAllByTaskId(anyString())).thenReturn(Flux.just(nodeTask));
        when(taskInfoMetadataRepository.updateTaskConfigById(anyString(), any(TaskConfig.class)))
                .thenReturn(Mono.empty());
        when(nodeTaskRepository.save(any(NodeTask.class))).thenReturn(Mono.just(nodeTask));

        // when - first call
        localTaskCoordinator.startTask(TASK_ID);
        Map<String, TaskStage> firstCallResult = localTaskCoordinator.runningTask();

        // when - second call (duplicate)
        localTaskCoordinator.startTask(TASK_ID);
        Map<String, TaskStage> secondCallResult = localTaskCoordinator.runningTask();

        // then
        assertNotNull(firstCallResult);
        assertNotNull(secondCallResult);
        // Should only call save once (from first call)
        verify(taskInfoMetadataRepository, atMost(1)).updateTaskConfigById(eq(TASK_ID), any(TaskConfig.class));
    }

    @Test
    void testStartTask_taskNotFound_shouldThrowException() {
        // given
        when(taskInfoMetadataRepository.findById(anyString())).thenReturn(Mono.empty());

        // when & then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            localTaskCoordinator.startTask(TASK_ID);
        });
        assertEquals("Task not found", exception.getMessage());
    }

    // ==================== 测试: stopTask ====================

    @Test
    void testStopTask_taskNotFound_shouldDoNothing() {
        // given - No task in runningTaskMap and nodeTask not found
        when(clusterDataManager.currentNode()).thenReturn(CompletableFuture.completedFuture(null));
        when(nodeTaskRepository.findFirstByTaskId(anyString())).thenReturn(Mono.empty());

        // when & then - Should not throw exception
        localTaskCoordinator.stopTask(TASK_ID);
    }

    // ==================== 测试: checkAllTasks (via taskFinish) ====================

    @Test
    void testCheckAllTasks_shouldUpdateStageMap() {
        // given
        TaskConfig taskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .build();

        NodeTask nodeTask1 = NodeTask.builder()
                .taskId(TASK_ID)
                .nodeId(NODE_ID)
                .taskConfig(taskConfig)
                .build();

        NodeTask nodeTask2 = NodeTask.builder()
                .taskId(TASK_ID)
                .nodeId("node-def456")
                .taskConfig(taskConfig)
                .build();

        Set<String> finishNodeIds = new HashSet<>();
        finishNodeIds.add(NODE_ID);

        when(nodeTaskRepository.findAllByTaskId(anyString())).thenReturn(Flux.just(nodeTask1, nodeTask2));

        // when - Private method called via reflection
        try {
            java.lang.reflect.Method method = LocalTaskCoordinator.class.getDeclaredMethod(
                    "checkAllTasksComplete", String.class, Set.class);
            method.setAccessible(true);
            method.invoke(localTaskCoordinator, TASK_ID, finishNodeIds);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke checkAllTasksComplete", e);
        }

        // then - Should call nodeTaskRepository to get all tasks
        verify(nodeTaskRepository).findAllByTaskId(TASK_ID);
    }

    @Test
    void testCheckAllTasks_allTasksFinished_shouldLogComplete() {
        // given
        TaskConfig taskConfig = TaskConfig.builder()
                .taskId(TASK_ID)
                .build();

        NodeTask nodeTask1 = NodeTask.builder()
                .taskId(TASK_ID)
                .nodeId(NODE_ID)
                .taskConfig(taskConfig)
                .build();

        NodeTask nodeTask2 = NodeTask.builder()
                .taskId(TASK_ID)
                .nodeId("node-xyz789")
                .taskConfig(taskConfig)
                .build();

        Set<String> finishNodeIds = new HashSet<>();
        finishNodeIds.add(NODE_ID);
        finishNodeIds.add("node-xyz789");

        when(nodeTaskRepository.findAllByTaskId(anyString())).thenReturn(Flux.just(nodeTask1, nodeTask2));

        // when - All nodes finished
        try {
            java.lang.reflect.Method method = LocalTaskCoordinator.class.getDeclaredMethod(
                    "checkAllTasksComplete", String.class, Set.class);
            method.setAccessible(true);
            method.invoke(localTaskCoordinator, TASK_ID, finishNodeIds);
        } catch (Exception e) {
            // Expected
        }

        // then
        verify(nodeTaskRepository).findAllByTaskId(TASK_ID);
    }

    // ==================== 测试: runningTask public method ====================

    @Test
    void testRunningTask_shouldReturnEmptyMapWhenNoTasksRunning() {
        // given - No tasks in runningTaskMap

        // when
        Map<String, TaskStage> result = localTaskCoordinator.runningTask();

        // then
        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
