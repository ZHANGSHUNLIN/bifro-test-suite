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

package org.apache.bifromq.testsuite.app.eventbus;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.bifromq.testsuite.worker.WorkerTaskCommand;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandAck;
import org.apache.bifromq.testsuite.worker.pojo.TaskStopContext;

public interface WorkerCommandGateway {

    CompletableFuture<WorkerCommandAck> sendStart(WorkerTaskCommand command);

    CompletableFuture<WorkerCommandAck> sendStop(String taskId, String nodeId);

    CompletableFuture<WorkerCommandAck> sendStop(String taskId, String nodeId, TaskStopContext context);

    default CompletableFuture<List<WorkerCommandAck>> sendStartAll(List<WorkerTaskCommand> commands) {
        List<CompletableFuture<WorkerCommandAck>> futures = commands.stream()
            .map(this::sendStart)
            .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    default CompletableFuture<List<WorkerCommandAck>> sendStopAll(String taskId, List<String> nodeIds) {
        List<CompletableFuture<WorkerCommandAck>> futures = nodeIds.stream()
            .map(nodeId -> sendStop(taskId, nodeId))
            .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    default CompletableFuture<List<WorkerCommandAck>> sendStopAll(String taskId, List<String> nodeIds,
                                                                  TaskStopContext context) {
        List<CompletableFuture<WorkerCommandAck>> futures = nodeIds.stream()
            .map(nodeId -> sendStop(taskId, nodeId, context))
            .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }
}
