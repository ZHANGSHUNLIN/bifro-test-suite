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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;

public final class TaskStateMachineConfig {

    private static final Set<TaskStage> NON_TERMINAL_STAGES = EnumSet.of(
        TaskStage.INIT,
        TaskStage.ASSIGNED,
        TaskStage.STARTING,
        TaskStage.ONGOING,
        TaskStage.SHUTTING
    );

    private TaskStateMachineConfig() {
    }

    public static StateMachine<TaskStage, TaskEvent> create() {
        return create(TaskStage.INIT);
    }

    public static StateMachine<TaskStage, TaskEvent> create(TaskStage initialState) {
        StateMachine<TaskStage, TaskEvent> machine = new StateMachine<>(initialState);

        addTransition(machine, TaskStage.INIT, TaskStage.ASSIGNED, TaskEvent.ASSIGN_TASK);
        addTransition(machine, TaskStage.ASSIGNED, TaskStage.ASSIGNED, TaskEvent.ASSIGN_TASK);
        addTransition(machine, TaskStage.ASSIGNED, TaskStage.STARTING, TaskEvent.START_TASK);
        addTransition(machine, TaskStage.STARTING, TaskStage.SHUTTING, TaskEvent.SHUTTING);
        addTransition(machine, TaskStage.STARTING, TaskStage.ONGOING, TaskEvent.ONGOING);
        addTransition(machine, TaskStage.ONGOING, TaskStage.SHUTTING, TaskEvent.SHUTTING);
        addTransition(machine, TaskStage.SHUTTING, TaskStage.SHUTDOWN, TaskEvent.SHUTDOWN);
        addTransition(machine, TaskStage.SHUTTING, TaskStage.STOPPED, TaskEvent.STOP);
        addTransition(machine, TaskStage.SHUTTING, TaskStage.TIMEOUT, TaskEvent.TIMEOUT);

        addTransitionsFromNonTerminal(machine, TaskStage.TIMEOUT, TaskEvent.TIMEOUT);
        addTransitionsFromNonTerminal(machine, TaskStage.FAILED, TaskEvent.FAILURE);
        addTransitionsFromNonTerminal(machine, TaskStage.STOPPED, TaskEvent.INTERRUPT);

        return machine;
    }

    public static List<StateTransitionMeta> toTransitionMeta(StateMachine<TaskStage, TaskEvent> machine) {
        List<StateTransitionMeta> result = new ArrayList<>();
        for (StateTransition<TaskStage, TaskEvent> t : machine.getAllTransitions()) {
            StateTransitionMeta meta = new StateTransitionMeta();
            meta.setFrom(t.getFrom() != null ? t.getFrom().name() : null);
            meta.setTo(t.getTo() != null ? t.getTo().name() : null);
            meta.setEvent(t.getEvent() != null ? t.getEvent().name() : null);
            result.add(meta);
        }
        return result;
    }

    private static void addTransition(StateMachine<TaskStage, TaskEvent> machine,
                                      TaskStage from, TaskStage to, TaskEvent event) {
        machine.addTransition(StateTransition.<TaskStage, TaskEvent>builder()
            .from(from)
            .to(to)
            .on(event)
            .build());
    }

    private static void addTransitionsFromNonTerminal(StateMachine<TaskStage, TaskEvent> machine,
                                                      TaskStage to, TaskEvent event) {
        for (TaskStage from : NON_TERMINAL_STAGES) {
            addTransition(machine, from, to, event);
        }
    }
}
