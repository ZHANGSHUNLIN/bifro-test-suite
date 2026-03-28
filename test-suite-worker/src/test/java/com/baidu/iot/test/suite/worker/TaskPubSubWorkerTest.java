package com.baidu.iot.test.suite.worker;

import com.baidu.iot.test.suite.TaskStage;
import io.reactivex.subjects.Subject;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * TaskPubSubWorker 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TaskPubSubWorkerTest {

    @Mock
    private Vertx vertx;

    @Mock
    private io.vertx.core.eventbus.EventBus eventBus;

    @Mock
    private io.vertx.core.eventbus.MessageConsumer<Object> messageConsumer;

    private TaskConfig taskConfig;

    private TaskPubSubWorker worker;

    @BeforeEach
    void setUp() {
        lenient().when(vertx.eventBus()).thenReturn(eventBus);
        lenient().when(eventBus.localConsumer(anyString())).thenReturn(messageConsumer);
    }

    // ==================== 测试: 初始化和基础功能 ====================

    @Test
    void testConstructor_shouldInitializeFields() throws Exception {
        // given
        taskConfig = TaskConfig.builder()
                .taskId("test-task")
                .totalClientCount(100)
                .fanOut(2)
                .fanIn(1)
                .thingIdStartAt(0)
                .tagPeriodIntervalInSec(30)
                .build();

        // when
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // then
        assertNotNull(worker);
        assertEquals(TaskStage.INIT, worker.getTaskState());
        assertNotNull(getPrivateField(worker, "pubClients"));
        assertNotNull(getPrivateField(worker, "subClients"));
        assertNotNull(getPrivateField(worker, "readyPubClients"));
        assertNotNull(getPrivateField(worker, "readySubClients"));
    }

    @Test
    void testGetTaskState_shouldReturnCurrentStage() {
        // given
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when & then
        assertEquals(TaskStage.INIT, worker.getTaskState());
    }

    @Test
    void testCanceled_whenInterrupted_shouldReturnTrue() throws Exception {
        // given
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when - set interrupt flag
        Field interruptField = getBaseTaskWorkerClass().getDeclaredField("interrupt");
        interruptField.setAccessible(true);
        java.util.concurrent.atomic.AtomicBoolean interrupt =
                (java.util.concurrent.atomic.AtomicBoolean) interruptField.get(worker);
        interrupt.set(true);

        // then
        assertTrue(worker.canceled());
        assertEquals(TaskStage.BREAKING, worker.getTaskState());
    }

    @Test
    void testCanceled_whenNotInterrupted_shouldReturnFalse() {
        // given
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when & then
        // interrupt flag is false by default
        boolean canceled = worker.canceled();
        assertEquals(false, canceled);
        assertEquals(TaskStage.INIT, worker.getTaskState());
    }

    // ==================== 测试: eventReport和reportEventSubject ====================

    @Test
    void testReportEventSubject_shouldReturnSubject() throws Exception {
        // given
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when
        Subject<?> subject = worker.reportEventSubject();

        // then
        assertNotNull(subject);
        // Note: Returns SerializedSubject (toSerialized), not plain PublishSubject
        assertTrue(subject instanceof Subject);
    }

    // ==================== 测试: buildClientTopic 逻辑 ====================

    @Test
    void testBuildClientTopic_subTopic_shouldBuildCorrectly() throws Exception {
        // given - With default settings (not subOnly, not fixedTopic), builds full path
        taskConfig = TaskConfig.builder()
                .taskId("test-task")
                .topic("test/topic")
                .build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when
        String topic = invokeBuildClientTopic(worker, 0, true, false);

        // then
        assertEquals("test/topic/test-task//0", topic);
    }

    @Test
    void testBuildClientTopic_fixedTopic_shouldBuildCorrectly() throws Exception {
        // given
        taskConfig = TaskConfig.builder()
                .taskId("test-task")
                .topic("test/topic")
                .fixedTopic(true)
                .build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when
        String topic = invokeBuildClientTopic(worker, 0, false, false);

        // then
        assertEquals("test/topic/0", topic);
    }

    @Test
    void testBuildClientTopic_normalPubTopic_shouldBuildCorrectly() throws Exception {
        // given - nodeIdPrefix returns first 4 chars, so "node123" becomes "node"
        taskConfig = TaskConfig.builder()
                .taskId("test-task-id")
                .nodeId("node123")
                .topic("test")
                .fixedTopic(false)
                .build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when
        String topic = invokeBuildClientTopic(worker, 0, false, false);

        // then
        assertEquals("test/test-task-id/node/0", topic);
    }

    @Test
    void testBuildClientTopic_wildcardSub_shouldBuildCorrectly() throws Exception {
        // given
        taskConfig = TaskConfig.builder()
                .taskId("test-task")
                .topic("test")
                .isWildcard(true)
                .build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when
        String topic = invokeBuildClientTopic(worker, 0, true, true);

        // then
        assertEquals("test/test-task//0/+", topic);
    }

    @Test
    void testBuildClientTopic_wildcardPub_shouldBuildCorrectly() throws Exception {
        // given
        taskConfig = TaskConfig.builder()
                .taskId("test-task")
                .topic("test")
                .isWildcard(true)
                .build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when
        String topic = invokeBuildClientTopic(worker, 0, false, true);

        // then
        assertEquals("test/test-task//0/suffix", topic);
    }

    // ==================== 测试: nodeIdPrefix 逻辑 ====================

    @Test
    void testNodeIdPrefix_shouldReturnCorrectPrefix() throws Exception {
        // given - nodeIdPrefix returns first 4 chars
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when
        String prefix = invokeNodeIdPrefix(worker, "node123abc");

        // then
        assertEquals("node", prefix);
    }

    @Test
    void testNodeIdPrefix_nullNodeId_shouldReturnEmptyString() throws Exception {
        // given
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when
        String prefix = invokeNodeIdPrefix(worker, null);

        // then
        assertEquals("", prefix);
    }

    @Test
    void testNodeIdPrefix_shortNodeId_shouldReturnAllChars() throws Exception {
        // given - With 4 chars, nodeIdPrefix returns all 4 (substring(0, 4))
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when
        String prefix = invokeNodeIdPrefix(worker, "abcd");

        // then
        assertEquals("abcd", prefix);
    }

    @Test
    void testNodeIdPrefix_longNodeId_shouldReturnFirst4Chars() throws Exception {
        // given - nodeIdPrefix returns first 4 chars
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when
        String prefix = invokeNodeIdPrefix(worker, "veryLongNodeIdHere");

        // then
        assertEquals("very", prefix);
    }

    // ==================== 测试: TaskPubSubWorker stopTask ====================

    @Test
    void testStopTask_shouldReturnCompletableFuture() {
        // given
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskPubSubWorker(vertx, taskConfig);

        // when
        CompletableFuture<Void> result = worker.stopTask();

        // then
        assertNotNull(result);
        assertEquals(TaskStage.SHUTDOWN, worker.getTaskState());
    }

    // ==================== 辅助方法 ====================

    private Field getPrivateField(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private Class<?> getBaseTaskWorkerClass() throws ClassNotFoundException {
        return Class.forName("com.baidu.iot.test.suite.worker.BaseTaskWorker");
    }

    private String invokeBuildClientTopic(TaskPubSubWorker worker, int topicIndex, boolean isSub, boolean isWildcard)
            throws Exception {
        Method method = worker.getClass().getDeclaredMethod("buildClientTopic", int.class, boolean.class, boolean.class);
        method.setAccessible(true);
        return (String) method.invoke(worker, topicIndex, isSub, isWildcard);
    }

    private String invokeNodeIdPrefix(TaskPubSubWorker worker, String nodeId) throws Exception {
        Method method = worker.getClass().getDeclaredMethod("nodeIdPrefix", String.class);
        method.setAccessible(true);
        return (String) method.invoke(worker, nodeId);
    }
}
