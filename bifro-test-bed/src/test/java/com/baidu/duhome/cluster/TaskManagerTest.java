package com.baidu.duhome.cluster;

import com.baidu.duhome.cluster.task.DefaultWeightCalculation;
import com.baidu.iot.test.suite.worker.TaskConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

@ExtendWith(MockitoExtension.class)
class TaskManagerTest {

    @InjectMocks
    private TaskManager taskManager;

    @Mock
    private DefaultWeightCalculation defaultWeightCalculation;

    private TaskConfig mainTaskConfig;

    @BeforeEach
    void setUp() {
        // 创建主任务配置
        mainTaskConfig = TaskConfig.builder()
                .taskId("main-task-123")
                .taskType(TaskConfig.TaskType.CONN)
                .protocol("tcp")
                .brokers(new ArrayList<>())
                .totalClientCount(100)
                .thingIdStartAt(1000)
                .build();
    }

//
//    @Test
//    void testCreateTaskConfig_WithValidInputs() {
//        // Given
//        String nodeId = "node-1";
//        int nodeClientCount = 10;
//
//        // When
//        TaskConfig result = taskManager.createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(mainTaskConfig.getTaskId(), result.getTaskId());
//        assertEquals(nodeId, result.getNodeId());
//        assertEquals(nodeClientCount, result.getTotalClientCount());
//        // Verify it's a new instance, not the same object
//        assertNotSame(mainTaskConfig, result);
//    }
//
//    @Test
//    void testCreateTaskConfig_WithEmptyNodeId() {
//        // Given
//        String nodeId = "";
//        int nodeClientCount = 5;
//
//        // When
//        TaskConfig result = taskManager.createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(mainTaskConfig.getTaskId(), result.getTaskId());
//        assertEquals("", result.getNodeId());
//        assertEquals(nodeClientCount, result.getTotalClientCount());
//    }
//
//    @Test
//    void testCreateTaskConfig_WithNullNodeId() {
//        // Given
//        String nodeId = null;
//        int nodeClientCount = 5;
//
//        // When
//        TaskConfig result = taskManager.createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(mainTaskConfig.getTaskId(), result.getTaskId());
//        assertEquals(null, result.getNodeId());
//        assertEquals(nodeClientCount, result.getTotalClientCount());
//    }
//
//    @Test
//    void testCreateTaskConfig_WithZeroClientCount() {
//        // Given
//        String nodeId = "node-1";
//        int nodeClientCount = 0;
//
//        // When
//        TaskConfig result = taskManager.createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(mainTaskConfig.getTaskId(), result.getTaskId());
//        assertEquals(nodeId, result.getNodeId());
//        assertEquals(0, result.getTotalClientCount());
//    }
//
//    @Test
//    void testCreateTaskConfig_WithNegativeClientCount() {
//        // Given
//        String nodeId = "node-1";
//        int nodeClientCount = -5;
//
//        // When
//        TaskConfig result = taskManager.createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(mainTaskConfig.getTaskId(), result.getTaskId());
//        assertEquals(nodeId, result.getNodeId());
//        assertEquals(-5, result.getTotalClientCount());
//    }
//
//    @Test
//    void testCreateTaskConfig_WithMaximumClientCount() {
//        // Given
//        String nodeId = "node-1";
//        int nodeClientCount = Integer.MAX_VALUE;
//
//        // When
//        TaskConfig result = taskManager.createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(mainTaskConfig.getTaskId(), result.getTaskId());
//        assertEquals(nodeId, result.getNodeId());
//        assertEquals(Integer.MAX_VALUE, result.getTotalClientCount());
//    }
//
//    @Test
//    void testCreateTaskConfig_WithMinimumClientCount() {
//        // Given
//        String nodeId = "node-1";
//        int nodeClientCount = Integer.MIN_VALUE;
//
//        // When
//        TaskConfig result = taskManager.createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(mainTaskConfig.getTaskId(), result.getTaskId());
//        assertEquals(nodeId, result.getNodeId());
//        assertEquals(Integer.MIN_VALUE, result.getTotalClientCount());
//    }
//
//    @Test
//    void testCreateTaskConfig_VerifyAllPropertiesCopied() {
//        // Given
//        String nodeId = "node-1";
//        int nodeClientCount = 15;
//
//        // 设置更多的属性来验证拷贝是否完整
//        mainTaskConfig.setUsername("test-user");
//        mainTaskConfig.setPassword("test-pass");
//        mainTaskConfig.setThingIdPrefix("test-prefix");
//
//        // When
//        TaskConfig result = taskManager.createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(mainTaskConfig.getTaskId(), result.getTaskId());
//        assertEquals(nodeId, result.getNodeId());
//        assertEquals(nodeClientCount, result.getTotalClientCount());
//        assertEquals("test-user", result.getUsername());
//        assertEquals("test-pass", result.getPassword());
//        assertEquals("test-prefix", result.getThingIdPrefix());
//    }
//
//    @Test
//    void testCreateTaskConfig_VerifyNewInstanceCreation() {
//        // Given
//        String nodeId = "node-1";
//        int nodeClientCount = 20;
//
//        // When
//        TaskConfig result1 = taskManager.createTaskConfig(mainTaskConfig, nodeId, nodeClientCount);
//        TaskConfig result2 = taskManager.createTaskConfig(mainTaskConfig, "node-2", 30);
//
//        // Then
//        assertNotNull(result1);
//        assertNotNull(result2);
//        assertNotSame(result1, result2);
//        assertEquals(mainTaskConfig.getTaskId(), result1.getTaskId());
//        assertEquals(mainTaskConfig.getTaskId(), result2.getTaskId());
//        assertEquals("node-1", result1.getNodeId());
//        assertEquals("node-2", result2.getNodeId());
//        assertEquals(20, result1.getTotalClientCount());
//        assertEquals(30, result2.getTotalClientCount());
//    }
}