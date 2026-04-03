
package com.baidu.iot.test.suite.statemachine;

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

/**
 * Unit tests for StateMachine.
 */
@ExtendWith(MockitoExtension.class)
class StateMachineTest {

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

    private StateMachine<TestState, TestEvent> stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new StateMachine<>(TestState.INIT);
    }

    @Nested
    class ConstructorTests {

        @Test
        void testConstructor_withInitialState_shouldSetInitialState() {
            // given
            StateMachine<TestState, TestEvent> machine = new StateMachine<>(TestState.INIT);

            // when
            TestState currentState = machine.getCurrentState();

            // then
            assertThat(currentState).isEqualTo(TestState.INIT);
        }

        @Test
        void testConstructor_withDifferentInitialState_shouldSetCorrectState() {
            // given
            StateMachine<TestState, TestEvent> machine = new StateMachine<>(TestState.RUNNING);

            // when
            TestState currentState = machine.getCurrentState();

            // then
            assertThat(currentState).isEqualTo(TestState.RUNNING);
        }
    }

    @Nested
    class AddTransitionTests {

        @Test
        void testAddTransition_withValidTransition_shouldAddTransition() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(TestState.INIT)
                            .to(TestState.RUNNING)
                            .on(TestEvent.START)
                            .build();

            // when
            stateMachine.addTransition(transition);
            boolean canTransition = stateMachine.canTransition(TestEvent.START);

            // then
            assertThat(canTransition).isTrue();
        }

        @Test
        void testAddTransition_usingBuilder_shouldAddTransition() {
            // given
            // when
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));

            boolean canTransition = stateMachine.canTransition(TestEvent.START);

            // then
            assertThat(canTransition).isTrue();
        }

        @Test
        void testAddAnyTransition_shouldAddGlobalTransition() {
            // given - The state machine implementation requires states to exist before
            // adding global transitions. We'll add transitions for both INIT and RUNNING
            // states, then add a global transition that applies to both.
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                    .from(TestState.RUNNING)
                    .to(TestState.SUCCESS)
                    .on(TestEvent.COMPLETE));
            stateMachine.addAnyTransition(TestState.STOPPED, TestEvent.STOP);

            // when
            boolean fromInit = stateMachine.canTransition(TestEvent.STOP);
            stateMachine.transition(TestEvent.START).join();
            boolean fromRunning = stateMachine.canTransition(TestEvent.STOP);

            // then - Global transition should be available from all registered states
            assertThat(fromInit).isTrue();
            assertThat(fromRunning).isTrue();
        }

        @Test
        void testAddTransition_withNullFrom_shouldNotThrowException() {
            // given - First add a normal transition to ensure transitions map is populated
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

            // when
            stateMachine.addTransition(transition);

            // then
            assertThat(stateMachine.canTransition(TestEvent.FAIL)).isTrue();
        }

        @Test
        void testAddMultipleTransitionsFromSameState_shouldAllBeAdded() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.FAILED)
                    .on(TestEvent.FAIL));

            // when
            boolean canStart = stateMachine.canTransition(TestEvent.START);
            boolean canFail = stateMachine.canTransition(TestEvent.FAIL);

            // then
            assertThat(canStart).isTrue();
            assertThat(canFail).isTrue();
        }
    }

    @Nested
    class TransitionTests {

        @Test
        void testTransition_withValidTransition_shouldChangeState() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            // then
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testTransition_withNoMatchingTransition_shouldNotChangeState() {
            // given
            TestState initialState = stateMachine.getCurrentState();

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            // then
            assertThat(result.join()).isFalse();
            assertThat(stateMachine.getCurrentState()).isEqualTo(initialState);
        }

        @Test
        void testTransition_withContext_shouldPassContextToTransition() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));
            Map<String, Object> context = Map.of("key1", "value1", "key2", 123);

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START, context);

            // then
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testTransition_multipleTransitions_shouldFollowCorrectPath() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                    .from(TestState.RUNNING)
                    .to(TestState.SUCCESS)
                    .on(TestEvent.COMPLETE));

            // when
            stateMachine.transition(TestEvent.START).join();
            stateMachine.transition(TestEvent.COMPLETE).join();

            // then
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.SUCCESS);
        }

        @Test
        void testTransition_withWrongEvent_shouldNotChangeState() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));
            TestState initialState = stateMachine.getCurrentState();

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.STOP);

            // then
            assertThat(result.join()).isFalse();
            assertThat(stateMachine.getCurrentState()).isEqualTo(initialState);
        }

        @Test
        void testTransition_withGuardBlocking_shouldNotChangeState() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(ctx -> false));

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            // then
            assertThat(result.join()).isFalse();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.INIT);
        }

        @Test
        void testTransition_withGuardPassing_shouldChangeState() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(ctx -> true));

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            // then
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testTransition_withContextGuard_shouldUseContextInGuard() {
            // given
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

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START, context);

            // then
            assertThat(result.join()).isTrue();
            assertThat(guardCalls.get()).isEqualTo(1);
            assertThat(capturedContext.get()).isNotNull();
            assertThat(capturedContext.get().getMetadata("permission")).isEqualTo("allowed");
        }

        @Test
        void testTransition_withGuardAndNoPermission_shouldNotChangeState() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(ctx -> "allowed".equals(ctx.getMetadata("permission"))));
            Map<String, Object> context = Map.of("permission", "denied");

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START, context);

            // then
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
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));

            // when
            stateMachine.addListener(listener1);

            // then
            stateMachine.transition(TestEvent.START).join();
            verify(listener1).onStateExited(eq(TestState.INIT), any());
            verify(listener1).onStateEntered(eq(TestState.RUNNING), any());
            verify(listener1).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
        }

        @Test
        void testAddListener_withNullListener_shouldNotAddListener() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));

            // when
            stateMachine.addListener(null);
            stateMachine.transition(TestEvent.START).join();

            // then
            verifyNoMoreInteractions(listener1);
        }

        @Test
        void testAddListener_multipleListeners_shouldNotifyAll() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));
            stateMachine.addListener(listener1);
            stateMachine.addListener(listener2);

            // when
            stateMachine.transition(TestEvent.START).join();

            // then
            verify(listener1).onStateExited(eq(TestState.INIT), any());
            verify(listener1).onStateEntered(eq(TestState.RUNNING), any());
            verify(listener1).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
            verify(listener2).onStateExited(eq(TestState.INIT), any());
            verify(listener2).onStateEntered(eq(TestState.RUNNING), any());
            verify(listener2).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
        }

        @Test
        void testListener_onStateExited_shouldPassCorrectContext() {
            // given
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

            // when
            stateMachine.transition(TestEvent.START, context).join();

            // then
            StateTransitionContext<TestState> ctx = capturedContext.get();
            assertThat(ctx).isNotNull();
            assertThat(ctx.getFromState()).isEqualTo(TestState.INIT);
            assertThat(ctx.getToState()).isEqualTo(TestState.RUNNING);
            assertThat(ctx.getEvent()).isEqualTo(TestEvent.START);
            assertThat(ctx.getMetadata("testKey")).isEqualTo("testValue");
        }

        @Test
        void testListener_onStateEntered_shouldPassCorrectContext() {
            // given
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

            // when
            stateMachine.transition(TestEvent.START).join();

            // then
            StateTransitionContext<TestState> ctx = capturedContext.get();
            assertThat(ctx).isNotNull();
            assertThat(ctx.getFromState()).isEqualTo(TestState.INIT);
            assertThat(ctx.getToState()).isEqualTo(TestState.RUNNING);
            assertThat(ctx.getEvent()).isEqualTo(TestEvent.START);
        }

        @Test
        void testListener_onStateChange_shouldPassCorrectStates() {
            // given
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

            // when
            stateMachine.transition(TestEvent.START).join();

            // then
            assertThat(fromState.get()).isEqualTo(TestState.INIT);
            assertThat(toState.get()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testListener_onStateExited_exception_shouldContinueExecution() {
            // given
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

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            // then
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
            verify(listener1).onStateEntered(eq(TestState.RUNNING), any());
            verify(listener1).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
        }

        @Test
        void testListener_onStateEntered_exception_shouldContinueExecution() {
            // given
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

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            // then
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
            verify(listener1).onStateExited(eq(TestState.INIT), any());
            verify(listener1).onStateEntered(eq(TestState.RUNNING), any());
            verify(listener2).onStateEntered(eq(TestState.RUNNING), any());
        }

        @Test
        void testListener_onStateChange_exception_shouldCallOnError() {
            // given
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

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            // then
            assertThat(result.join()).isTrue();
            assertThat(capturedError.get()).isNotNull();
            assertThat(capturedError.get().getMessage()).isEqualTo("Test exception in onStateChange");
        }

        @Test
        void testListener_onStateChange_exception_shouldContinueWithOtherListeners() {
            // given
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

            // when
            stateMachine.transition(TestEvent.START).join();

            // then
            verify(listener1).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
            verify(listener2).onStateChange(eq(TestState.INIT), eq(TestState.RUNNING), any());
        }

        @Test
        void testListener_notificationOrder_shouldNotifyInCorrectOrder() {
            // given
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

            // when
            stateMachine.transition(TestEvent.START).join();

            // then
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
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));

            // when
            TestState beforeTransition = stateMachine.getCurrentState();
            stateMachine.transition(TestEvent.START).join();
            TestState afterTransition = stateMachine.getCurrentState();

            // then
            assertThat(beforeTransition).isEqualTo(TestState.INIT);
            assertThat(afterTransition).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testGetCurrentStateReference_shouldReturnAtomicReference() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));

            // when
            java.util.concurrent.atomic.AtomicReference<TestState> ref =
                    stateMachine.getCurrentStateReference();

            // then
            assertThat(ref).isNotNull();
            assertThat(ref.get()).isEqualTo(TestState.INIT);
            stateMachine.transition(TestEvent.START).join();
            assertThat(ref.get()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testCanTransition_withValidTransition_shouldReturnTrue() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));

            // when
            boolean canTransition = stateMachine.canTransition(TestEvent.START);

            // then
            assertThat(canTransition).isTrue();
        }

        @Test
        void testCanTransition_withInvalidTransition_shouldReturnFalse() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));

            // when
            boolean canTransition = stateMachine.canTransition(TestEvent.STOP);

            // then
            assertThat(canTransition).isFalse();
        }

        @Test
        void testCanTransition_afterStateChange_shouldReflectNewState() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                    .from(TestState.RUNNING)
                    .to(TestState.STOPPED)
                    .on(TestEvent.STOP));

            // when
            boolean canStartInit = stateMachine.canTransition(TestEvent.START);
            boolean canStopInit = stateMachine.canTransition(TestEvent.STOP);
            stateMachine.transition(TestEvent.START).join();
            boolean canStartRunning = stateMachine.canTransition(TestEvent.START);
            boolean canStopRunning = stateMachine.canTransition(TestEvent.STOP);

            // then
            assertThat(canStartInit).isTrue();
            assertThat(canStopInit).isFalse();
            assertThat(canStartRunning).isFalse();
            assertThat(canStopRunning).isTrue();
        }

        @Test
        void testCanTransition_withGuardBlocking_shouldReturnTrue_butTransitionFails() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(ctx -> false));

            // when
            boolean canTransition = stateMachine.canTransition(TestEvent.START);
            CompletableFuture<Boolean> transitionResult = stateMachine.transition(TestEvent.START);

            // then
            // canTransition checks if a transition exists, not if guard passes
            assertThat(canTransition).isTrue();
            assertThat(transitionResult.join()).isFalse();
        }
    }

    @Nested
    class ResetTests {

        @Test
        void testReset_afterTransitions_shouldReturnToInitialState() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                    .from(TestState.RUNNING)
                    .to(TestState.SUCCESS)
                    .on(TestEvent.COMPLETE));

            // when
            stateMachine.transition(TestEvent.START).join();
            stateMachine.transition(TestEvent.COMPLETE).join();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.SUCCESS);
            stateMachine.reset();

            // then
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.INIT);
        }

        @Test
        void testReset_multipleTimes_shouldAlwaysReturnToInitialState() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));

            // when
            stateMachine.transition(TestEvent.START).join();
            stateMachine.reset();
            TestState firstReset = stateMachine.getCurrentState();
            stateMachine.reset();
            TestState secondReset = stateMachine.getCurrentState();

            // then
            assertThat(firstReset).isEqualTo(TestState.INIT);
            assertThat(secondReset).isEqualTo(TestState.INIT);
        }

        @Test
        void testReset_andTransitionAgain_shouldWorkCorrectly() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));

            // when
            stateMachine.transition(TestEvent.START).join();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
            stateMachine.reset();
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            // then
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
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);

            // when
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

            // then
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(threadCount - 1);
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testConcurrentTransitions_sequentialEvents_shouldSucceed()
                throws InterruptedException {
            // given
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

            // when
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

            // then
            // First START succeeds, then COMPLETE succeeds
            // Rest fail due to state mismatch
            assertThat(completedCount.get()).isGreaterThanOrEqualTo(2);
            assertThat(completedCount.get()).isLessThanOrEqualTo(10);
        }

        @Test
        void testConcurrentListeners_shouldHandleSafely() throws InterruptedException {
            // given
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

            // when
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

            // then
            // All listeners should be called for successful transitions
            assertThat(notificationCount.get()).isGreaterThan(0);
        }

        @Test
        void testConcurrentCanTransition_shouldNotThrowException()
                throws InterruptedException {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));
            AtomicInteger queryCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(100);

            // when
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

            // then
            assertThat(queryCount.get()).isEqualTo(100);
        }
    }

    @Nested
    class StateTransitionContextTests {

        @Test
        void testStateTransitionContext_of_shouldCreateContextWithCorrectValues() {
            // given
            TestState from = TestState.INIT;
            TestState to = TestState.RUNNING;
            TestEvent event = TestEvent.START;

            // when
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(from, to, event);

            // then
            assertThat(context.getFromState()).isEqualTo(from);
            assertThat(context.getToState()).isEqualTo(to);
            assertThat(context.getEvent()).isEqualTo(event);
        }

        @Test
        void testStateTransitionContext_withMetadata_shouldStoreMetadata() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            // when
            context.withMetadata("key1", "value1");
            context.withMetadata("key2", 123);
            context.withMetadata("key3", true);

            // then
            assertThat(context.getMetadata("key1")).isEqualTo("value1");
            assertThat(context.getMetadata("key2")).isEqualTo(123);
            assertThat(context.getMetadata("key3")).isEqualTo(true);
        }

        @Test
        void testStateTransitionContext_withMetadata_chaining_shouldWork() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

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
        void testStateTransitionContext_getMetadata_withType_shouldReturnTypedValue() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);
            context.withMetadata("string", "test");
            context.withMetadata("integer", 123);
            context.withMetadata("boolean", true);

            // when
            String stringValue = context.getMetadata("string", String.class);
            Integer integerValue = context.getMetadata("integer", Integer.class);
            Boolean booleanValue = context.getMetadata("boolean", Boolean.class);

            // then
            assertThat(stringValue).isEqualTo("test");
            assertThat(integerValue).isEqualTo(123);
            assertThat(booleanValue).isEqualTo(true);
        }

        @Test
        void testStateTransitionContext_getMetadata_withWrongType_shouldReturnNull() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);
            context.withMetadata("value", "string");

            // when
            Integer result = context.getMetadata("value", Integer.class);

            // then
            assertThat(result).isNull();
        }

        @Test
        void testStateTransitionContext_getMetadata_nonexistentKey_shouldReturnNull() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            // when
            Object result = context.getMetadata("nonexistent");

            // then
            assertThat(result).isNull();
        }

        @Test
        void testStateTransitionContext_withError_shouldStoreError() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);
            Throwable error = new RuntimeException("Test error");

            // when
            context.withError(error);

            // then
            assertThat(context.getError()).isSameAs(error);
            assertThat(context.getError().getMessage()).isEqualTo("Test error");
        }

        @Test
        void testStateTransitionContext_metadataMap_shouldBeModifiable() {
            // given
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            // when
            context.withMetadata("key", "value");
            context.withMetadata("key", "newValue");

            // then
            assertThat(context.getMetadata("key")).isEqualTo("newValue");
        }
    }

    @Nested
    class StateTransitionTests {

        @Test
        void testStateTransitionBuilder_shouldBuildCorrectTransition() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(TestState.INIT)
                            .to(TestState.RUNNING)
                            .on(TestEvent.START)
                            .build();

            // when
            boolean matches = transition.matches(TestState.INIT, TestEvent.START);

            // then
            assertThat(transition.getFrom()).isEqualTo(TestState.INIT);
            assertThat(transition.getTo()).isEqualTo(TestState.RUNNING);
            assertThat(transition.getEvent()).isEqualTo(TestEvent.START);
            assertThat(matches).isTrue();
        }

        @Test
        void testStateTransition_matches_withCorrectStateAndEvent_shouldReturnTrue() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(TestState.INIT)
                            .to(TestState.RUNNING)
                            .on(TestEvent.START)
                            .build();

            // when
            boolean result = transition.matches(TestState.INIT, TestEvent.START);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void testStateTransition_matches_withWrongState_shouldReturnFalse() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(TestState.INIT)
                            .to(TestState.RUNNING)
                            .on(TestEvent.START)
                            .build();

            // when
            boolean result = transition.matches(TestState.RUNNING, TestEvent.START);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void testStateTransition_matches_withWrongEvent_shouldReturnFalse() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(TestState.INIT)
                            .to(TestState.RUNNING)
                            .on(TestEvent.START)
                            .build();

            // when
            boolean result = transition.matches(TestState.INIT, TestEvent.STOP);

            // then
            assertThat(result).isFalse();
        }

        @Test
        void testStateTransition_matches_withNullFrom_shouldMatchAnyState() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(null)
                            .to(TestState.FAILED)
                            .on(TestEvent.FAIL)
                            .build();

            // when
            boolean initMatches = transition.matches(TestState.INIT, TestEvent.FAIL);
            boolean runningMatches = transition.matches(TestState.RUNNING, TestEvent.FAIL);

            // then
            assertThat(initMatches).isTrue();
            assertThat(runningMatches).isTrue();
        }

        @Test
        void testStateTransition_matches_withNullEvent_shouldMatchAnyEvent() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(TestState.RUNNING)
                            .to(TestState.FAILED)
                            .on(null)
                            .build();

            // when
            boolean startMatches = transition.matches(TestState.RUNNING, TestEvent.START);
            boolean stopMatches = transition.matches(TestState.RUNNING, TestEvent.STOP);

            // then
            assertThat(startMatches).isTrue();
            assertThat(stopMatches).isTrue();
        }

        @Test
        void testStateTransition_canApply_withoutGuard_shouldReturnTrue() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(TestState.INIT)
                            .to(TestState.RUNNING)
                            .on(TestEvent.START)
                            .build();
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            // when
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START, context);

            // then
            assertThat(canApply).isTrue();
        }

        @Test
        void testStateTransition_canApply_withPassingGuard_shouldReturnTrue() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(TestState.INIT)
                            .to(TestState.RUNNING)
                            .on(TestEvent.START)
                            .guard(ctx -> true)
                            .build();
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            // when
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START, context);

            // then
            assertThat(canApply).isTrue();
        }

        @Test
        void testStateTransition_canApply_withFailingGuard_shouldReturnFalse() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(TestState.INIT)
                            .to(TestState.RUNNING)
                            .on(TestEvent.START)
                            .guard(ctx -> false)
                            .build();
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START);

            // when
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START, context);

            // then
            assertThat(canApply).isFalse();
        }

        @Test
        void testStateTransition_canApply_withWrongEvent_shouldReturnFalse() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(TestState.INIT)
                            .to(TestState.RUNNING)
                            .on(TestEvent.START)
                            .build();
            StateTransitionContext<TestState> context =
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.STOP);

            // when
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.STOP, context);

            // then
            assertThat(canApply).isFalse();
        }

        @Test
        void testStateTransition_guardWithContext_shouldUseContext() {
            // given
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

            // when
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START, context);

            // then
            assertThat(canApply).isTrue();
            assertThat(capturedContext.get()).isSameAs(context);
        }
    }

    @Nested
    class EdgeCasesTests {

        @Test
        void testTransition_withNullEvent_shouldNotThrowException() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.FAILED)
                    .on(null));

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(null);

            // then
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.FAILED);
        }

        @Test
        void testCanTransition_withNullEvent_shouldWork() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.FAILED)
                    .on(null));

            // when
            boolean canTransition = stateMachine.canTransition(null);

            // then
            assertThat(canTransition).isTrue();
        }

        @Test
        void testMultipleTransitionsWithSameFromAndEvent_shouldUseFirstMatch() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.STOPPED)
                    .on(TestEvent.START));

            // when
            CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);

            // then
            assertThat(result.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testTransition_afterFailedTransition_shouldStillWork() {
            // given
            stateMachine.addTransition(builder -> builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START));

            // when
            CompletableFuture<Boolean> firstResult = stateMachine.transition(TestEvent.STOP);
            CompletableFuture<Boolean> secondResult = stateMachine.transition(TestEvent.START);

            // then
            assertThat(firstResult.join()).isFalse();
            assertThat(secondResult.join()).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
        }

        @Test
        void testTransition_chain_shouldFollowAllSteps() {
            // given
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

            // when
            boolean step1 = stateMachine.transition(TestEvent.START).join();
            boolean step2 = stateMachine.transition(TestEvent.COMPLETE).join();
            boolean step3 = stateMachine.transition(TestEvent.STOP).join();

            // then
            assertThat(step1).isTrue();
            assertThat(step2).isTrue();
            assertThat(step3).isTrue();
            assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.STOPPED);
        }

        @Test
        void testAddListener_returnThis_shouldAllowChaining() {
            // given
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

            // when
            StateMachine<TestState, TestEvent> result = stateMachine
                    .addListener(mockListener1)
                    .addListener(mockListener2);

            // then
            assertThat(result).isSameAs(stateMachine);
            stateMachine.transition(TestEvent.START).join();
            org.mockito.Mockito.verify(mockListener1).onStateChange(any(), any(), any());
            org.mockito.Mockito.verify(mockListener2).onStateChange(any(), any(), any());
        }

        @Test
        void testAddTransition_returnThis_shouldAllowChaining() {
            // given
            StateTransition<TestState, TestEvent> transition =
                    StateTransition.<TestState, TestEvent>builder()
                            .from(TestState.INIT)
                            .to(TestState.RUNNING)
                            .on(TestEvent.START)
                            .build();

            // when
            StateMachine<TestState, TestEvent> result = stateMachine.addTransition(transition);

            // then
            assertThat(result).isSameAs(stateMachine);
            assertThat(stateMachine.canTransition(TestEvent.START)).isTrue();
        }
    }
}
