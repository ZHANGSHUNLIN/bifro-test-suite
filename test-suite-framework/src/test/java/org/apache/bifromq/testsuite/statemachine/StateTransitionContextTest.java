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

package org.apache.bifromq.testsuite.statemachine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            
            TestState fromState = TestState.INIT;
            TestState toState = TestState.RUNNING;
            Object event = "START_EVENT";

            
            StateTransitionContext<TestState> context =
                new StateTransitionContext<>(fromState, toState, event);

            
            assertThat(context.getFromState()).isEqualTo(fromState);
            assertThat(context.getToState()).isEqualTo(toState);
            assertThat(context.getEvent()).isEqualTo(event);
            assertThat(context.getMetadata()).isNotNull();
            assertThat(context.getMetadata()).isEmpty();
            assertThat(context.getError()).isNull();
        }

        @Test
        void testConstructor_withNullParameters_shouldCreateContext() {
            
            TestState fromState = null;
            TestState toState = null;
            Object event = null;

            
            StateTransitionContext<TestState> context =
                new StateTransitionContext<>(fromState, toState, event);

            
            assertThat(context.getFromState()).isNull();
            assertThat(context.getToState()).isNull();
            assertThat(context.getEvent()).isNull();
            assertThat(context.getMetadata()).isNotNull();
        }

        @Test
        void testConstructor_withEnumEvent_shouldCreateContext() {
            
            enum TestEvent {
                START,
                STOP
            }
            TestState fromState = TestState.INIT;
            TestState toState = TestState.RUNNING;
            TestEvent event = TestEvent.START;

            
            StateTransitionContext<TestState> context =
                new StateTransitionContext<>(fromState, toState, event);

            
            assertThat(context.getFromState()).isEqualTo(fromState);
            assertThat(context.getToState()).isEqualTo(toState);
            assertThat(context.getEvent()).isEqualTo(event);
        }

        @Test
        void testConstructor_shouldRecordTransitionTimestamp() {
            
            TestState fromState = TestState.INIT;
            TestState toState = TestState.RUNNING;
            Object event = "START_EVENT";
            java.time.Instant beforeCreation = java.time.Instant.now();

            
            StateTransitionContext<TestState> context =
                new StateTransitionContext<>(fromState, toState, event);
            java.time.Instant afterCreation = java.time.Instant.now();

            
            assertThat(context.getTransitionTimestamp()).isNotNull();
            assertThat(context.getTransitionTimestamp()).isBetween(beforeCreation, afterCreation);
        }

        @Test
        void testOf_shouldRecordTransitionTimestamp() {
            
            TestState fromState = TestState.INIT;
            TestState toState = TestState.RUNNING;
            Object event = "START_EVENT";
            java.time.Instant beforeCreation = java.time.Instant.now();

            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(fromState, toState, event);
            java.time.Instant afterCreation = java.time.Instant.now();

            
            assertThat(context.getTransitionTimestamp()).isNotNull();
            assertThat(context.getTransitionTimestamp()).isBetween(beforeCreation, afterCreation);
        }
    }

    @Nested
    class FactoryMethodTests {

        @Test
        void testOf_withValidParameters_shouldCreateContext() {
            
            TestState fromState = TestState.INIT;
            TestState toState = TestState.RUNNING;
            Object event = "START_EVENT";

            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(fromState, toState, event);

            
            assertThat(context.getFromState()).isEqualTo(fromState);
            assertThat(context.getToState()).isEqualTo(toState);
            assertThat(context.getEvent()).isEqualTo(event);
        }

        @Test
        void testOf_withSameState_shouldCreateContext() {
            
            TestState fromState = TestState.RUNNING;
            TestState toState = TestState.RUNNING;
            Object event = "NO_CHANGE_EVENT";

            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(fromState, toState, event);

            
            assertThat(context.getFromState()).isEqualTo(fromState);
            assertThat(context.getToState()).isEqualTo(toState);
            assertThat(context.getEvent()).isEqualTo(event);
        }
    }

    @Nested
    class MetadataTests {

        @Test
        void testWithMetadata_shouldAddMetadata() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            String key = "userId";
            String value = "user123";

            
            context.withMetadata(key, value);

            
            assertThat(context.getMetadata(key)).isEqualTo(value);
        }

        @Test
        void testWithMetadata_withMultipleKeys_shouldAddAllMetadata() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            
            context.withMetadata("key1", "value1")
                .withMetadata("key2", "value2")
                .withMetadata("key3", "value3");

            
            assertThat(context.getMetadata("key1")).isEqualTo("value1");
            assertThat(context.getMetadata("key2")).isEqualTo("value2");
            assertThat(context.getMetadata("key3")).isEqualTo("value3");
        }

        @Test
        void testWithMetadata_shouldOverwriteExistingKey() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            context.withMetadata("key", "oldValue");

            
            context.withMetadata("key", "newValue");

            
            assertThat(context.getMetadata("key")).isEqualTo("newValue");
        }

        @Test
        void testWithMetadata_withNullValue_shouldIgnore() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            
            context.withMetadata("nullKey", null);
            assertThat(context.getMetadata("nullKey")).isNull();
            assertThat(context.getMetadata()).isEmpty();
        }

        @Test
        void testGetMetadata_withNonexistentKey_shouldReturnNull() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            
            Object result = context.getMetadata("nonexistent");

            
            assertThat(result).isNull();
        }

        @Test
        void testGetMetadata_withTypedClassAndCorrectType_shouldReturnValue() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            Integer value = 42;
            context.withMetadata("number", value);

            
            Integer result = context.getMetadata("number", Integer.class);

            
            assertThat(result).isEqualTo(value);
        }

        @Test
        void testGetMetadata_withTypedClassAndWrongType_shouldReturnNull() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            String value = "not a number";
            context.withMetadata("number", value);

            
            Integer result = context.getMetadata("number", Integer.class);

            
            assertThat(result).isNull();
        }

        @Test
        void testGetMetadata_withTypedClassAndNullValue_shouldIgnore() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            
            context.withMetadata("nullValue", null);
            assertThat(context.getMetadata("nullValue", Integer.class)).isNull();
            assertThat(context.getMetadata()).isEmpty();
        }

        @Test
        void testGetMetadata_withTypedClassAndNonexistentKey_shouldReturnNull() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            
            Integer result = context.getMetadata("nonexistent", Integer.class);

            
            assertThat(result).isNull();
        }

        @Test
        void testGetMetadata_withComplexObjectType_shouldReturnSameInstance() {
            
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

            
            ComplexObject result = context.getMetadata("complex", ComplexObject.class);

            
            assertThat(result).isSameAs(obj);
            assertThat(result.getName()).isEqualTo("test");
        }

        @Test
        void testGetMetadata_shouldReturnConcurrentHashMap() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            
            Map<String, Object> metadata = context.getMetadata();

            
            assertThat(metadata).isInstanceOf(ConcurrentHashMap.class);
        }
    }

    @Nested
    class ErrorTests {

        @Test
        void testWithError_shouldSetError() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            Throwable error = new RuntimeException("Test error");

            
            context.withError(error);

            
            assertThat(context.getError()).isEqualTo(error);
        }

        @Test
        void testWithError_withNullError_shouldSetNull() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            Throwable error = new RuntimeException("Test error");
            context.withError(error);

            
            context.withError(null);

            
            assertThat(context.getError()).isNull();
        }

        @Test
        void testGetError_whenNoErrorSet_shouldReturnNull() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            
            Throwable error = context.getError();

            
            assertThat(error).isNull();
        }

        @Test
        void testWithError_withDifferentExceptionTypes() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            
            RuntimeException runtimeError = new RuntimeException("Runtime error");
            context.withError(runtimeError);
            Throwable result1 = context.getError();

            IllegalStateException illegalError = new IllegalStateException("Illegal state");
            context.withError(illegalError);
            Throwable result2 = context.getError();

            NullPointerException nullError = new NullPointerException("Null pointer");
            context.withError(nullError);
            Throwable result3 = context.getError();

            
            assertThat(result1).isInstanceOf(RuntimeException.class);
            assertThat(result2).isInstanceOf(IllegalStateException.class);
            assertThat(result3).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ChainingTests {

        @Test
        void testWithMetadata_shouldReturnContextForChaining() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");

            
            StateTransitionContext<TestState> result = context
                .withMetadata("key1", "value1")
                .withMetadata("key2", "value2");

            
            assertThat(result).isSameAs(context);
            assertThat(context.getMetadata("key1")).isEqualTo("value1");
            assertThat(context.getMetadata("key2")).isEqualTo("value2");
        }

        @Test
        void testWithError_shouldReturnContextForChaining() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            Throwable error = new RuntimeException("Error");

            
            StateTransitionContext<TestState> result = context.withError(error);

            
            assertThat(result).isSameAs(context);
            assertThat(context.getError()).isEqualTo(error);
        }

        @Test
        void testMixedChaining_shouldWorkCorrectly() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            Throwable error = new RuntimeException("Error");

            
            StateTransitionContext<TestState> result = context
                .withMetadata("key1", "value1")
                .withError(error)
                .withMetadata("key2", "value2");

            
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
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, "START");
            int threadCount = 10;
            int operationsPerThread = 100;
            Thread[] threads = new Thread[threadCount];

            
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

            
            int expectedSize = threadCount * operationsPerThread;
            assertThat(context.getMetadata().size()).isEqualTo(expectedSize);
        }
    }
}
