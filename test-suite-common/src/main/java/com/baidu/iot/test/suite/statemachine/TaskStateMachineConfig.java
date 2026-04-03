
package com.baidu.iot.test.suite.statemachine;

import com.baidu.iot.test.suite.TaskEvent;
import com.baidu.iot.test.suite.TaskStage;

/**
 * Configuration for task state machine transitions.
 */
public final class TaskStateMachineConfig {

    private TaskStateMachineConfig() {
    }

    /**
     * Create a task state machine with all configured transitions.
     *
     * @return configured state machine
     */
//    public static StateMachine<TaskStage, TaskEvent> create() {
//        StateMachine<TaskStage, TaskEvent> machine = new StateMachine<>(TaskStage.INIT);
//
//        // === Normal flow transitions ===
//
//        // INIT -> START
//        addTransition(machine, TaskStage.INIT, TaskStage.START, TaskEvent.START_TASK);
//
//        // START -> INIT_PUB_CLIENT (for pubsub tasks)
//        addTransition(machine, TaskStage.START, TaskStage.INIT_PUB_CLIENT, TaskEvent.INIT_PUB);
//
//        // INIT_PUB_CLIENT -> INIT_SUB_CLIENT (for pubsub tasks)
//        addTransition(machine, TaskStage.INIT_PUB_CLIENT, TaskStage.INIT_SUB_CLIENT, TaskEvent.INIT_SUB);
//
//        // INIT_SUB_CLIENT -> ONGOING
//        addTransition(machine, TaskStage.INIT_SUB_CLIENT, TaskStage.ONGOING, TaskEvent.ALL_CLIENTS_READY);
//
//        // START -> CONNECTING (for conn tasks)
//        addTransition(machine, TaskStage.START, TaskStage.CONNECTING, TaskEvent.START_CLIENT_TASK);
//
//        // CONNECTING -> ONGOING (for conn tasks)
//        addTransition(machine, TaskStage.CONNECTING, TaskStage.ONGOING, TaskEvent.ALL_CLIENTS_READY);
//
//        // ONGOING -> SHUTDOWN (end of stress test)
//        addTransition(machine, TaskStage.ONGOING, TaskStage.SHUTDOWN, TaskEvent.SHUTDOWN);
//
//        // SHUTDOWN -> STOPPED
//        addTransition(machine, TaskStage.SHUTDOWN, TaskStage.STOPPED, TaskEvent.STOP);
//
//        // === Global transitions (from any state) ===
//
//        // Any state -> TIMEOUT
//        machine.addAnyTransition(TaskStage.TIMEOUT, TaskEvent.TIMEOUT);
//
//        // Any state -> FAILED (general failure)
//        machine.addAnyTransition(TaskStage.FAILED, TaskEvent.FAILURE);
//
//        return machine;
//    }


    public static StateMachine<TaskStage, TaskEvent> createConn() {
        StateMachine<TaskStage, TaskEvent> machine = new StateMachine<>(TaskStage.INIT);

        // === Normal flow transitions ===

        // INIT -> START
        addTransition(machine, TaskStage.INIT, TaskStage.START, TaskEvent.START_TASK);

        // START -> INIT_PUB_CLIENT (for pubsub tasks)
        addTransition(machine, TaskStage.START, TaskStage.INIT_CLIENT, TaskEvent.INIT_CONN);

        addTransition(machine, TaskStage.INIT_CLIENT, TaskStage.ONGOING, TaskEvent.START_CONN_CLIENT_TASK);

        // ONGOING -> SHUTDOWN (end of stress test)
        addTransition(machine, TaskStage.ONGOING, TaskStage.SHUTTING, TaskEvent.SHUTTING);

        addTransition(machine, TaskStage.ONGOING, TaskStage.STOPPED, TaskEvent.STOP);

        // SHUTDOWN -> STOPPED
        addTransition(machine, TaskStage.SHUTTING, TaskStage.SHUTDOWN, TaskEvent.SHUTDOWN);

        addTransition(machine, TaskStage.SHUTTING, TaskStage.SHUTDOWN, TaskEvent.STOP);

        // === Global transitions (from any state) ===

        // Any state -> TIMEOUT
        machine.addAnyTransition(TaskStage.TIMEOUT, TaskEvent.TIMEOUT);

        // Any state -> FAILED (general failure)
        machine.addAnyTransition(TaskStage.FAILED, TaskEvent.FAILURE);



        return machine;
    }

    public static StateMachine<TaskStage, TaskEvent> createPubSub() {
        StateMachine<TaskStage, TaskEvent> machine = new StateMachine<>(TaskStage.INIT);

        // === Normal flow transitions ===

        // INIT -> START
        addTransition(machine, TaskStage.INIT, TaskStage.START, TaskEvent.START_TASK);

        // START -> INIT_PUB_CLIENT (for pubsub tasks)
        addTransition(machine, TaskStage.START, TaskStage.INIT_PUB_CLIENT, TaskEvent.INIT_PUB);
        addTransition(machine, TaskStage.INIT_PUB_CLIENT, TaskStage.INIT_SUB_CLIENT, TaskEvent.PUB_READY);



        addTransition(machine, TaskStage.INIT_SUB_CLIENT, TaskStage.PUB_SUB_CLIENT_READY, TaskEvent.INIT_SUB);

        addTransition(machine, TaskStage.PUB_SUB_CLIENT_READY, TaskStage.PUB_SUB_CLIENT_START, TaskEvent.ALL_CLIENTS_READY);



        addTransition(machine, TaskStage.PUB_SUB_CLIENT_START, TaskStage.PUB_CLIENT_CONN, TaskEvent.PUB_CONN);

        addTransition(machine, TaskStage.PUB_CLIENT_CONN, TaskStage.SUB_CLIENT_CONN, TaskEvent.SUB_CONN);




        addTransition(machine, TaskStage.SUB_CLIENT_CONN, TaskStage.ONGOING, TaskEvent.START_PUBSUB_CLIENT_TASK);

        // ONGOING -> SHUTDOWN (end of stress test)
        addTransition(machine, TaskStage.ONGOING, TaskStage.SHUTTING, TaskEvent.SHUTTING);

        addTransition(machine, TaskStage.ONGOING, TaskStage.STOPPED, TaskEvent.STOP);

        // SHUTDOWN -> STOPPED
        addTransition(machine, TaskStage.SHUTTING, TaskStage.SHUTDOWN, TaskEvent.SHUTDOWN);

        addTransition(machine, TaskStage.SHUTTING, TaskStage.SHUTDOWN, TaskEvent.STOP);

        // === Global transitions (from any state) ===

        // Any state -> TIMEOUT
        machine.addAnyTransition(TaskStage.TIMEOUT, TaskEvent.TIMEOUT);

        // Any state -> FAILED (general failure)
        machine.addAnyTransition(TaskStage.FAILED, TaskEvent.FAILURE);


        return machine;
    }

    public static StateMachine<TaskStage, TaskEvent> createKafka() {
        StateMachine<TaskStage, TaskEvent> machine = new StateMachine<>(TaskStage.INIT);

        // === Normal flow transitions ===

        // INIT -> START
        addTransition(machine, TaskStage.INIT, TaskStage.START, TaskEvent.START_TASK);

        // START -> INIT_KAFKA_CLIENT
        addTransition(machine, TaskStage.START, TaskStage.INIT_KAFKA_CLIENT, TaskEvent.INIT_KAFKA);

        // INIT_KAFKA_CLIENT -> PRODUCING
        addTransition(machine, TaskStage.INIT_KAFKA_CLIENT, TaskStage.PRODUCING, TaskEvent.START_KAFKA_TASK);

        // PRODUCING -> ONGOING
        addTransition(machine, TaskStage.PRODUCING, TaskStage.ONGOING, TaskEvent.SHUTTING);

        // ONGOING -> SHUTDOWN
        addTransition(machine, TaskStage.ONGOING, TaskStage.SHUTDOWN, TaskEvent.SHUTDOWN);

        // SHUTDOWN -> STOPPED
        addTransition(machine, TaskStage.SHUTDOWN, TaskStage.SHUTTING, TaskEvent.STOP);

        // === Global transitions (from any state) ===

        // Any state -> TIMEOUT
        machine.addAnyTransition(TaskStage.TIMEOUT, TaskEvent.TIMEOUT);

        // Any state -> FAILED (general failure)
        machine.addAnyTransition(TaskStage.FAILED, TaskEvent.FAILURE);

        return machine;
    }

    /**
     * Create a transition for a specific state and event.
     */
    private static void addTransition(StateMachine<TaskStage, TaskEvent> machine,
                                      TaskStage from, TaskStage to, TaskEvent event) {
        machine.addTransition(StateTransition.<TaskStage, TaskEvent>builder()
                .from(from)
                .to(to)
                .on(event)
                .build());
    }
}
