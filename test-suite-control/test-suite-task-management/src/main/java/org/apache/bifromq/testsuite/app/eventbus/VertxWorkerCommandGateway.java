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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.bifromq.testsuite.eventbus.EventBusAddresses;
import org.apache.bifromq.testsuite.eventbus.EventBusRequestKind;
import org.apache.bifromq.testsuite.eventbus.VertxEventBusClient;
import org.apache.bifromq.testsuite.worker.WorkerTaskCommand;
import org.apache.bifromq.testsuite.worker.command.WorkerCommand;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandAck;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandType;

public class VertxWorkerCommandGateway implements WorkerCommandGateway {

    private final VertxEventBusClient eventBusClient;
    private final int startRetries;
    private final int stopRetries;

    public VertxWorkerCommandGateway(VertxEventBusClient eventBusClient) {
        this(eventBusClient, 1, 2);
    }

    public VertxWorkerCommandGateway(VertxEventBusClient eventBusClient, int startRetries, int stopRetries) {
        this.eventBusClient = eventBusClient;
        this.startRetries = normalizeRetries(startRetries);
        this.stopRetries = normalizeRetries(stopRetries);
    }

    @Override
    public CompletableFuture<WorkerCommandAck> sendStart(WorkerTaskCommand command) {
        WorkerCommand workerCommand = WorkerCommand.builder()
            .messageId(UUID.randomUUID().toString())
            .taskId(command.taskId())
            .nodeId(command.nodeId())
            .type(WorkerCommandType.START_TASK)
            .createdAtMs(System.currentTimeMillis())
            .startCommand(command)
            .build();
        return requestWithRetry(
            EventBusAddresses.workerCommand(command.nodeId()),
            workerCommand,
            startRetries);
    }

    @Override
    public CompletableFuture<WorkerCommandAck> sendStop(String taskId, String nodeId) {
        WorkerCommand workerCommand = WorkerCommand.builder()
            .messageId(UUID.randomUUID().toString())
            .taskId(taskId)
            .nodeId(nodeId)
            .type(WorkerCommandType.STOP_TASK)
            .createdAtMs(System.currentTimeMillis())
            .build();
        return requestWithRetry(
            EventBusAddresses.workerCommand(nodeId),
            workerCommand,
            stopRetries);
    }

    private CompletableFuture<WorkerCommandAck> requestWithRetry(String address, WorkerCommand command, int retries) {
        return requestWithRetry(address, command, retries, 0);
    }

    private CompletableFuture<WorkerCommandAck> requestWithRetry(String address, WorkerCommand command,
                                                                 int retries, int attempt) {
        return eventBusClient.<WorkerCommandAck>request(address, command, EventBusRequestKind.TASK_COMMAND)
            .handle((ack, error) -> {
                if (error == null) {
                    return CompletableFuture.completedFuture(ack);
                }
                if (attempt >= retries) {
                    CompletableFuture<WorkerCommandAck> failed = new CompletableFuture<>();
                    failed.completeExceptionally(error);
                    return failed;
                }
                return requestWithRetry(address, command, retries, attempt + 1);
            })
            .thenCompose(future -> future);
    }

    private int normalizeRetries(int retries) {
        return Math.max(0, retries);
    }
}
