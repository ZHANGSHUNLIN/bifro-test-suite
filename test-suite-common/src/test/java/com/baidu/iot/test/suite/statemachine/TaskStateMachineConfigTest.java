
package com.baidu.iot.test.suite.statemachine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import com.baidu.iot.test.suite.TaskEvent;
import com.baidu.iot.test.suite.TaskStage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TaskStateMachineConfig.
 */
class TaskStateMachineConfigTest {

    @Nested
    class CreateConnTests {

        @Test
        void testCreateConn_initialStateIsInit() {
            // given - none

            // when
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();

            // then
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT);
        }

        @Test
        void testCreateConn_transitionInitToStart_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();

            // when
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.START_TASK);

            // then
            assertThat(result.join()).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.START);
        }

        @Test
        void testCreateConn_transitionStartToInitClient_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();
            machine.transition(TaskEvent.START_TASK).join();

            // when
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.INIT_CONN);

            // then
            assertThat(result.join()).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT_CLIENT);
        }

        @Test
        void testCreateConn_fullNormalFlow_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();

            // when
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.INIT_CONN).join();
            machine.transition(TaskEvent.START_CONN_CLIENT_TASK).join();

            // then
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.ONGOING);
        }

        @Test
        void testCreateConn_transitionOngoingToShutting_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.INIT_CONN).join();
            machine.transition(TaskEvent.START_CONN_CLIENT_TASK).join();

            // when
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.SHUTTING);

            // then
            assertThat(result.join()).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.SHUTTING);
        }

        @Test
        void testCreateConn_transitionShuttingToShutdown_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.INIT_CONN).join();
            machine.transition(TaskEvent.START_CONN_CLIENT_TASK).join();
            machine.transition(TaskEvent.SHUTTING).join();

            // when
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.SHUTDOWN);

            // then
            assertThat(result.join()).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.SHUTDOWN);
        }

        @Test
        void testCreateConn_transitionOngoingToStopped_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.INIT_CONN).join();
            machine.transition(TaskEvent.START_CONN_CLIENT_TASK).join();

            // when
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.STOP);

            // then
            assertThat(result.join()).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.STOPPED);
        }

        @Test
        void testCreateConn_globalFailureTransition_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();

            // when - can fail from any state
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.FAILURE);

            // then
            assertThat(result.join()).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.FAILED);
        }

        @Test
        void testCreateConn_globalTimeoutTransition_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();

            // when - can timeout from any state
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.TIMEOUT);

            // then
            assertThat(result.join()).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.TIMEOUT);
        }

        @Test
        void testCreateConn_invalidTransition_shouldFail() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();

            // when - trying to transition directly from INIT to ONGOING
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.START_CONN_CLIENT_TASK);

            // then
            assertThat(result.join()).isFalse();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT);
        }

        @Test
        void testCreateConn_canTransition_shouldReturnCorrectResult() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();

            // when/then
            assertThat(machine.canTransition(TaskEvent.START_TASK)).isTrue();
            assertThat(machine.canTransition(TaskEvent.FAILURE)).isTrue();
            assertThat(machine.canTransition(TaskEvent.TIMEOUT)).isTrue();
            assertThat(machine.canTransition(TaskEvent.INIT_CONN)).isFalse();
            assertThat(machine.canTransition(TaskEvent.START_CONN_CLIENT_TASK)).isFalse();
        }

        @Test
        void testCreateConn_resetToInitialState_shouldWork() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.INIT_CONN).join();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT_CLIENT);

            // when
            machine.reset();

            // then
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT);
        }

        @Test
        void testCreateConn_failureAfterRunning_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createConn();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.INIT_CONN).join();
            machine.transition(TaskEvent.START_CONN_CLIENT_TASK).join();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.ONGOING);

            // when
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.FAILURE);

            // then
            assertThat(result.join()).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.FAILED);
        }
    }

    @Nested
    class CreatePubSubTests {

        @Test
        void testCreatePubSub_initialStateIsInit() {
            // given - none

            // when
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createPubSub();

            // then
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT);
        }

        @Test
        void testCreatePubSub_transitionInitToInitPub_shouldFailBecauseNoStartTransition() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createPubSub();

            // when - cannot transition from INIT directly
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.INIT_PUB);

            // then
            assertThat(result.join()).isFalse();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT);
        }
    }

    @Nested
    class CreateKafkaTests {

        @Test
        void testCreateKafka_initialStateIsInit() {
            // given - none

            // when
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createKafka();

            // then
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT);
        }

        @Test
        void testCreateKafka_fullNormalFlow_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createKafka();

            // when
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.INIT_KAFKA).join();
            machine.transition(TaskEvent.START_KAFKA_TASK).join();
            machine.transition(TaskEvent.SHUTTING).join();

            // then
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.ONGOING);
        }

        @Test
        void testCreateKafka_transitionOngoingToShutdown_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createKafka();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.INIT_KAFKA).join();
            machine.transition(TaskEvent.START_KAFKA_TASK).join();
            machine.transition(TaskEvent.SHUTTING).join();

            // when
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.SHUTDOWN);

            // then
            assertThat(result.join()).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.SHUTDOWN);
        }

        @Test
        void testCreateKafka_shutdownToShutting_shouldSucceed() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createKafka();
            machine.transition(TaskEvent.START_TASK).join();
            machine.transition(TaskEvent.INIT_KAFKA).join();
            machine.transition(TaskEvent.START_KAFKA_TASK).join();
            machine.transition(TaskEvent.SHUTTING).join();
            machine.transition(TaskEvent.SHUTDOWN).join();

            // when
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.STOP);

            // then
            assertThat(result.join()).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.SHUTTING);
        }

        @Test
        void testCreateKafka_globalTransitions_shouldWork() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createKafka();
            machine.transition(TaskEvent.START_TASK).join();

            // when
            CompletableFuture<Boolean> failureResult = machine.transition(TaskEvent.FAILURE);
            StateMachine<TaskStage, TaskEvent> timeoutMachine = TaskStateMachineConfig.createKafka();
            CompletableFuture<Boolean> timeoutResult = timeoutMachine.transition(TaskEvent.TIMEOUT);

            // then
            assertThat(failureResult.join()).isTrue();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.FAILED);
            assertThat(timeoutResult.join()).isTrue();
            assertThat(timeoutMachine.getCurrentState()).isEqualTo(TaskStage.TIMEOUT);
        }

        @Test
        void testCreateKafka_invalidTransition_shouldFail() {
            // given
            StateMachine<TaskStage, TaskEvent> machine = TaskStateMachineConfig.createKafka();

            // when - trying to skip START stage
            CompletableFuture<Boolean> result = machine.transition(TaskEvent.INIT_KAFKA);

            // then
            assertThat(result.join()).isFalse();
            assertThat(machine.getCurrentState()).isEqualTo(TaskStage.INIT);
        }
    }

    @Nested
    class StateMachineIndependentTests {

        @Test
        void testCreateMultipleStateMachines_shouldBeIndependent() {
            // given
            StateMachine<TaskStage, TaskEvent> connMachine = TaskStateMachineConfig.createConn();
            StateMachine<TaskStage, TaskEvent> kafkaMachine = TaskStateMachineConfig.createKafka();

            // when
            connMachine.transition(TaskEvent.START_TASK).join();
            kafkaMachine.transition(TaskEvent.START_TASK).join();
            kafkaMachine.transition(TaskEvent.INIT_KAFKA).join();

            // then - each machine should maintain its own state
            assertThat(connMachine.getCurrentState()).isEqualTo(TaskStage.START);
            assertThat(kafkaMachine.getCurrentState()).isEqualTo(TaskStage.INIT_KAFKA_CLIENT);
        }

        @Test
        void testStateMachineReset_shouldAffectOnlyOneInstance() {
            // given
            StateMachine<TaskStage, TaskEvent> machine1 = TaskStateMachineConfig.createConn();
            StateMachine<TaskStage, TaskEvent> machine2 = TaskStateMachineConfig.createConn();
            machine1.transition(TaskEvent.START_TASK).join();
            machine2.transition(TaskEvent.START_TASK).join();
            machine1.transition(TaskEvent.INIT_CONN).join();

            // when
            machine1.reset();

            // then
            assertThat(machine1.getCurrentState()).isEqualTo(TaskStage.INIT);
            assertThat(machine2.getCurrentState()).isEqualTo(TaskStage.START);
        }
    }
}
