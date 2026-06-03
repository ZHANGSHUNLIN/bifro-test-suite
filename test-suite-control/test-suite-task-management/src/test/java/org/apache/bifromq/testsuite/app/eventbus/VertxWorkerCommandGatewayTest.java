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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.apache.bifromq.testsuite.eventbus.EventBusRequestKind;
import org.apache.bifromq.testsuite.eventbus.VertxEventBusClient;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskCommand;
import org.apache.bifromq.testsuite.worker.command.WorkerCommand;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandAck;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandAckStatus;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandType;
import org.apache.bifromq.testsuite.worker.pojo.TaskStopContext;
import org.apache.bifromq.testsuite.worker.pojo.TaskStopReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VertxWorkerCommandGatewayTest {

    private static final String TASK_ID = "task-1";
    private static final String NODE_ID = "node-1";

    @Mock
    private VertxEventBusClient eventBusClient;

    @Test
    void sendStart_givenTransientFailure_shouldRetryWithSameMessageId() throws Exception {
        VertxWorkerCommandGateway gateway = new VertxWorkerCommandGateway(eventBusClient, 1, 0);
        WorkerCommandAck ack = WorkerCommandAck.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .type(WorkerCommandType.START_TASK)
            .status(WorkerCommandAckStatus.ACCEPTED)
            .build();
        when(eventBusClient.<WorkerCommandAck>request(eq("worker." + NODE_ID + ".command"),
            any(WorkerCommand.class), eq(EventBusRequestKind.TASK_COMMAND)))
            .thenReturn(failedFuture(new RuntimeException("transient")))
            .thenReturn(CompletableFuture.completedFuture(ack));

        WorkerCommandAck result = gateway.sendStart(command()).get();

        assertThat(result.getStatus()).isEqualTo(WorkerCommandAckStatus.ACCEPTED);
        ArgumentCaptor<WorkerCommand> captor = ArgumentCaptor.forClass(WorkerCommand.class);
        verify(eventBusClient, org.mockito.Mockito.times(2))
            .request(eq("worker." + NODE_ID + ".command"), captor.capture(), eq(EventBusRequestKind.TASK_COMMAND));
        assertThat(captor.getAllValues()).extracting(WorkerCommand::getMessageId).doesNotContainNull();
        assertThat(captor.getAllValues().get(0).getMessageId())
            .isEqualTo(captor.getAllValues().get(1).getMessageId());
    }

    @Test
    void sendStop_givenRetryDisabled_shouldFailAfterFirstFailure() {
        VertxWorkerCommandGateway gateway = new VertxWorkerCommandGateway(eventBusClient, 1, 0);
        when(eventBusClient.<WorkerCommandAck>request(eq("worker." + NODE_ID + ".command"),
            any(WorkerCommand.class), eq(EventBusRequestKind.TASK_COMMAND)))
            .thenReturn(failedFuture(new RuntimeException("offline")));

        CompletableFuture<WorkerCommandAck> result = gateway.sendStop(TASK_ID, NODE_ID);

        assertThat(result).failsWithin(java.time.Duration.ofSeconds(1))
            .withThrowableThat().withMessageContaining("offline");
        verify(eventBusClient, org.mockito.Mockito.times(1))
            .request(eq("worker." + NODE_ID + ".command"), any(WorkerCommand.class),
                eq(EventBusRequestKind.TASK_COMMAND));
    }

    @Test
    void sendStop_givenTransientFailure_shouldRetryWithSameMessageId() throws Exception {
        VertxWorkerCommandGateway gateway = new VertxWorkerCommandGateway(eventBusClient, 0, 1);
        WorkerCommandAck ack = WorkerCommandAck.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .type(WorkerCommandType.STOP_TASK)
            .status(WorkerCommandAckStatus.ACCEPTED)
            .build();
        when(eventBusClient.<WorkerCommandAck>request(eq("worker." + NODE_ID + ".command"),
            any(WorkerCommand.class), eq(EventBusRequestKind.TASK_COMMAND)))
            .thenReturn(failedFuture(new RuntimeException("transient")))
            .thenReturn(CompletableFuture.completedFuture(ack));

        WorkerCommandAck result = gateway.sendStop(TASK_ID, NODE_ID).get();

        assertThat(result.getStatus()).isEqualTo(WorkerCommandAckStatus.ACCEPTED);
        ArgumentCaptor<WorkerCommand> captor = ArgumentCaptor.forClass(WorkerCommand.class);
        verify(eventBusClient, org.mockito.Mockito.times(2))
            .request(eq("worker." + NODE_ID + ".command"), captor.capture(), eq(EventBusRequestKind.TASK_COMMAND));
        assertThat(captor.getAllValues()).extracting(WorkerCommand::getMessageId).doesNotContainNull();
        assertThat(captor.getAllValues().get(0).getMessageId())
            .isEqualTo(captor.getAllValues().get(1).getMessageId());
    }

    @Test
    void sendStart_givenRejectedAck_shouldNotRetryWithNewCommand() throws Exception {
        VertxWorkerCommandGateway gateway = new VertxWorkerCommandGateway(eventBusClient, 1, 1);
        WorkerCommandAck ack = WorkerCommandAck.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .type(WorkerCommandType.START_TASK)
            .status(WorkerCommandAckStatus.REJECTED_INVALID_STATE)
            .reason("already running")
            .build();
        when(eventBusClient.<WorkerCommandAck>request(eq("worker." + NODE_ID + ".command"),
            any(WorkerCommand.class), eq(EventBusRequestKind.TASK_COMMAND)))
            .thenReturn(CompletableFuture.completedFuture(ack));

        WorkerCommandAck result = gateway.sendStart(command()).get();

        assertThat(result.getStatus()).isEqualTo(WorkerCommandAckStatus.REJECTED_INVALID_STATE);
        verify(eventBusClient, org.mockito.Mockito.times(1))
            .request(eq("worker." + NODE_ID + ".command"), any(WorkerCommand.class),
                eq(EventBusRequestKind.TASK_COMMAND));
    }

    @Test
    void sendStop_givenStopContext_shouldIncludeContextInCommand() throws Exception {
        VertxWorkerCommandGateway gateway = new VertxWorkerCommandGateway(eventBusClient, 1, 0);
        WorkerCommandAck ack = WorkerCommandAck.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .type(WorkerCommandType.STOP_TASK)
            .status(WorkerCommandAckStatus.ACCEPTED)
            .build();
        when(eventBusClient.<WorkerCommandAck>request(eq("worker." + NODE_ID + ".command"),
            any(WorkerCommand.class), eq(EventBusRequestKind.TASK_COMMAND)))
            .thenReturn(CompletableFuture.completedFuture(ack));

        gateway.sendStop(TASK_ID, NODE_ID, TaskStopContext.serviceShutdown(NODE_ID)).get();

        ArgumentCaptor<WorkerCommand> captor = ArgumentCaptor.forClass(WorkerCommand.class);
        verify(eventBusClient).request(eq("worker." + NODE_ID + ".command"),
            captor.capture(), eq(EventBusRequestKind.TASK_COMMAND));
        assertThat(captor.getValue().getStopContext().getReason()).isEqualTo(TaskStopReason.SERVICE_SHUTDOWN);
        assertThat(captor.getValue().getStopContext().getMetadata()).containsEntry("nodeShutdown", true);
    }

    private WorkerTaskCommand command() {
        return WorkerTaskCommand.fromTaskConfig(TaskConfig.builder()
            .taskId(TASK_ID)
            .nodeId(NODE_ID)
            .totalClientCount(1)
            .build());
    }

    private CompletableFuture<WorkerCommandAck> failedFuture(Throwable error) {
        CompletableFuture<WorkerCommandAck> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
