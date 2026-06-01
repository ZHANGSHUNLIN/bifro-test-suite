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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateMachineTest {

    private StateMachine<TestState, TestEvent> stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new StateMachine<>(TestState.INIT);
    }

    private enum TestState {
        INIT,
        RUNNING,
        STOPPED,
        FAILED,
        SUCCESS
    }

    private enum TestEvent {
        START,
        STOP,
        FAIL,
        COMPLETE
    }

    @Nested
    class ConstructorTests {

        @Test
        void testConstructor_withInitialState_shouldSetInitialState() {
            
            StateMachine<TestState, TestEvent> machine = new StateMachine<>(TestState.INIT);

            
            TestState currentState = machine.getCurrentState();

            
            assertThat(currentState).isEqualTo(TestState.INIT);
        }

        @Test
        void testConstructor_withDifferentInitialState_shouldSetCorrectState() {
            
            StateMachine<TestState, TestEvent> machine = new StateMachine<>(TestState.RUNNING);

            
            TestState currentState = machine.getCurrentState();

            
            assertThat(currentState).isEqualTo(TestState.RUNNING);
        }
    }

    @Nested
    class AddTransitionTests {

        @Test
        void testAddTransition_withValidTransition_shouldAddTransition() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            
            stateMachine.addTransition(transition);
            boolean canTransition = stateMachine.canTransition(TestEvent.START);

            
            assertThat(canTransition).isTrue();
        }

        @Test
        void testAddTransition_usingBuilder_shouldAddTransition() {
            
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            boolean canTransition = stateMachine.canTransition(TestEvent.START);

            
            assertThat(canTransition).isTrue();
        }

        @Test
        void testAddAnyTransition_shouldAddGlobalTransition() {
            
            
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                .from(TestState.RUNNING)
                .to(TestState.SUCCESS)
                .on(TestEvent.COMPLETE));
            stateMachine.addAnyTransition(TestState.STOPPED, TestEvent.STOP);

            
            boolean fromInit = stateMachine.canTransition(TestEvent.STOP);
            stateMachine.transition(TestEvent.START).join();
            boolean fromRunning = stateMachine.canTransition(TestEvent.STOP);

            
            assertThat(fromInit).isTrue();
            assertThat(fromRunning).isTrue();
        }

        @Test
        void testAddTransition_withNullFrom_shouldNotThrowException() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(null)
                    .to(TestState.FAILED)
                    .on(TestEvent.FAIL)
                    .build();

            
            stateMachine.addTransition(transition);

            
            assertThat(stateMachine.canTransition(TestEvent.FAIL)).isTrue();
        }

        @Test
        void testAddMultipleTransitionsFromSameState_shouldAllBeAdded() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.FAILED)
                .on(TestEvent.FAIL));

            
            boolean canStart = stateMachine.canTransition(TestEvent.START);
            boolean canFail = stateMachine.canTransition(TestEvent.FAIL);

            
            assertThat(canStart).isTrue();
            assertThat(canFail).isTrue();
        }
    }

    @Nested
    class TransitionTests {

        @Test
        void testTransition_withValidTransition_shouldChangeState() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testTransition_withNoMatchingTransition_shouldNotChangeState() {
            
            TestState initialState = stateMachine.getCurrentState();

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            
            assertThat(result.join()).isFalse();
            assertThat(stateMachine.getCurrentState()).isEqualTo(initialState);
        }

        @Test
        void testTransition_withContext_shouldPassContextToTransition() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            Map<String, Object> context = Map.of("key1", "value1", "key2", 123);

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START, context);

            
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testTransition_multipleTransitions_shouldFollowCorrectPath() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                .from(TestState.RUNNING)
                .to(TestState.SUCCESS)
                .on(TestEvent.COMPLETE));

            
            stateMachine.transition(TestEvent.START).join();
            stateMachine.transition(TestEvent.COMPLETE).join();

            
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.SUCCESS);
        }

        @Test
        void testTransition_withWrongEvent_shouldNotChangeState() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            TestState initialState = stateMachine.getCurrentState();

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.STOP);

            
            assertThat(result.join()).isFalse();
            assertThat(stateMachine.getCurrentState()).isEqualTo(initialState);
        }

        @Test
        void testTransition_withGuardBlocking_shouldNotChangeState() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .guard(ctx -> false));

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            
            assertThat(result.join()).isFalse();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.INIT);
        }

        @Test
        void testTransition_withGuardPassing_shouldChangeState() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .guard(ctx -> true));

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testTransition_withContextGuard_shouldUseContextInGuard() {
            
            AtomicInteger guardCalls = new AtomicInteger(0);
            AtomicReference<StateTransitionContext<TestState>> capturedContext = new AtomicReference<>();
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .guard(ctx -> {
                    guardCalls.incrementAndGet();
                    capturedContext.set(ctx);
                    return "allowed".equals(ctx.getMetadata("permission"));
                }));
            Map<String, Object> context = Map.of("permission", "allowed");

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START, context);

            
            assertThat(result.join()).isTrue();
            assertThat(guardCalls.get()).isEqualTo(1);
            assertThat(capturedContext.get()).isNotNull();
            assertThat(capturedContext.get().getMetadata("permission")).isEqualTo("allowed");
        }

        @Test
        void testTransition_withGuardAndNoPermission_shouldNotChangeState() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .guard(ctx -> "allowed".equals(ctx.getMetadata("permission"))));
            Map<String, Object> context = Map.of("permission", "denied");

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START, context);

            
            assertThat(result.join()).isFalse();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.INIT);
        }
    }

    @Nested
    class ListenerTests {

        @Mock
        private StateChangeListener<TestState> listener1;

        @Mock
        private StateChangeListener<TestState> listener2;

        @Test
        void testAddListener_withValidListener_shouldAddListener() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            
            stateMachine.addListener(listener1);

            
            stateMachine.transition(TestEvent.START).join();
            verify(listener1).onStateExited(eq(TestState.INIT), any());
            verify(listener1).onStateEntered(eq(TestState.RUNNING), any());
            verify(listener1).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
        }

        @Test
        void testAddListener_withNullListener_shouldNotAddListener() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            
            stateMachine.addListener(null);
            stateMachine.transition(TestEvent.START).join();

            
            verifyNoMoreInteractions(listener1);
        }

        @Test
        void testAddListener_multipleListeners_shouldNotifyAll() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addListener(listener1);
            stateMachine.addListener(listener2);

            
            stateMachine.transition(TestEvent.START).join();

            
            verify(listener1).onStateExited(eq(TestState.INIT), any());
            verify(listener1).onStateEntered(eq(TestState.RUNNING), any());
            verify(listener1).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
            verify(listener2).onStateExited(eq(TestState.INIT), any());
            verify(listener2).onStateEntered(eq(TestState.RUNNING), any());
            verify(listener2).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
        }

        @Test
        void testListener_onStateExited_shouldPassCorrectContext() {
            
            AtomicReference<StateTransitionContext<TestState>> capturedContext = new AtomicReference<>();
            StateChangeListener<TestState> contextCapturingListener = new StateChangeListener<TestState>() {
                @Override
                public void onStateExited(TestState state, StateTransitionContext<TestState> context) {
                    capturedContext.set(context);
                }

                @Override
                public void onStateChange(TestState from, TestState to,
                                          StateTransitionContext<TestState> context) {
                }
            };
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addListener(contextCapturingListener);
            Map<String, Object> context = Map.of("testKey", "testValue");

            
            stateMachine.transition(TestEvent.START, context).join();

            
            StateTransitionContext<TestState> ctx = capturedContext.get();
            assertThat(ctx).isNotNull();
            assertThat(ctx.getFromState()).isEqualTo(TestState.INIT);
            assertThat(ctx.getToState()).isEqualTo(TestState.RUNNING);
            assertThat(ctx.getEvent()).isEqualTo(TestEvent.START);
            assertThat(ctx.getMetadata("testKey")).isEqualTo("testValue");
        }

        @Test
        void testListener_onStateEntered_shouldPassCorrectContext() {
            
            AtomicReference<StateTransitionContext<TestState>> capturedContext = new AtomicReference<>();
            StateChangeListener<TestState> contextCapturingListener = new StateChangeListener<TestState>() {
                @Override
                public void onStateEntered(TestState state, StateTransitionContext<TestState> context) {
                    capturedContext.set(context);
                }

                @Override
                public void onStateChange(TestState from, TestState to,
                                          StateTransitionContext<TestState> context) {
                }
            };
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addListener(contextCapturingListener);

            
            stateMachine.transition(TestEvent.START).join();

            
            StateTransitionContext<TestState> ctx = capturedContext.get();
            assertThat(ctx).isNotNull();
            assertThat(ctx.getFromState()).isEqualTo(TestState.INIT);
            assertThat(ctx.getToState()).isEqualTo(TestState.RUNNING);
            assertThat(ctx.getEvent()).isEqualTo(TestEvent.START);
        }

        @Test
        void testListener_onStateChange_shouldPassCorrectStates() {
            
            AtomicReference<TestState> fromState = new AtomicReference<>();
            AtomicReference<TestState> toState = new AtomicReference<>();
            StateChangeListener<TestState> stateCapturingListener = new StateChangeListener<TestState>() {
                @Override
                public void onStateChange(TestState from, TestState to,
                                          StateTransitionContext<TestState> context) {
                    fromState.set(from);
                    toState.set(to);
                }
            };
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addListener(stateCapturingListener);

            
            stateMachine.transition(TestEvent.START).join();

            
            assertThat(fromState.get()).isEqualTo(TestState.INIT);
            assertThat(toState.get()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testListener_onStateExited_exception_shouldContinueExecution() {
            
            StateChangeListener<TestState> failingListener = new StateChangeListener<TestState>() {
                @Override
                public void onStateExited(TestState state, StateTransitionContext<TestState> context) {
                    throw new RuntimeException("Test exception in onStateExited");
                }

                @Override
                public void onStateChange(TestState from, TestState to,
                                          StateTransitionContext<TestState> context) {
                }
            };
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addListener(failingListener);
            stateMachine.addListener(listener1);

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
            verify(listener1).onStateEntered(eq(TestState.RUNNING), any());
            verify(listener1).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
        }

        @Test
        void testListener_onStateEntered_exception_shouldContinueExecution() {
            
            StateChangeListener<TestState> failingListener = new StateChangeListener<TestState>() {
                @Override
                public void onStateEntered(TestState state, StateTransitionContext<TestState> context) {
                    throw new RuntimeException("Test exception in onStateEntered");
                }

                @Override
                public void onStateChange(TestState from, TestState to,
                                          StateTransitionContext<TestState> context) {
                }
            };
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addListener(listener1);
            stateMachine.addListener(failingListener);
            stateMachine.addListener(listener2);

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
            verify(listener1).onStateExited(eq(TestState.INIT), any());
            verify(listener1).onStateEntered(eq(TestState.RUNNING), any());
            verify(listener2).onStateEntered(eq(TestState.RUNNING), any());
        }

        @Test
        void testListener_onStateChange_exception_shouldCallOnError() {
            
            AtomicReference<Throwable> capturedError = new AtomicReference<>();
            StateChangeListener<TestState> errorCapturingListener = new StateChangeListener<TestState>() {
                @Override
                public void onStateChange(TestState from, TestState to,
                                          StateTransitionContext<TestState> context) {
                    throw new RuntimeException("Test exception in onStateChange");
                }

                @Override
                public void onTransitionError(TestState from, TestState to, Throwable error) {
                    capturedError.set(error);
                }
            };
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addListener(errorCapturingListener);

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            
            assertThat(result.join()).isTrue();
            assertThat(capturedError.get()).isNotNull();
            assertThat(capturedError.get().getMessage()).isEqualTo("Test exception in onStateChange");
        }

        @Test
        void testListener_onStateChange_exception_shouldContinueWithOtherListeners() {
            
            StateChangeListener<TestState> failingListener = new StateChangeListener<TestState>() {
                @Override
                public void onStateChange(TestState from, TestState to,
                                          StateTransitionContext<TestState> context) {
                    throw new RuntimeException("Test exception");
                }
            };
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addListener(listener1);
            stateMachine.addListener(failingListener);
            stateMachine.addListener(listener2);

            
            stateMachine.transition(TestEvent.START).join();

            
            verify(listener1).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
            verify(listener2).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
        }

        @Test
        void testListener_notificationOrder_shouldNotifyInCorrectOrder() {
            
            java.util.List<String> notifications = new java.util.ArrayList<>();
            StateChangeListener<TestState> listener1 = new StateChangeListener<TestState>() {
                @Override
                public void onStateExited(TestState state, StateTransitionContext<TestState> context) {
                    notifications.add("listener1-exit-" + state);
                }

                @Override
                public void onStateEntered(TestState state, StateTransitionContext<TestState> context) {
                    notifications.add("listener1-enter-" + state);
                }

                @Override
                public void onStateChange(TestState from, TestState to,
                                          StateTransitionContext<TestState> context) {
                    notifications.add("listener1-change-" + from + "-" + to);
                }
            };
            StateChangeListener<TestState> listener2 = new StateChangeListener<TestState>() {
                @Override
                public void onStateExited(TestState state, StateTransitionContext<TestState> context) {
                    notifications.add("listener2-exit-" + state);
                }

                @Override
                public void onStateEntered(TestState state, StateTransitionContext<TestState> context) {
                    notifications.add("listener2-enter-" + state);
                }

                @Override
                public void onStateChange(TestState from, TestState to,
                                          StateTransitionContext<TestState> context) {
                    notifications.add("listener2-change-" + from + "-" + to);
                }
            };
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addListener(listener1);
            stateMachine.addListener(listener2);

            
            stateMachine.transition(TestEvent.START).join();

            
            assertThat(notifications).containsExactly(
                "listener1-exit-INIT",
                "listener2-exit-INIT",
                "listener1-enter-RUNNING",
                "listener2-enter-RUNNING",
                "listener1-change-INIT-RUNNING",
                "listener2-change-INIT-RUNNING"
            );
        }
    }

    @Nested
    class StateQueryTests {

        @Test
        void testGetCurrentState_shouldReturnCurrentState() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            
            TestState beforeTransition = stateMachine.getCurrentState();
            stateMachine.transition(TestEvent.START).join();
            TestState afterTransition = stateMachine.getCurrentState();

            
            assertThat(beforeTransition).isEqualTo(TestState.INIT);
            assertThat(afterTransition).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testGetCurrentStateReference_shouldReturnAtomicReference() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            
            java.util.concurrent.atomic.AtomicReference<TestState> ref =
                stateMachine.getCurrentStateReference();

            
            assertThat(ref).isNotNull();
            assertThat(ref.get()).isEqualTo(TestState.INIT);
            stateMachine.transition(TestEvent.START).join();
            assertThat(ref.get()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testCanTransition_withValidTransition_shouldReturnTrue() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            
            boolean canTransition = stateMachine.canTransition(TestEvent.START);

            
            assertThat(canTransition).isTrue();
        }

        @Test
        void testCanTransition_withInvalidTransition_shouldReturnFalse() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            
            boolean canTransition = stateMachine.canTransition(TestEvent.STOP);

            
            assertThat(canTransition).isFalse();
        }

        @Test
        void testCanTransition_afterStateChange_shouldReflectNewState() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                .from(TestState.RUNNING)
                .to(TestState.STOPPED)
                .on(TestEvent.STOP));

            
            boolean canStartInit = stateMachine.canTransition(TestEvent.START);
            boolean canStopInit = stateMachine.canTransition(TestEvent.STOP);
            stateMachine.transition(TestEvent.START).join();
            boolean canStartRunning = stateMachine.canTransition(TestEvent.START);
            boolean canStopRunning = stateMachine.canTransition(TestEvent.STOP);

            
            assertThat(canStartInit).isTrue();
            assertThat(canStopInit).isFalse();
            assertThat(canStartRunning).isFalse();
            assertThat(canStopRunning).isTrue();
        }

        @Test
        void testCanTransition_withGuardBlocking_shouldReturnTrue_butTransitionFails() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .guard(ctx -> false));

            
            boolean canTransition = stateMachine.canTransition(TestEvent.START);
            CompletableFuture<Boolean> transitionResult = stateMachine.transition(TestEvent.START);

            
            
            assertThat(canTransition).isTrue();
            assertThat(transitionResult.join()).isFalse();
        }
    }

    @Nested
    class ResetTests {

        @Test
        void testReset_afterTransitions_shouldReturnToInitialState() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                .from(TestState.RUNNING)
                .to(TestState.SUCCESS)
                .on(TestEvent.COMPLETE));

            
            stateMachine.transition(TestEvent.START).join();
            stateMachine.transition(TestEvent.COMPLETE).join();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.SUCCESS);
            stateMachine.reset();

            
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.INIT);
        }

        @Test
        void testReset_multipleTimes_shouldAlwaysReturnToInitialState() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            
            stateMachine.transition(TestEvent.START).join();
            stateMachine.reset();
            TestState firstReset = stateMachine.getCurrentState();
            stateMachine.reset();
            TestState secondReset = stateMachine.getCurrentState();

            
            assertThat(firstReset).isEqualTo(TestState.INIT);
            assertThat(secondReset).isEqualTo(TestState.INIT);
        }

        @Test
        void testReset_andTransitionAgain_shouldWorkCorrectly() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            
            stateMachine.transition(TestEvent.START).join();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
            stateMachine.reset();
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }
    }

    @Nested
    class ConcurrencyTests {

        private ExecutorService executorService;

        @BeforeEach
        void setUpExecutor() {
            executorService = Executors.newFixedThreadPool(10);
        }

        @AfterEach
        void tearDownExecutor() {
            if (executorService != null) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Test
        void testConcurrentTransitions_sameEvent_shouldAllowOnlyOneToSucceed()
            throws InterruptedException {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);

            
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);
                        if (result.join()) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();

            
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(threadCount - 1);
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testConcurrentTransitions_sequentialEvents_shouldSucceed()
            throws InterruptedException {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                .from(TestState.RUNNING)
                .to(TestState.SUCCESS)
                .on(TestEvent.COMPLETE));
            AtomicInteger completedCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(10);

            
            for (int i = 0; i < 5; i++) {
                executorService.submit(() -> {
                    try {
                        stateMachine.transition(TestEvent.START).join();
                        completedCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
                executorService.submit(() -> {
                    try {
                        stateMachine.transition(TestEvent.COMPLETE).join();
                        completedCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();

            
            
            
            assertThat(completedCount.get()).isGreaterThanOrEqualTo(2);
            assertThat(completedCount.get()).isLessThanOrEqualTo(10);
        }

        @Test
        void testConcurrentListeners_shouldHandleSafely() throws InterruptedException {
            
            AtomicInteger notificationCount = new AtomicInteger(0);
            StateChangeListener<TestState> countingListener = new StateChangeListener<TestState>() {
                @Override
                public void onStateChange(TestState from, TestState to,
                                          StateTransitionContext<TestState> context) {
                    notificationCount.incrementAndGet();
                }
            };
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                .from(TestState.RUNNING)
                .to(TestState.STOPPED)
                .on(TestEvent.STOP));
            for (int i = 0; i < 10; i++) {
                stateMachine.addListener(countingListener);
            }
            AtomicInteger counter = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(20);

            
            for (int i = 0; i < 20; i++) {
                final int eventId = counter.getAndIncrement();
                executorService.submit(() -> {
                    try {
                        stateMachine.transition(eventId % 2 == 0 ? TestEvent.START : TestEvent.STOP).join();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();

            
            
            assertThat(notificationCount.get()).isGreaterThan(0);
        }

        @Test
        void testConcurrentCanTransition_shouldNotThrowException()
            throws InterruptedException {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            AtomicInteger queryCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(100);

            
            for (int i = 0; i < 100; i++) {
                executorService.submit(() -> {
                    try {
                        stateMachine.canTransition(TestEvent.START);
                        queryCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();

            
            assertThat(queryCount.get()).isEqualTo(100);
        }
    }

    @Nested
    class StateTransitionContextTests {

        @Test
        void testStateTransitionContext_of_shouldCreateContextWithCorrectValues() {
            
            TestState from = TestState.INIT;
            TestState to = TestState.RUNNING;
            TestEvent event = TestEvent.START;

            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(from, to, event);

            
            assertThat(context.getFromState()).isEqualTo(from);
            assertThat(context.getToState()).isEqualTo(to);
            assertThat(context.getEvent()).isEqualTo(event);
        }

        @Test
        void testStateTransitionContext_withMetadata_shouldStoreMetadata() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            
            context.withMetadata("key1", "value1");
            context.withMetadata("key2", 123);
            context.withMetadata("key3", true);

            
            assertThat(context.getMetadata("key1")).isEqualTo("value1");
            assertThat(context.getMetadata("key2")).isEqualTo(123);
            assertThat(context.getMetadata("key3")).isEqualTo(true);
        }

        @Test
        void testStateTransitionContext_withMetadata_chaining_shouldWork() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            
            StateTransitionContext<TestState> result = context
                .withMetadata("key1", "value1")
                .withMetadata("key2", "value2");

            
            assertThat(result).isSameAs(context);
            assertThat(context.getMetadata("key1")).isEqualTo("value1");
            assertThat(context.getMetadata("key2")).isEqualTo("value2");
        }

        @Test
        void testStateTransitionContext_getMetadata_withType_shouldReturnTypedValue() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);
            context.withMetadata("string", "test");
            context.withMetadata("integer", 123);
            context.withMetadata("boolean", true);

            
            String stringValue = context.getMetadata("string", String.class);
            Integer integerValue = context.getMetadata("integer", Integer.class);
            Boolean booleanValue = context.getMetadata("boolean", Boolean.class);

            
            assertThat(stringValue).isEqualTo("test");
            assertThat(integerValue).isEqualTo(123);
            assertThat(booleanValue).isEqualTo(true);
        }

        @Test
        void testStateTransitionContext_getMetadata_withWrongType_shouldReturnNull() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);
            context.withMetadata("value", "string");

            
            Integer result = context.getMetadata("value", Integer.class);

            
            assertThat(result).isNull();
        }

        @Test
        void testStateTransitionContext_getMetadata_nonexistentKey_shouldReturnNull() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            
            Object result = context.getMetadata("nonexistent");

            
            assertThat(result).isNull();
        }

        @Test
        void testStateTransitionContext_withError_shouldStoreError() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);
            Throwable error = new RuntimeException("Test error");

            
            context.withError(error);

            
            assertThat(context.getError()).isSameAs(error);
            assertThat(context.getError().getMessage()).isEqualTo("Test error");
        }

        @Test
        void testStateTransitionContext_metadataMap_shouldBeModifiable() {
            
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            
            context.withMetadata("key", "value");
            context.withMetadata("key", "newValue");

            
            assertThat(context.getMetadata("key")).isEqualTo("newValue");
        }
    }

    @Nested
    class StateTransitionTests {

        @Test
        void testStateTransitionBuilder_shouldBuildCorrectTransition() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            
            boolean matches = transition.matches(TestState.INIT, TestEvent.START);

            
            assertThat(transition.getFrom()).isEqualTo(TestState.INIT);
            assertThat(transition.getTo()).isEqualTo(TestState.RUNNING);
            assertThat(transition.getEvent()).isEqualTo(TestEvent.START);
            assertThat(matches).isTrue();
        }

        @Test
        void testStateTransition_matches_withCorrectStateAndEvent_shouldReturnTrue() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            
            boolean result = transition.matches(TestState.INIT, TestEvent.START);

            
            assertThat(result).isTrue();
        }

        @Test
        void testStateTransition_matches_withWrongState_shouldReturnFalse() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            
            boolean result = transition.matches(TestState.RUNNING, TestEvent.START);

            
            assertThat(result).isFalse();
        }

        @Test
        void testStateTransition_matches_withWrongEvent_shouldReturnFalse() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            
            boolean result = transition.matches(TestState.INIT, TestEvent.STOP);

            
            assertThat(result).isFalse();
        }

        @Test
        void testStateTransition_matches_withNullFrom_shouldMatchAnyState() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(null)
                    .to(TestState.FAILED)
                    .on(TestEvent.FAIL)
                    .build();

            
            boolean initMatches = transition.matches(TestState.INIT, TestEvent.FAIL);
            boolean runningMatches = transition.matches(TestState.RUNNING, TestEvent.FAIL);

            
            assertThat(initMatches).isTrue();
            assertThat(runningMatches).isTrue();
        }

        @Test
        void testStateTransition_matches_withNullEvent_shouldMatchAnyEvent() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.RUNNING)
                    .to(TestState.FAILED)
                    .on(null)
                    .build();

            
            boolean startMatches = transition.matches(TestState.RUNNING, TestEvent.START);
            boolean stopMatches = transition.matches(TestState.RUNNING, TestEvent.STOP);

            
            assertThat(startMatches).isTrue();
            assertThat(stopMatches).isTrue();
        }

        @Test
        void testStateTransition_canApply_withoutGuard_shouldReturnTrue() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START, context);

            
            assertThat(canApply).isTrue();
        }

        @Test
        void testStateTransition_canApply_withPassingGuard_shouldReturnTrue() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(ctx -> true)
                    .build();
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START, context);

            
            assertThat(canApply).isTrue();
        }

        @Test
        void testStateTransition_canApply_withFailingGuard_shouldReturnFalse() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(ctx -> false)
                    .build();
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START, context);

            
            assertThat(canApply).isFalse();
        }

        @Test
        void testStateTransition_canApply_withWrongEvent_shouldReturnFalse() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.STOP);

            
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.STOP, context);

            
            assertThat(canApply).isFalse();
        }

        @Test
        void testStateTransition_guardWithContext_shouldUseContext() {
            
            AtomicReference<StateTransitionContext<TestState>> capturedContext =
                new AtomicReference<>();
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(ctx -> {
                        capturedContext.set(ctx);
                        return "allowed".equals(ctx.getMetadata("permission"));
                    })
                    .build();
            StateTransitionContext<TestState> context =
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);
            context.withMetadata("permission", "allowed");

            
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START, context);

            
            assertThat(canApply).isTrue();
            assertThat(capturedContext.get()).isSameAs(context);
        }
    }

    @Nested
    class EdgeCasesTests {

        @Test
        void testTransition_withNullEvent_shouldNotThrowException() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.FAILED)
                .on(null));

            
            CompletableFuture<Boolean> result = stateMachine.transition(null);

            
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.FAILED);
        }

        @Test
        void testCanTransition_withNullEvent_shouldWork() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.FAILED)
                .on(null));

            
            boolean canTransition = stateMachine.canTransition(null);

            
            assertThat(canTransition).isTrue();
        }

        @Test
        void testMultipleTransitionsWithSameFromAndEvent_shouldUseFirstMatch() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.STOPPED)
                .on(TestEvent.START));

            
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testTransition_afterFailedTransition_shouldStillWork() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            
            CompletableFuture<Boolean> firstResult = stateMachine.transition(TestEvent.STOP);
            CompletableFuture<Boolean> secondResult = stateMachine.transition(TestEvent.START);

            
            assertThat(firstResult.join()).isFalse();
            assertThat(secondResult.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testTransition_chain_shouldFollowAllSteps() {
            
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                .from(TestState.RUNNING)
                .to(TestState.SUCCESS)
                .on(TestEvent.COMPLETE));
            stateMachine.addTransition(builder -> builder
                .from(TestState.SUCCESS)
                .to(TestState.STOPPED)
                .on(TestEvent.STOP));

            
            boolean step1 = stateMachine.transition(TestEvent.START).join();
            boolean step2 = stateMachine.transition(TestEvent.COMPLETE).join();
            boolean step3 = stateMachine.transition(TestEvent.STOP).join();

            
            assertThat(step1).isTrue();
            assertThat(step2).isTrue();
            assertThat(step3).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.STOPPED);
        }

        @Test
        void testAddListener_returnThis_shouldAllowChaining() {
            
            @SuppressWarnings("unchecked")
            StateChangeListener<TestState> mockListener1 =
                org.mockito.Mockito.mock(StateChangeListener.class);
            @SuppressWarnings("unchecked")
            StateChangeListener<TestState> mockListener2 =
                org.mockito.Mockito.mock(StateChangeListener.class);
            stateMachine.addTransition(builder -> builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START));

            
            StateMachine<TestState, TestEvent> result = stateMachine
                .addListener(mockListener1)
                .addListener(mockListener2);

            
            assertThat(result).isSameAs(stateMachine);
            stateMachine.transition(TestEvent.START).join();
            org.mockito.Mockito.verify(mockListener1).onStateChange(any(), any(), any());
            org.mockito.Mockito.verify(mockListener2).onStateChange(any(), any(), any());
        }

        @Test
        void testAddTransition_returnThis_shouldAllowChaining() {
            
            StateTransition<TestState, TestEvent> transition =
                StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            
            StateMachine<TestState, TestEvent> result = stateMachine.addTransition(transition);

            
            assertThat(result).isSameAs(stateMachine);
            assertThat(stateMachine.canTransition(TestEvent.START)).isTrue();
        }
    }
}
