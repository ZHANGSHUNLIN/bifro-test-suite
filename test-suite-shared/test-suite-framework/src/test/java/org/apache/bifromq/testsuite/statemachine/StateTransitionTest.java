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

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            
            StateTransition<TestState, TestEvent> transition = builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .build();

            
            assertThat(transition.getFrom()).isEqualTo(TestState.INIT);
            assertThat(transition.getTo()).isEqualTo(TestState.RUNNING);
            assertThat(transition.getEvent()).isEqualTo(TestEvent.START);
        }

        @Test
        void testBuilder_withGuard_shouldBuildTransitionWithGuard() {
            
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            
            StateTransition<TestState, TestEvent> transition = builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .guard(context -> context.getFromState() == TestState.INIT)
                .build();

            
            assertThat(transition.canApply(TestState.INIT, TestEvent.START,
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START)))
                .isTrue();
            assertThat(transition.canApply(TestState.RUNNING, TestEvent.START,
                StateTransitionContext.of(TestState.RUNNING, TestState.FAILED, TestEvent.START)))
                .isFalse();
        }

        @Test
        void testBuilder_withoutGuard_shouldBuildTransitionWithoutGuard() {
            
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            
            StateTransition<TestState, TestEvent> transition = builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .build();

            
            assertThat(transition.canApply(TestState.INIT, TestEvent.START,
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START)))
                .isTrue();
        }

        @Test
        void testBuilder_withNullFrom_shouldBuildTransitionWithNullFrom() {
            
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            
            StateTransition<TestState, TestEvent> transition = builder
                .from(null)
                .to(TestState.FAILED)
                .on(TestEvent.FAIL)
                .build();

            
            assertThat(transition.getFrom()).isNull();
            assertThat(transition.getTo()).isEqualTo(TestState.FAILED);
            assertThat(transition.getEvent()).isEqualTo(TestEvent.FAIL);
        }

        @Test
        void testBuilder_withNullEvent_shouldBuildTransitionWithNullEvent() {
            
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            
            StateTransition<TestState, TestEvent> transition = builder
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(null)
                .build();

            
            assertThat(transition.getFrom()).isEqualTo(TestState.INIT);
            assertThat(transition.getTo()).isEqualTo(TestState.RUNNING);
            assertThat(transition.getEvent()).isNull();
        }

        @Test
        void testBuilder_multipleCalls_shouldBuildCorrectly() {
            
            StateTransition.Builder<TestState, TestEvent> builder = StateTransition.builder();

            
            StateTransition<TestState, TestEvent> transition = builder
                .from(TestState.INIT)
                .from(TestState.RUNNING)
                .to(TestState.SUCCESS)
                .on(TestEvent.COMPLETE)
                .build();

            
            assertThat(transition.getFrom()).isEqualTo(TestState.RUNNING);
            assertThat(transition.getTo()).isEqualTo(TestState.SUCCESS);
            assertThat(transition.getEvent()).isEqualTo(TestEvent.COMPLETE);
        }
    }

    @Nested
    class MatchesTests {

        @Test
        void testMatches_withMatchingStateAndEvent_shouldReturnTrue() {
            
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .build();

            
            boolean matches = transition.matches(TestState.INIT, TestEvent.START);

            
            assertThat(matches).isTrue();
        }

        @Test
        void testMatches_withDifferentState_shouldReturnFalse() {
            
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .build();

            
            boolean matches = transition.matches(TestState.RUNNING, TestEvent.START);

            
            assertThat(matches).isFalse();
        }

        @Test
        void testMatches_withDifferentEvent_shouldReturnFalse() {
            
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .build();

            
            boolean matches = transition.matches(TestState.INIT, TestEvent.STOP);

            
            assertThat(matches).isFalse();
        }

        @Test
        void testMatches_withNullFrom_shouldMatchAnyState() {
            
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                .from(null)
                .to(TestState.FAILED)
                .on(TestEvent.FAIL)
                .build();

            
            boolean matchesInit = transition.matches(TestState.INIT, TestEvent.FAIL);
            boolean matchesRunning = transition.matches(TestState.RUNNING, TestEvent.FAIL);

            
            assertThat(matchesInit).isTrue();
            assertThat(matchesRunning).isTrue();
        }

        @Test
        void testMatches_withNullEvent_shouldMatchAnyEvent() {
            
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                .from(TestState.RUNNING)
                .to(TestState.STOPPED)
                .on(null)
                .build();

            
            boolean matchesStart = transition.matches(TestState.RUNNING, TestEvent.START);
            boolean matchesStop = transition.matches(TestState.RUNNING, TestEvent.STOP);

            
            assertThat(matchesStart).isTrue();
            assertThat(matchesStop).isTrue();
        }

        @Test
        void testMatches_withBothNull_shouldMatchAnyStateAndEvent() {
            
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                .from(null)
                .to(TestState.FAILED)
                .on(null)
                .build();

            
            boolean matchesInitStart = transition.matches(TestState.INIT, TestEvent.START);
            boolean matchesRunningStop = transition.matches(TestState.RUNNING, TestEvent.STOP);

            
            assertThat(matchesInitStart).isTrue();
            assertThat(matchesRunningStop).isTrue();
        }
    }

    @Nested
    class CanApplyTests {

        @Test
        void testCanApply_withoutGuard_shouldReturnTrueWhenMatches() {
            
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .build();

            
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START,
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START));

            
            assertThat(canApply).isTrue();
        }

        @Test
        void testCanApply_withoutGuard_shouldReturnFalseWhenNotMatches() {
            
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .build();

            
            boolean canApply = transition.canApply(TestState.RUNNING, TestEvent.START,
                StateTransitionContext.of(TestState.RUNNING, TestState.FAILED, TestEvent.START));

            
            assertThat(canApply).isFalse();
        }

        @Test
        void testCanApply_withGuardSatisfied_shouldReturnTrue() {
            
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .guard(context -> context.getFromState() == TestState.INIT)
                .build();

            
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START,
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START));

            
            assertThat(canApply).isTrue();
        }

        @Test
        void testCanApply_withGuardNotSatisfied_shouldReturnFalse() {
            
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .guard(context -> context.getFromState() != TestState.INIT)
                .build();

            
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START,
                StateTransitionContext.of(TestState.INIT, TestState.RUNNING, TestEvent.START));

            
            assertThat(canApply).isFalse();
        }

        @Test
        void testCanApply_guardCanAccessContextMetadata() {
            
            StateTransition<TestState, TestEvent> transition = StateTransition.<TestState, TestEvent>builder()
                .from(TestState.INIT)
                .to(TestState.RUNNING)
                .on(TestEvent.START)
                .guard(context -> "allowed".equals(context.getMetadata("permission")))
                .build();
            StateTransitionContext<TestState> context = StateTransitionContext.of(TestState.INIT,
                TestState.RUNNING, TestEvent.START);
            context.withMetadata("permission", "allowed");

            
            boolean canApply = transition.canApply(TestState.INIT, TestEvent.START, context);

            
            assertThat(canApply).isTrue();
        }

        @Test
        void testCanApply_shouldCheckMatchesBeforeGuard() {
            
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

            
            transition.canApply(TestState.RUNNING, TestEvent.START,
                StateTransitionContext.of(TestState.RUNNING, TestState.FAILED, TestEvent.START));

            
            assertThat(guardCalled.get()).isFalse();
        }
    }
}
