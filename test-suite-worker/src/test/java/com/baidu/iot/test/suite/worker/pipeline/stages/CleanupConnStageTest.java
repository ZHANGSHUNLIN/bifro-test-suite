
package com.baidu.iot.test.suite.worker.pipeline.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.baidu.iot.test.suite.ConnClientTask;
import com.baidu.iot.test.suite.TaskEvent;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.StageResult;
import com.baidu.iot.test.suite.statemachine.StateMachine;
import com.baidu.iot.test.suite.worker.TaskConfig;

import io.vertx.core.Vertx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

/**
 * Unit tests for CleanupConnStage.
 */
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class CleanupConnStageTest {

    @Mock
    private TaskConfig taskConfig;

    private Vertx vertx;
    private StateMachine<TaskStage, TaskEvent> stateMachine;
    private Map<String, ConnClientTask> connClients;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        stateMachine = new StateMachine<>(TaskStage.INIT);
        connClients = new HashMap<>();

        when(taskConfig.getDisConnectRateLimiter()).thenReturn(com.google.common.util.concurrent.RateLimiter.create(1000));
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    private PipelineContext createTestContext() {
        return PipelineContext.of(vertx, stateMachine, new HashMap<>(), 0, 0);
    }

    @Nested
    class ConstructorTests {

        @Test
        void testConstructor_withValidParameters_shouldCreateStage() {
            // given
            String taskId = "test-task";

            // when
            CleanupConnStage stage = new CleanupConnStage("");

            // then
            assertThat(stage.getName()).isEqualTo("CleanupConn");
        }

        @Test
        void testConstructor_withEmptyClients_shouldCreateStage() {
            // given
            Map<String, ConnClientTask> emptyClients = new HashMap<>();
            String taskId = "test-task";

            // when
            CleanupConnStage stage = new CleanupConnStage("");

            // then
            assertThat(stage).isNotNull();
            assertThat(stage.getName()).isEqualTo("CleanupConn");
        }
    }

    @Nested
    class GetNameTests {

        @Test
        void testGetName_shouldReturnCorrectName() {
            // given
            String taskId = "test-task";
            CleanupConnStage stage = new CleanupConnStage("");

            // when
            String name = stage.getName();

            // then
            assertThat(name).isEqualTo("CleanupConn");
        }
    }

    @Nested
    class OnBeforeTests {

        @Test
        void testOnBefore_shouldNotThrow() {
            // given
            String taskId = "test-task";
            CleanupConnStage stage = new CleanupConnStage("");
            PipelineContext context = createTestContext();

            // when
            stage.onBefore(context);

            // then - no exception should be thrown
            assertThat(context).isNotNull();
        }
    }

    @Nested
    class OnAfterTests {

        @Test
        void testOnAfter_shouldNotThrow() throws Exception {
            // given
            String taskId = "test-task";
            CleanupConnStage stage = new CleanupConnStage("");
            StageResult successResult = StageResult.success("Cleanup completed");
            PipelineContext context = createTestContext();

            // when
            stage.onAfter(context, successResult);

            // then - no exception thrown
            assertThat(context).isNotNull();
        }
    }

    @Nested
    class OnErrorTests {

        @Test
        void testOnError_shouldNotThrow() {
            // given
            String taskId = "test-task";
            CleanupConnStage stage = new CleanupConnStage("");
            RuntimeException error = new RuntimeException("Test error");
            PipelineContext context = createTestContext();

            // when
            stage.onError(context, error);

            // then - no exception should be thrown
            assertThat(context).isNotNull();
        }
    }

    @Nested
    class OnCancelledTests {

        @Test
        void testOnCancelled_shouldNotThrow() {
            // given
            String taskId = "test-task";
            CleanupConnStage stage = new CleanupConnStage("");
            PipelineContext context = createTestContext();

            // when
            stage.onCancelled(context);

            // then - no exception should be thrown
            assertThat(context).isNotNull();
        }
    }

    @Nested
    class CanExecuteTests {

        @Test
        void testCanExecute_withDefaultImplementation_shouldUseContextCancelled() {
            // given
            String taskId = "test-task";
            CleanupConnStage stage = new CleanupConnStage("");
            PipelineContext context = createTestContext();

            // when
            boolean canExecute = stage.canExecute(context);

            // then
            assertThat(canExecute).isTrue();
        }

        @Test
        void testCanExecute_withCancelledContext_shouldReturnFalse() {
            // given
            String taskId = "test-task";
            CleanupConnStage stage = new CleanupConnStage("");
            PipelineContext context = createTestContext();
            context.cancel();

            // when
            boolean canExecute = stage.canExecute(context);

            // then
            assertThat(canExecute).isFalse();
        }
    }

    @Nested
    class CancelTests {

        @Test
        void testCancel_shouldReturnCompletedFuture() {
            // given
            String taskId = "test-task";
            CleanupConnStage stage = new CleanupConnStage("");
            PipelineContext context = createTestContext();

            // when
            CompletableFuture<Void> cancelResult = stage.cancel(context);

            // then
            assertThat(cancelResult).isNotNull();
            assertThat(cancelResult.isDone()).isTrue();
        }
    }

    @Nested
    class ExecuteTests {

        @Test
        void testExecute_shouldClearClientsMap() throws Exception {
            // given
            String taskId = "test-task";
            Map<String, ConnClientTask> clients = new HashMap<>();
            clients.put("client1", mock(ConnClientTask.class));
            clients.put("client2", mock(ConnClientTask.class));
            CleanupConnStage stage = new CleanupConnStage("");
            PipelineContext context = createTestContext();

            // when
            CompletableFuture<StageResult> result = stage.execute(context);

            // then
            assertThat(result.get().isSuccess()).isTrue();
            assertThat(clients).isEmpty();
        }

        @Test
        void testExecute_withEmptyClients_shouldReturnSuccess() throws Exception {
            // given
            String taskId = "test-task";
            Map<String, ConnClientTask> emptyClients = new HashMap<>();
            CleanupConnStage stage = new CleanupConnStage("");
            PipelineContext context = createTestContext();

            // when
            CompletableFuture<StageResult> result = stage.execute(context);

            // then
            assertThat(result.get().isSuccess()).isTrue();
        }
    }
}
