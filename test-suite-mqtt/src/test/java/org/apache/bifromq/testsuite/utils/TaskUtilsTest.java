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

package org.apache.bifromq.testsuite.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskUtilsTest {

    @Test
    void testGetClientTaskAddr_shouldReturnCorrectFormat() {
        
        String taskId = "task-123-abc";

        
        String addr = TaskUtils.getClientTaskAddr(taskId);

        
        assertThat(addr).isEqualTo("client.task.task-123-abc");
    }

    @Test
    void testGetWorkerTaskAddr_shouldReturnCorrectFormat() {
        
        String taskId = "task-456-def";

        
        String addr = TaskUtils.getWorkerTaskAddr(taskId);

        
        assertThat(addr).isEqualTo("worker.task.task-456-def");
    }

    @Test
    void testGetWorkerSignalAddr_shouldReturnCorrectFormat() {
        
        String uniqueName = "signal-unique";

        
        String addr = TaskUtils.getWorkerSignalAddr(uniqueName);

        
        assertThat(addr).isEqualTo("worker.signal.signal-unique");
    }

    @Test
    void testGetWorkEventAddr_shouldReturnConstant() {
        
        String addr = TaskUtils.getWorkEventAddr();

        
        assertThat(addr).isEqualTo("worker.event");
    }

    @Test
    void testGetClientTaskAddr_withEmptyString_shouldReturnPrefix() {
        
        String taskId = "";

        
        String addr = TaskUtils.getClientTaskAddr(taskId);

        
        assertThat(addr).isEqualTo("client.task.");
    }

    @Test
    void testGetClientTaskAddr_withSpecialCharacters_shouldPreserveCharacters() {
        
        String taskId = "task_123.abc-def";

        
        String addr = TaskUtils.getClientTaskAddr(taskId);

        
        assertThat(addr).isEqualTo("client.task.task_123.abc-def");
    }

    @Test
    void testGetWorkerTaskAddr_consistencyWithClientTaskAddr() {
        
        String taskId = "same-task-id";

        
        String clientAddr = TaskUtils.getClientTaskAddr(taskId);
        String workerAddr = TaskUtils.getWorkerTaskAddr(taskId);

        
        assertThat(clientAddr).isNotEqualTo(workerAddr);
        assertThat(clientAddr).startsWith("client.task.");
        assertThat(workerAddr).startsWith("worker.task.");
        assertThat(clientAddr.substring("client.task.".length()))
            .isEqualTo(workerAddr.substring("worker.task.".length()));
    }
}
