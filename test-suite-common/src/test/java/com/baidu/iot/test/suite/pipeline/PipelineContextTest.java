
package com.baidu.iot.test.suite.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.baidu.iot.test.suite.TaskEvent;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.statemachine.StateMachine;

import io.vertx.core.Vertx;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PipelineContext.
 */
class PipelineContextTest {

    @Nested
    class ConstructorTests {

        @Test
        void testConstructor_withValidParameters_shouldCreateContext() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            int expectPubCount = 10;
            int expectSubCount = 5;

            // when
            PipelineContext context = new PipelineContext(vertx, stateMachine, config,
                    expectPubCount, expectSubCount);

            // then
            assertThat(context.getVertx()).isSameAs(vertx);
            assertThat(context.getStateMachine()).isSameAs(stateMachine);
            assertThat(context.getConfig()).isSameAs(config);
            assertThat(context.getExpectPubCount()).isEqualTo(expectPubCount);
            assertThat(context.getExpectSubCount()).isEqualTo(expectSubCount);
            assertThat(context.getStageData()).isNotNull();
            assertThat(context.getCancelled()).isInstanceOf(AtomicBoolean.class);
            assertThat(context.getCompletionFuture()).isNotNull();
            assertThat(context.isCancelled()).isFalse();

            vertx.close();
        }

        @Test
        void testConstructor_withEmptyConfig_shouldCreateContext() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();

            // when
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // then
            assertThat(context.getConfig()).isEmpty();
            assertThat(context.getStageData()).isEmpty();

            vertx.close();
        }

        @Test
        void testConstructor_withNullConfig_shouldNotThrow() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);

            // when
            PipelineContext context = new PipelineContext(vertx, stateMachine, null, 0, 0);

            // then - PipelineContext accepts null config
            assertThat(context.getConfig()).isNull();
            assertThat(context.getVertx()).isSameAs(vertx);

            vertx.close();
        }
    }

    @Nested
    class FactoryMethodTests {

        @Test
        void testOf_withValidParameters_shouldCreateContext() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();

            // when
            PipelineContext context = PipelineContext.of(vertx, stateMachine, config, 10, 5);

            // then
            assertThat(context).isNotNull();
            assertThat(context.getVertx()).isSameAs(vertx);
            assertThat(context.getStateMachine()).isSameAs(stateMachine);

            vertx.close();
        }

        @Test
        void testOf_withZeroCounts_shouldCreateContext() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();

            // when
            PipelineContext context = PipelineContext.of(vertx, stateMachine, config, 0, 0);

            // then
            assertThat(context.getExpectPubCount()).isEqualTo(0);
            assertThat(context.getExpectSubCount()).isEqualTo(0);

            vertx.close();
        }
    }

    @Nested
    class CancelTests {

        @Test
        void testCancel_shouldSetCancelledFlag() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            context.cancel();

            // then
            assertThat(context.isCancelled()).isTrue();

            vertx.close();
        }

        @Test
        void testCancel_multipleCalls_shouldKeepCancelledTrue() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            context.cancel();
            context.cancel();
            context.cancel();

            // then
            assertThat(context.isCancelled()).isTrue();

            vertx.close();
        }
    }

    @Nested
    class IsCancelledTests {

        @Test
        void testIsCancelled_afterCreation_shouldReturnFalse() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            boolean cancelled = context.isCancelled();

            // then
            assertThat(cancelled).isFalse();

            vertx.close();
        }

        @Test
        void testIsCancelled_afterCancel_shouldReturnTrue() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);
            context.cancel();

            // when
            boolean cancelled = context.isCancelled();

            // then
            assertThat(cancelled).isTrue();

            vertx.close();
        }
    }

    @Nested
    class GetConfigValueTests {

        @Test
        void testGetConfigValue_withMatchingType_shouldReturnValue() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            config.put("timeout", 30);
            config.put("name", "test");
            config.put("enabled", true);
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            Integer timeout = context.getConfigValue("timeout", Integer.class);
            String name = context.getConfigValue("name", String.class);
            Boolean enabled = context.getConfigValue("enabled", Boolean.class);

            // then
            assertThat(timeout).isEqualTo(30);
            assertThat(name).isEqualTo("test");
            assertThat(enabled).isTrue();

            vertx.close();
        }

        @Test
        void testGetConfigValue_withWrongType_shouldReturnNull() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            config.put("value", "30");
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            Integer value = context.getConfigValue("value", Integer.class);

            // then
            assertThat(value).isNull();

            vertx.close();
        }

        @Test
        void testGetConfigValue_withNonexistentKey_shouldReturnNull() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            String value = context.getConfigValue("nonexistent", String.class);

            // then
            assertThat(value).isNull();

            vertx.close();
        }

        @Test
        void testGetConfigValue_withNullValue_shouldReturnNull() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            config.put("nullValue", null);
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            String value = context.getConfigValue("nullValue", String.class);

            // then
            assertThat(value).isNull();

            vertx.close();
        }

        @Test
        void testGetConfigValue_withComplexObject_shouldReturnSameInstance() {
            // given
            class ComplexObject {
                private final String name;

                ComplexObject(String name) {
                    this.name = name;
                }

                String getName() {
                    return name;
                }
            }
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            ComplexObject obj = new ComplexObject("test");
            config.put("complex", obj);
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            ComplexObject result = context.getConfigValue("complex", ComplexObject.class);

            // then
            assertThat(result).isSameAs(obj);
            assertThat(result.getName()).isEqualTo("test");

            vertx.close();
        }
    }

    @Nested
    class StageDataTests {

        @Test
        void testStageData_shouldBeModifiable() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            context.getStageData().put("key1", "value1");
            context.getStageData().put("key2", 42);

            // then
            assertThat(context.getStageData()).hasSize(2);
            assertThat(context.getStageData().get("key1")).isEqualTo("value1");
            assertThat(context.getStageData().get("key2")).isEqualTo(42);

            vertx.close();
        }

        @Test
        void testStageData_shouldStartEmpty() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            int size = context.getStageData().size();

            // then
            assertThat(size).isEqualTo(0);

            vertx.close();
        }

        @Test
        void testStageData_shouldBeSameInstance() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            Map<String, Object> data1 = context.getStageData();
            Map<String, Object> data2 = context.getStageData();

            // then
            assertThat(data1).isSameAs(data2);

            vertx.close();
        }
    }

    @Nested
    class CompletionFutureTests {

        @Test
        void testCompletionFuture_shouldNotBeCompletedInitially() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            CompletableFuture<Void> future = context.getCompletionFuture();

            // then
            assertThat(future).isNotNull();
            assertThat(future.isDone()).isFalse();
            assertThat(future.isCompletedExceptionally()).isFalse();

            vertx.close();
        }

        @Test
        void testCompletionFuture_shouldBeCompletable() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            context.getCompletionFuture().complete(null);

            // then
            assertThat(context.getCompletionFuture().isDone()).isTrue();
            assertThat(context.getCompletionFuture().isCompletedExceptionally()).isFalse();

            vertx.close();
        }

        @Test
        void testCompletionFuture_shouldBeCompletableExceptionally() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);
            RuntimeException error = new RuntimeException("Test error");

            // when
            context.getCompletionFuture().completeExceptionally(error);

            // then
            assertThat(context.getCompletionFuture().isDone()).isTrue();
            assertThat(context.getCompletionFuture().isCompletedExceptionally()).isTrue();

            vertx.close();
        }
    }

    @Nested
    class GetterTests {

        @Test
        void testGetVertx_shouldReturnProvidedVertxInstance() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            Vertx result = context.getVertx();

            // then
            assertThat(result).isSameAs(vertx);

            vertx.close();
        }

        @Test
        void testGetStateMachine_shouldReturnProvidedStateMachine() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            StateMachine<TaskStage, TaskEvent> result = context.getStateMachine();

            // then
            assertThat(result).isSameAs(stateMachine);

            vertx.close();
        }

        @Test
        void testGetConfig_shouldReturnProvidedConfigMap() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            config.put("key", "value");
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            Map<String, Object> result = context.getConfig();

            // then
            assertThat(result).isSameAs(config);
            assertThat(result).hasSize(1);

            vertx.close();
        }

        @Test
        void testGetExpectPubCount_shouldReturnProvidedValue() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            int expectPubCount = 25;
            PipelineContext context = new PipelineContext(vertx, stateMachine, config,
                    expectPubCount, 0);

            // when
            int result = context.getExpectPubCount();

            // then
            assertThat(result).isEqualTo(expectPubCount);

            vertx.close();
        }

        @Test
        void testGetExpectSubCount_shouldReturnProvidedValue() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            int expectSubCount = 15;
            PipelineContext context = new PipelineContext(vertx, stateMachine, config,
                    0, expectSubCount);

            // when
            int result = context.getExpectSubCount();

            // then
            assertThat(result).isEqualTo(expectSubCount);

            vertx.close();
        }

        @Test
        void testGetCancelled_shouldReturnAtomicBoolean() {
            // given
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            Map<String, Object> config = new HashMap<>();
            PipelineContext context = new PipelineContext(vertx, stateMachine, config, 0, 0);

            // when
            AtomicBoolean cancelled = context.getCancelled();

            // then
            assertThat(cancelled).isNotNull();
            assertThat(cancelled.get()).isFalse();

            vertx.close();
        }
    }
}
