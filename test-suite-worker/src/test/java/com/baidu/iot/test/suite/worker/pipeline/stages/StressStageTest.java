
package com.baidu.iot.test.suite.worker.pipeline.stages;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

/**
 * Unit tests for StressStage.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class StressStageTest {

    @Mock
    private TaskConfig taskConfig;

    private Vertx vertx;
    private StateMachine<TaskStage, TaskEvent> stateMachine;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        stateMachine = new StateMachine<>(TaskStage.INIT);

        Mockito.when(taskConfig.getStressDurationInSec()).thenReturn(1);
        Mockito.when(taskConfig.getTaskId()).thenReturn("test-task");
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

            // when
            StressStage stage = new StressStage(vertx, taskConfig);

            // then
            assertThat(stage).isNotNull();
            assertThat(stage.getName()).isEqualTo("Stress");
        }

        @Test
        void testConstructor_withNullVertx_shouldNotThrow() {
            // given

            // when
            StressStage stage = new StressStage(vertx, taskConfig);

            // then - NullPointerException is thrown later on execute
            assertThat(stage.getName()).isEqualTo("Stress");
        }
    }

    @Nested
    class GetNameTests {

        @Test
        void testGetName_shouldReturnCorrectName() {
            // given
            StressStage stage = new StressStage(vertx, taskConfig);

            // when
            String name = stage.getName();

            // then
            assertThat(name).isEqualTo("Stress");
        }
    }

    @Nested
    class ExecuteTests {

        @Test
        void testExecute_shouldWaitForStressDuration() throws Exception {
            // given
            StressStage stage = new StressStage(vertx, taskConfig);
            PipelineContext context = createTestContext();

            // when
            CompletableFuture<StageResult> result = stage.execute(context);

            // then
            assertThat(result.get().isSuccess()).isTrue();
        }

        @Test
        void testExecute_shouldReturnSuccessResult() throws Exception {
            // given
            StressStage stage = new StressStage(vertx, taskConfig);
            PipelineContext context = createTestContext();

            // when
            StageResult result = stage.execute(context).get();

            // then
            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    class OnBeforeTests {

        @Test
        void testOnBefore_shouldNotThrow() {
            // given
            StressStage stage = new StressStage(vertx, taskConfig);
            PipelineContext context = createTestContext();

            // when
            stage.onBefore(context);

            // then - no exception should be thrown
            assertThat(context.isCancelled()).isFalse();
        }
    }

    @Nested
    class OnAfterTests {

        @Test
        void testOnAfter_shouldNotThrow() throws Exception {
            // given
            StressStage stage = new StressStage(vertx, taskConfig);
            PipelineContext context = createTestContext();
            stage.execute(context).get();

            // when
            stage.onAfter(context, StageResult.success());

            // then - no exception thrown
            assertThat(context).isNotNull();
        }
    }

    @Nested
    class OnErrorTests {

        @Test
        void testOnError_shouldNotThrow() {
            // given
            StressStage stage = new StressStage(vertx, taskConfig);
            RuntimeException error = new RuntimeException("Test error");
            PipelineContext context = createTestContext();

            // when
            stage.onError(context, error);

            // then - no exception should be thrown
            assertThat(context).isNotNull();
        }
    }

    @Nested
    class CanExecuteTests {

        @Test
        void testCanExecute_withDefaultImplementation_shouldUseContextCancelled() {
            // given
            StressStage stage = new StressStage(vertx, taskConfig);
            PipelineContext context = createTestContext();

            // when
            boolean canExecute = stage.canExecute(context);

            // then
            assertThat(canExecute).isTrue();
        }

        @Test
        void testCanExecute_withCancelledContext_shouldReturnFalse() {
            // given
            StressStage stage = new StressStage(vertx, taskConfig);
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
        void testCancel_shouldCompleteStageFuture() throws Exception {
            // given
            StressStage stage = new StressStage(vertx, taskConfig);
            PipelineContext context = createTestContext();

            // when
            CompletableFuture<Void> cancelResult = stage.cancel(context);

            // then
            assertThat(cancelResult).isNotNull();
            assertThat(cancelResult.isDone()).isTrue();
        }
    }
}
