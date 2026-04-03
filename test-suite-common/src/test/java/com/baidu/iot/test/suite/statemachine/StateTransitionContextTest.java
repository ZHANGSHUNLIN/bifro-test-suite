
package com.baidu.iot.test.suite.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for StateTransitionContext.
 */
class StateTransitionContextTest {

    private enum TestState {
        INIT,
        RUNNING,
        STOPPED,
        FAILED,
        SUCCESS
    }

    @Nested
    class ConstructorTests {

        @Test
        void testConstructor_withValidParameters_shouldCreateContext() {
            // given
            TestState fromState = TestState.INIT;
            TestState toState = TestState.RUNNING;
            Object event = "START_EVENT";

            // when
            StateTransitionContext<TestState> context =
                    new StateTransitionContext<>(fromState, toState, event);

            // then
            assertThat(context.getFromState()).isEqualTo(fromState);
            assertThat(context.getToState()).isEqualTo(toState);
            assertThat(context.getEvent()).isEqualTo(event);
            assertThat(context.getMetadata()).isNotNull();
            assertThat(context.getMetadata()).isEmpty();
            assertThat(context.getError()).isNull();
        }

        @Test
        void testConstructor_withNullParameters_shouldCreateContext() {
            // given
            TestState fromState = null;
            TestState toState = null;
            Object event = null;

            // when
            StateTransitionContext<TestState> context =
                    new StateTransitionContext<>(fromState, toState, event);

            // then
            assertThat(context.getFromState()).isNull();
            assertThat(context.getToState()).isNull();
            assertThat(context.getEvent()).isNull();
            assertThat(context.getMetadata()).isNotNull();
        }

        @Test
        void testConstructor_withEnumEvent_shouldCreateContext() {
            // given
            enum TestEvent {
                START,
                STOP
            }
            TestState fromState = TestState.INIT;
            TestState toState = TestState.RUNNING;
            TestEvent event = TestEvent.START;

            // when
            StateTransitionContext<TestState> context =
                    new StateTransitionContext<>(fromState, toState, event);

            // then
            assertThat(context.getFromState()).isEqualTo(fromState);
            assertThat(context.getToState()).isEqualTo(toState);
            assertThat(context.getEvent()).isEqualTo(event);
        }
    }

    @Nested
    class FactoryMethodTests {

        @Test
        void testOf_withValidParameters_shouldCreateContext() {
            // given
            TestState fromState = TestState.INIT;
            TestState toState = TestState.RUNNING;
            Object event = "START_EVENT";

            // when
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(fromState, toState, event);

            // then
            assertThat(context.getFromState()).isEqualTo(fromState);
            assertThat(context.getToState()).isEqualTo(toState);
            assertThat(context.getEvent()).isEqualTo(event);
        }

        @Test
        void testOf_withSameState_shouldCreateContext() {
            // given
            TestState fromState = TestState.RUNNING;
            TestState toState = TestState.RUNNING;
            Object event = "NO_CHANGE_EVENT";

            // when
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(fromState, toState, event);

            // then
            assertThat(context.getFromState()).isEqualTo(fromState);
            assertThat(context.getToState()).isEqualTo(toState);
            assertThat(context.getEvent()).isEqualTo(event);
        }
    }

    @Nested
    class MetadataTests {

        @Test
        void testWithMetadata_shouldAddMetadata() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            String key = "userId";
            String value = "user123";

            // when
            context.withMetadata(key, value);

            // then
            assertThat(context.getMetadata(key)).isEqualTo(value);
        }

        @Test
        void testWithMetadata_withMultipleKeys_shouldAddAllMetadata() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            // when
            context.withMetadata("key1", "value1")
                    .withMetadata("key2", "value2")
                    .withMetadata("key3", "value3");

            // then
            assertThat(context.getMetadata("key1")).isEqualTo("value1");
            assertThat(context.getMetadata("key2")).isEqualTo("value2");
            assertThat(context.getMetadata("key3")).isEqualTo("value3");
        }

        @Test
        void testWithMetadata_shouldOverwriteExistingKey() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            context.withMetadata("key", "oldValue");

            // when
            context.withMetadata("key", "newValue");

            // then
            assertThat(context.getMetadata("key")).isEqualTo("newValue");
        }

        @Test
        void testWithMetadata_withNullValue_shouldThrowNPE() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            // when/then - ConcurrentHashMap does not allow null values
            assertThatThrownBy(() -> context.withMetadata("nullKey", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void testGetMetadata_withNonexistentKey_shouldReturnNull() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            // when
            Object result = context.getMetadata("nonexistent");

            // then
            assertThat(result).isNull();
        }

        @Test
        void testGetMetadata_withTypedClassAndCorrectType_shouldReturnValue() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            Integer value = 42;
            context.withMetadata("number", value);

            // when
            Integer result = context.getMetadata("number", Integer.class);

            // then
            assertThat(result).isEqualTo(value);
        }

        @Test
        void testGetMetadata_withTypedClassAndWrongType_shouldReturnNull() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            String value = "not a number";
            context.withMetadata("number", value);

            // when
            Integer result = context.getMetadata("number", Integer.class);

            // then
            assertThat(result).isNull();
        }

        @Test
        void testGetMetadata_withTypedClassAndNullValue_shouldThrowNPE() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            // when/then - ConcurrentHashMap does not allow null values
            assertThatThrownBy(() -> context.withMetadata("nullValue", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void testGetMetadata_withTypedClassAndNonexistentKey_shouldReturnNull() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            // when
            Integer result = context.getMetadata("nonexistent", Integer.class);

            // then
            assertThat(result).isNull();
        }

        @Test
        void testGetMetadata_withComplexObjectType_shouldReturnSameInstance() {
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
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            ComplexObject obj = new ComplexObject("test");
            context.withMetadata("complex", obj);

            // when
            ComplexObject result = context.getMetadata("complex", ComplexObject.class);

            // then
            assertThat(result).isSameAs(obj);
            assertThat(result.getName()).isEqualTo("test");
        }

        @Test
        void testGetMetadata_shouldReturnConcurrentHashMap() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            // when
            Map<String, Object> metadata = context.getMetadata();

            // then
            assertThat(metadata).isInstanceOf(ConcurrentHashMap.class);
        }
    }

    @Nested
    class ErrorTests {

        @Test
        void testWithError_shouldSetError() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            Throwable error = new RuntimeException("Test error");

            // when
            context.withError(error);

            // then
            assertThat(context.getError()).isEqualTo(error);
        }

        @Test
        void testWithError_withNullError_shouldSetNull() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            Throwable error = new RuntimeException("Test error");
            context.withError(error);

            // when
            context.withError(null);

            // then
            assertThat(context.getError()).isNull();
        }

        @Test
        void testGetError_whenNoErrorSet_shouldReturnNull() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            // when
            Throwable error = context.getError();

            // then
            assertThat(error).isNull();
        }

        @Test
        void testWithError_withDifferentExceptionTypes() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            // when
            RuntimeException runtimeError = new RuntimeException("Runtime error");
            context.withError(runtimeError);
            Throwable result1 = context.getError();

            IllegalStateException illegalError = new IllegalStateException("Illegal state");
            context.withError(illegalError);
            Throwable result2 = context.getError();

            NullPointerException nullError = new NullPointerException("Null pointer");
            context.withError(nullError);
            Throwable result3 = context.getError();

            // then
            assertThat(result1).isInstanceOf(RuntimeException.class);
            assertThat(result2).isInstanceOf(IllegalStateException.class);
            assertThat(result3).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ChainingTests {

        @Test
        void testWithMetadata_shouldReturnContextForChaining() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            // when
            StateTransitionContext<TestState> result = context
                    .withMetadata("key1", "value1")
                    .withMetadata("key2", "value2");

            // then
            assertThat(result).isSameAs(context);
            assertThat(context.getMetadata("key1")).isEqualTo("value1");
            assertThat(context.getMetadata("key2")).isEqualTo("value2");
        }

        @Test
        void testWithError_shouldReturnContextForChaining() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            Throwable error = new RuntimeException("Error");

            // when
            StateTransitionContext<TestState> result = context.withError(error);

            // then
            assertThat(result).isSameAs(context);
            assertThat(context.getError()).isEqualTo(error);
        }

        @Test
        void testMixedChaining_shouldWorkCorrectly() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            Throwable error = new RuntimeException("Error");

            // when
            StateTransitionContext<TestState> result = context
                    .withMetadata("key1", "value1")
                    .withError(error)
                    .withMetadata("key2", "value2");

            // then
            assertThat(result).isSameAs(context);
            assertThat(context.getMetadata("key1")).isEqualTo("value1");
            assertThat(context.getMetadata("key2")).isEqualTo("value2");
            assertThat(context.getError()).isEqualTo(error);
        }
    }

    @Nested
    class ThreadSafetyTests {

        @Test
        void testConcurrentMetadataAccess_shouldBeThreadSafe() throws InterruptedException {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            int threadCount = 10;
            int operationsPerThread = 100;
            Thread[] threads = new Thread[threadCount];

            // when
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < operationsPerThread; j++) {
                        context.withMetadata("thread" + threadId + "_" + j, "value" + j);
                        context.getMetadata("thread" + threadId + "_" + j);
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // then
            int expectedSize = threadCount * operationsPerThread;
            assertThat(context.getMetadata().size()).isEqualTo(expectedSize);
        }
    }
}
