
package com.baidu.iot.test.suite.statemachine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for StateTransition.
 */
class StateTransitionTest {

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
    class BuilderTests {

        @Test
        void testBuilder_fromToOn_shouldBuildValidTransition() {
            // given
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            // when
            StateTransition<TestState, TestEvent> transition = builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            // then
            assertThat(transition.getFrom()).isEqualTo(TestState.INIT);
            assertThat(transition.getTo()).isEqualTo(TestState.RUNNING);
            assertThat(transition.getEvent()).isEqualTo(TestEvent.START);
        }

        @Test
        void testBuilder_withGuard_shouldBuildTransitionWithGuard() {
            // given
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            // when
            StateTransition<TestState, TestEvent> transition = builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(context -> context.getFromState() == TestState.INIT)
                    .build();

            // then
            assertThat(transition.canApply(TestState.INIT, TestEvent.START,
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START)))
                    .isTrue();
            assertThat(transition.canApply(TestState.RUNNING, TestEvent.START,
                    StateTransitionContext.of(TestState.RUNNING, TestState.FAILED, TestEvent.START)))
                    .isFalse();
        }

        @Test
        void testBuilder_withoutGuard_shouldBuildTransitionWithoutGuard() {
            // given
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            // when
            StateTransition<TestState, TestEvent> transition = builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            // then
            assertThat(transition.canApply(TestState.INIT, TestEvent.START,
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START)))
                    .isTrue();
        }

        @Test
        void testBuilder_withNullFrom_shouldBuildTransitionWithNullFrom() {
            // given
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            // when
            StateTransition<TestState, TestEvent> transition = builder
                    .from(null)
                    .to(TestState.FAILED)
                    .on(TestEvent.FAIL)
                    .build();

            // then
            assertThat(transition.getFrom()).isNull();
            assertThat(transition.getTo()).isEqualTo(TestState.FAILED);
            assertThat(transition.getEvent()).isEqualTo(TestEvent.FAIL);
        }

        @Test
        void testBuilder_withNullEvent_shouldBuildTransitionWithNullEvent() {
            // given
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            // when
            StateTransition<TestState, TestEvent> transition = builder
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(null)
                    .build();

            // then
            assertThat(transition.getFrom()).isEqualTo(TestState.INIT);
            assertThat(transition.getTo()).isEqualTo(TestState.RUNNING);
            assertThat(transition.getEvent()).isNull();
        }

        @Test
        void testBuilder_multipleCalls_shouldBuildCorrectly() {
            // given
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            // when
            StateTransition<TestState, TestEvent> transition = builder
                    .from(TestState.INIT)
                    .from(TestState.RUNNING)
                    .to(TestState.SUCCESS)
                    .on(TestEvent.COMPLETE)
                    .build();

            // then - last call wins
            assertThat(transition.getFrom()).isEqualTo(TestState.RUNNING);
            assertThat(transition.getTo()).isEqualTo(TestState.SUCCESS);
            assertThat(transition.getEvent()).isEqualTo(TestEvent.COMPLETE);
        }
    }

    @Nested
    class MatchesTests {

        @Test
        void testMatches_withMatchingStateAndEvent_shouldReturnTrue() {
            // given
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            // when
            boolean matches = transition.matches(TestState.INIT, TestEvent.START);

            // then
            assertThat(matches).isTrue();
        }

        @Test
        void testMatches_withDifferentState_shouldReturnFalse() {
            // given
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            // when
            boolean matches = transition.matches(TestState.RUNNING, TestEvent.START);

            // then
            assertThat(matches).isFalse();
        }

        @Test
        void testMatches_withDifferentEvent_shouldReturnFalse() {
            // given
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            // when
            boolean matches = transition.matches(TestState.INIT, TestEvent.STOP);

            // then
            assertThat(matches).isFalse();
        }

        @Test
        void testMatches_withNullFrom_shouldMatchAnyState() {
            // given
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(null)
                    .to(TestState.FAILED)
                    .on(TestEvent.FAIL)
                    .build();

            // when
            boolean matchesInit = transition.matches(TestState.INIT, TestEvent.FAIL);
            boolean matchesRunning = transition.matches(TestState.RUNNING, TestEvent.FAIL);

            // then
            assertThat(matchesInit).isTrue();
            assertThat(matchesRunning).isTrue();
        }

        @Test
        void testMatches_withNullEvent_shouldMatchAnyEvent() {
            // given
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.RUNNING)
                    .to(TestState.STOPPED)
                    .on(null)
                    .build();

            // when
            boolean matchesStart = transition.matches(TestState.RUNNING, TestEvent.START);
            boolean matchesStop = transition.matches(TestState.RUNNING, TestEvent.STOP);

            // then
            assertThat(matchesStart).isTrue();
            assertThat(matchesStop).isTrue();
        }

        @Test
        void testMatches_withBothNull_shouldMatchAnyStateAndEvent() {
            // given
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(null)
                    .to(TestState.FAILED)
                    .on(null)
                    .build();

            // when
            boolean matchesInitStart = transition.matches(TestState.INIT, TestEvent.START);
            boolean matchesRunningStop = transition.matches(TestState.RUNNING, TestEvent.STOP);

            // then
            assertThat(matchesInitStart).isTrue();
            assertThat(matchesRunningStop).isTrue();
        }
    }

    @Nested
    class CanApplyTests {

        @Test
        void testCanApply_withoutGuard_shouldReturnTrueWhenMatches() {
            // given
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            // when
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START,
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START));

            // then
            assertThat(canApply).isTrue();
        }

        @Test
        void testCanApply_withoutGuard_shouldReturnFalseWhenNotMatches() {
            // given
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .build();

            // when
            boolean canApply = transition.canApply(TestState.RUNNING, TestEvent.START,
                    StateTransitionContext.of(TestState.RUNNING, TestState.FAILED, TestEvent.START));

            // then
            assertThat(canApply).isFalse();
        }

        @Test
        void testCanApply_withGuardSatisfied_shouldReturnTrue() {
            // given
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(context -> context.getFromState() == TestState.INIT)
                    .build();

            // when
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START,
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START));

            // then
            assertThat(canApply).isTrue();
        }

        @Test
        void testCanApply_withGuardNotSatisfied_shouldReturnFalse() {
            // given
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(context -> context.getFromState() != TestState.INIT)
                    .build();

            // when
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START,
                    StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START));

            // then
            assertThat(canApply).isFalse();
        }

        @Test
        void testCanApply_guardCanAccessContextMetadata() {
            // given
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(context -> "allowed".equals(context.getMetadata("permission")))
                    .build();
            StateTransitionContext<TestState> context = StateTransitionContext.of(TestState.INIT,
                    TestState.RUNNING, TestEvent.START);
            context.withMetadata("permission", "allowed");

            // when
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START, context);

            // then
            assertThat(canApply).isTrue();
        }

        @Test
        void testCanApply_shouldCheckMatchesBeforeGuard() {
            // given
            AtomicBoolean guardCalled = new AtomicBoolean(false);
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                    .from(TestState.INIT)
                    .to(TestState.RUNNING)
                    .on(TestEvent.START)
                    .guard(context -> {
                        guardCalled.set(true);
                        return true;
                    })
                    .build();

            // when
            transition.canApply(TestState.RUNNING, TestEvent.START,
                    StateTransitionContext.of(TestState.RUNNING, TestState.FAILED, TestEvent.START));

            // then - guard should not be called when matches returns false
            assertThat(guardCalled.get()).isFalse();
        }
    }
}
