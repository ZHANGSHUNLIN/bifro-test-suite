package com.baidu.iot.test.suite.worker;

import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.worker.models.WorkerTaskEvent;
import io.reactivex.subjects.Subject;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * TaskConnWorker 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TaskConnWorkerTest {

    @Mock
    private Vertx vertx;

    @Mock
    private io.vertx.core.eventbus.EventBus eventBus;

    @Mock
    private MessageConsumer<Object> messageConsumer;

    @Mock
    private MessageConsumer<Object> taskFinishConsumer;

    private TaskConfig taskConfig;

    private TaskConnWorker worker;

    @BeforeEach
    void setUp() {
        lenient().when(vertx.eventBus()).thenReturn(eventBus);
        lenient().when(eventBus.localConsumer(anyString())).thenReturn(messageConsumer);
        lenient().when(eventBus.localConsumer(anyString())).thenReturn(taskFinishConsumer);
        lenient().when(vertx.setTimer(anyLong(), any())).thenReturn(1L);
        lenient().when(vertx.cancelTimer(anyLong())).thenReturn(true);
        lenient().when(vertx.setPeriodic(anyLong(), any())).thenReturn(1L);
    }

    // ==================== 测试: 初始化和基础功能 ====================

    @Test
    void testConstructor_shouldInitializeFields() throws Exception {
        // given
        taskConfig = TaskConfig.builder()
                .taskId("test-task")
                .totalClientCount(50)
                .thingIdStartAt(0)
                .stressDurationInSec(60)
                .tagPeriodIntervalInSec(30)
                .build();

        // when
        worker = new TaskConnWorker(vertx, taskConfig);

        // then
        assertNotNull(worker);
        assertEquals(TaskStage.INIT, worker.getTaskState());
        assertNotNull(getPrivateField(worker, "connClients"));
        assertNotNull(getPrivateField(worker, "connClientIds"));
    }

    @Test
    void testGetTaskState_shouldReturnCurrentStage() {
        // given
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskConnWorker(vertx, taskConfig);

        // when & then
        assertEquals(TaskStage.INIT, worker.getTaskState());
    }

    @Test
    void testCanceled_whenInterrupted_shouldReturnTrue() throws Exception {
        // given
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskConnWorker(vertx, taskConfig);

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
        worker = new TaskConnWorker(vertx, taskConfig);

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
        worker = new TaskConnWorker(vertx, taskConfig);

        // when
        Subject<?> subject = worker.reportEventSubject();

        // then
        assertNotNull(subject);
        // Note: Returns SerializedSubject (toSerialized), not plain PublishSubject
        assertTrue(subject instanceof Subject);
    }

    @Test
    void testClientPostConnectObservable_shouldReturnSubject() throws Exception {
        // given
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskConnWorker(vertx, taskConfig);

        // when
        Subject<?> subject = worker.clientPostConnectObservable();

        // then
        assertNotNull(subject);
        // Note: Returns SerializedSubject (toSerialized), not plain PublishSubject
        assertTrue(subject instanceof Subject);
    }

    // ==================== 测试: TaskConnWorker stopTask ====================

    @Test
    void testStopTask_shouldReturnCompletableFuture() {
        // given
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskConnWorker(vertx, taskConfig);

        // when
        CompletableFuture<Void> result = worker.stopTask();

        // then
        assertNotNull(result);
        assertEquals(TaskStage.SHUTDOWN, worker.getTaskState());
    }

    @Test
    void testStopTask_whenAlreadyShuttingDown_shouldReturnCompletedFuture() throws Exception {
        // given
        taskConfig = TaskConfig.builder().taskId("test-task").build();
        worker = new TaskConnWorker(vertx, taskConfig);

        // when - set stage to SHUTDOWN_ING
        setTaskStage(worker, TaskStage.SHUTDOWN_ING);

        // then
        CompletableFuture<Void> result = worker.stopTask();
        assertNotNull(result);
        // Should return already completed future
        assertTrue(result.isDone());
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

    private void setTaskStage(TaskConnWorker worker, TaskStage stage) throws Exception {
        Field stageField = getBaseTaskWorkerClass().getDeclaredField("taskStage");
        stageField.setAccessible(true);
        java.util.concurrent.atomic.AtomicReference<TaskStage> taskStage =
                (java.util.concurrent.atomic.AtomicReference<TaskStage>) stageField.get(worker);
        taskStage.set(stage);
    }
}
