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

package org.apache.bifromq.testsuite.eventbus;

import io.vertx.core.eventbus.EventBus;
import java.util.UUID;
import org.apache.bifromq.testsuite.TaskSchedule;

public class VertxClusterTaskCommandBus implements ClusterTaskCommandBus {

    private final EventBus eventBus;

    public VertxClusterTaskCommandBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void broadcastStart(String taskId) {
        publish(TaskSchedule.Op.REG, taskId, null);
    }

    @Override
    public void broadcastStop(String taskId) {
        publish(TaskSchedule.Op.UN_REG, taskId, null);
    }

    @Override
    public void broadcastTaskFinished(String taskId, String sourceNodeId) {
        publish(TaskSchedule.Op.TASK_FINISH, taskId, sourceNodeId);
    }

    private void publish(TaskSchedule.Op op, String taskId, String sourceNodeId) {
        TaskSchedule taskSchedule = TaskSchedule.builder()
            .op(op)
            .id(taskId)
            .sourceNodeId(sourceNodeId)
            .messageId(UUID.randomUUID().toString())
            .build();
        eventBus.publish(EventBusAddresses.CLUSTER_TASK_COMMAND, taskSchedule);
    }
}
