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

import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TaskStateMachineConfigTest {

    @Nested
    class CreateTests {

        @Test
        void create_initialStateIsInit() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();

            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT);
        }

        @Test
        void create_initToAssigned_shouldSucceed() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();

            boolean ok = machine.transition(TaskEvent.ASSIGN_TASK).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.ASSIGNED);
        }

        @Test
        void create_assignedToStarting_shouldSucceed() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();
            machine.transition(TaskEvent.ASSIGN_TASK).join();

            boolean ok = machine.transition(TaskEvent.START_TASK).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.STARTING);
        }

        @Test
        void create_startingToOngoing_shouldSucceed() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();
            machine.transition(TaskEvent.ASSIGN_TASK).join();
            machine.transition(TaskEvent.START_TASK).join();

            boolean ok = machine.transition(TaskEvent.ONGOING).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.ONGOING);
        }

        @Test
        void create_ongoingToShutting_shouldSucceed() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();
            machine.transition(TaskEvent.ASSIGN_TASK).join();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.ONGOING).join();

            boolean ok = machine.transition(TaskEvent.SHUTTING).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.SHUTTING);
        }

        @Test
        void create_startingToShutting_shouldSucceed() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();
            machine.transition(TaskEvent.ASSIGN_TASK).join();
            machine.transition(TaskEvent.START_TASK).join();

            boolean ok = machine.transition(TaskEvent.SHUTTING).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.SHUTTING);
        }

        @Test
        void create_fullNormalFlow_shouldEndAtShutdown() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();

            machine.transition(TaskEvent.ASSIGN_TASK).join();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.ONGOING).join();
            machine.transition(TaskEvent.SHUTTING).join();
            machine.transition(TaskEvent.SHUTDOWN).join();

            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.SHUTDOWN);
        }

        @Test
        void create_userStopFlow_shouldEndAtStopped() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();
            machine.transition(TaskEvent.ASSIGN_TASK).join();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.ONGOING).join();
            machine.transition(TaskEvent.SHUTTING).join();

            boolean ok = machine.transition(TaskEvent.STOP).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.STOPPED);
        }

        @Test
        void create_timeoutDuringShutting_shouldEndAtTimeout() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();
            machine.transition(TaskEvent.ASSIGN_TASK).join();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.ONGOING).join();
            machine.transition(TaskEvent.SHUTTING).join();

            boolean ok = machine.transition(TaskEvent.TIMEOUT).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.TIMEOUT);
        }

        @Test
        void create_globalFailure_shouldReachFailedFromAnyState() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();

            boolean ok = machine.transition(TaskEvent.FAILURE).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.FAILED);
        }

        @Test
        void create_globalTimeout_shouldReachTimeoutFromAnyState() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();

            boolean ok = machine.transition(TaskEvent.TIMEOUT).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.TIMEOUT);
        }

        @Test
        void create_globalInterrupt_shouldReachStoppedFromAnyState() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();

            boolean ok = machine.transition(TaskEvent.INTERRUPT).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.STOPPED);
        }

        @Test
        void create_invalidTransition_shouldFail() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();

            
            boolean ok = machine.transition(TaskEvent.ONGOING).join();

            assertThat(ok).isFalse();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT);
        }

        @Test
        void create_canTransition_shouldReturnCorrectResult() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();

            assertThat(machine.canTransition(TaskEvent.ASSIGN_TASK)).isTrue();
            assertThat(machine.canTransition(TaskEvent.FAILURE)).isTrue();
            assertThat(machine.canTransition(TaskEvent.TIMEOUT)).isTrue();
            assertThat(machine.canTransition(TaskEvent.INTERRUPT)).isTrue();
            
            assertThat(machine.canTransition(TaskEvent.START_TASK)).isFalse();
            assertThat(machine.canTransition(TaskEvent.ONGOING)).isFalse();
            assertThat(machine.canTransition(TaskEvent.SHUTTING)).isFalse();
        }

        @Test
        void create_reset_shouldReturnToInitialState() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();
            machine.transition(TaskEvent.ASSIGN_TASK).join();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.ONGOING).join();

            machine.reset();

            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT);
        }

        @Test
        void create_failureAfterOngoing_shouldSucceed() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create();
            machine.transition(TaskEvent.ASSIGN_TASK).join();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.ONGOING).join();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.ONGOING);

            boolean ok = machine.transition(TaskEvent.FAILURE).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.FAILED);
        }

        @Test
        void create_initialStateAssigned_canStartTask() {
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create(TaskStage.ASSIGNED);

            boolean ok = machine.transition(TaskEvent.START_TASK).join();

            assertThat(ok).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.STARTING);
        }

        @Test
        void create_terminalState_shouldRejectLaterEvents() {
            for (TaskStage terminalStage : new TaskStage[] {
                TaskStage.SHUTDOWN,
                TaskStage.STOPPED,
                TaskStage.FAILED,
                TaskStage.TIMEOUT
            }) {
                StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.create(terminalStage);

                assertThat(machine.transition(TaskEvent.ASSIGN_TASK).join()).isFalse();
                assertThat(machine.transition(TaskEvent.START_TASK).join()).isFalse();
                assertThat(machine.transition(TaskEvent.ONGOING).join()).isFalse();
                assertThat(machine.transition(TaskEvent.SHUTTING).join()).isFalse();
                assertThat(machine.transition(TaskEvent.SHUTDOWN).join()).isFalse();
                assertThat(machine.transition(TaskEvent.STOP).join()).isFalse();
                assertThat(machine.transition(TaskEvent.INTERRUPT).join()).isFalse();
                assertThat(machine.transition(TaskEvent.TIMEOUT).join()).isFalse();
                assertThat(machine.transition(TaskEvent.FAILURE).join()).isFalse();
                assertThat(machine.getCurrentState()).isEqualTo(terminalStage);
            }
        }
    }

    @Nested
    class StateMachineIndependentTests {

        @Test
        void twoInstances_resetAffectsOnlyOne() {
            StateMachine<TaskStage, TaskEvent> machine1 = TaskStateMachineConfig.create();
            StateMachine<TaskStage, TaskEvent> machine2 = TaskStateMachineConfig.create();
            machine1.transition(TaskEvent.ASSIGN_TASK).join();
            machine2.transition(TaskEvent.ASSIGN_TASK).join();
            machine1.transition(TaskEvent.START_TASK).join();
            machine2.transition(TaskEvent.START_TASK).join();
            machine1.transition(TaskEvent.ONGOING).join();

            machine1.reset();

            assertThat(machine1.getCurrentState()).isEqualTo(TaskStage.INIT);
            assertThat(machine2.getCurrentState()).isEqualTo(TaskStage.STARTING);
        }
    }
}
