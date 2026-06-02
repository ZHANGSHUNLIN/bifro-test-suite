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

package org.apache.bifromq.testsuite.app.cluster.scheduling;

import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
@AllArgsConstructor
public class TaskAssignment {

    private final String taskId;

    private final int totalClients;

    private final Map<String, Integer> nodeAssignments;

    private final Instant calculatedAt;

    public TaskAssignment(String taskId, int totalClients, Map<String, Integer> nodeAssignments) {
        this.taskId = taskId;
        this.totalClients = totalClients;
        this.nodeAssignments = nodeAssignments;
        this.calculatedAt = Instant.now();
    }

    public int getClientsForNode(String nodeId) {
        return nodeAssignments.getOrDefault(nodeId, 0);
    }

    public boolean isEmpty() {
        return nodeAssignments.isEmpty() || nodeAssignments.values().stream().allMatch(v -> v == 0);
    }

}