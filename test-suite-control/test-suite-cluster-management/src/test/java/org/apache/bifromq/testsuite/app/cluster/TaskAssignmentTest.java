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

package org.apache.bifromq.testsuite.app.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.bifromq.testsuite.app.cluster.scheduling.TaskAssignment;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskAssignmentTest {

    @Test
    void constructor_shouldSetFieldsCorrectly() {
        
        Map<String, Integer> assignments = new HashMap<>();
        assignments.put("node-1", 5);
        assignments.put("node-2", 3);

        
        TaskAssignment assignment = new TaskAssignment("task-1", 8, assignments);

        
        assertThat(assignment.getTaskId()).isEqualTo("task-1");
        assertThat(assignment.getTotalClients()).isEqualTo(8);
        assertThat(assignment.getNodeAssignments()).hasSize(2);
        assertThat(assignment.getCalculatedAt()).isNotNull();
    }

    @Test
    void getClientsForNode_givenExistingNode_shouldReturnCount() {
        
        Map<String, Integer> assignments = new HashMap<>();
        assignments.put("node-1", 5);

        TaskAssignment assignment = new TaskAssignment("task-1", 5, assignments);

        
        int clients = assignment.getClientsForNode("node-1");

        
        assertThat(clients).isEqualTo(5);
    }

    @Test
    void getClientsForNode_givenNonExistingNode_shouldReturnZero() {
        
        Map<String, Integer> assignments = new HashMap<>();
        assignments.put("node-1", 5);

        TaskAssignment assignment = new TaskAssignment("task-1", 5, assignments);

        
        int clients = assignment.getClientsForNode("non-existing");

        
        assertThat(clients).isZero();
    }

    @Test
    void isEmpty_givenEmptyAssignments_shouldReturnTrue() {
        
        TaskAssignment assignment = new TaskAssignment("task-1", 0, new HashMap<>());

        
        assertThat(assignment.isEmpty()).isTrue();
    }

    @Test
    void isEmpty_givenAllZeroAssignments_shouldReturnTrue() {
        
        Map<String, Integer> assignments = new HashMap<>();
        assignments.put("node-1", 0);
        assignments.put("node-2", 0);

        TaskAssignment assignment = new TaskAssignment("task-1", 0, assignments);

        
        assertThat(assignment.isEmpty()).isTrue();
    }

    @Test
    void isEmpty_givenNonEmptyAssignments_shouldReturnFalse() {
        
        Map<String, Integer> assignments = new HashMap<>();
        assignments.put("node-1", 5);

        TaskAssignment assignment = new TaskAssignment("task-1", 5, assignments);

        
        assertThat(assignment.isEmpty()).isFalse();
    }

    @Test
    void toString_shouldContainTaskIdAndClients() {
        
        Map<String, Integer> assignments = new HashMap<>();
        assignments.put("node-1", 5);

        TaskAssignment assignment = new TaskAssignment("task-1", 5, assignments);

        
        String str = assignment.toString();

        
        assertThat(str).contains("taskId=task-1");
        assertThat(str).contains("totalClients=5");
    }
}
