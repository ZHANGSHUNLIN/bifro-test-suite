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

package org.apache.bifromq.testsuite.app.controller.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.app.bean.vo.TaskBasicInfoResponse;
import org.apache.bifromq.testsuite.app.bean.vo.TaskConfigView;
import org.apache.bifromq.testsuite.app.database.pojo.TaskStateHistory;
import org.apache.bifromq.testsuite.app.database.repository.TaskStateHistoryRepository;
import org.apache.bifromq.testsuite.app.database.service.TaskInfoMetadataService;
import org.apache.bifromq.testsuite.app.eventbus.WorkerCommandGateway;
import org.apache.bifromq.testsuite.app.task.TaskManager;
import org.apache.bifromq.testsuite.audit.application.AuditLogService;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandAck;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandAckStatus;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TaskControllerWorkerCommandFailureTest {

    private static final String TASK_ID = "task-1";

    @Mock
    private WorkerCommandGateway workerCommandGateway;
    @Mock
    private TaskManager taskManager;
    @Mock
    private TaskStateHistoryRepository taskStateHistoryRepository;
    @Mock
    private TaskInfoMetadataService taskInfoMetadataService;
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private TaskController taskController;

    @BeforeEach
    void setUp() {
        when(auditLogService.record(any(), any(), any(), any(), org.mockito.Mockito.anyBoolean(), any()))
            .thenReturn(Mono.empty());
        org.mockito.Mockito.lenient().when(taskStateHistoryRepository.save(any(TaskStateHistory.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void stopTask_givenRejectedAck_shouldRecordCommandFailureHistory() {
        WorkerCommandAck ack = WorkerCommandAck.builder()
            .messageId("message-1")
            .taskId(TASK_ID)
            .nodeId("node-1")
            .type(WorkerCommandType.STOP_TASK)
            .status(WorkerCommandAckStatus.REJECTED_INVALID_STATE)
            .reason("not running")
            .ackAtMs(100L)
            .build();
        when(taskManager.getTaskBasicInfo(TASK_ID)).thenReturn(Mono.just(taskBasicInfo(TaskStage.ONGOING)));
        when(taskInfoMetadataService.tryUpdateStage(
            eq(TASK_ID), eq(List.of(TaskStage.STARTING, TaskStage.ONGOING)), eq(TaskStage.SHUTTING),
            any(Instant.class))).thenReturn(Mono.just(true));
        when(taskManager.getTaskWorkerNodeIds(TASK_ID)).thenReturn(Mono.just(List.of("node-1")));
        when(workerCommandGateway.sendStopAll(TASK_ID, List.of("node-1")))
            .thenReturn(CompletableFuture.completedFuture(List.of(ack)));

        var response = taskController.stopTask(TASK_ID, null).block();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        ArgumentCaptor<TaskStateHistory> captor = ArgumentCaptor.forClass(TaskStateHistory.class);
        org.mockito.Mockito.verify(taskStateHistoryRepository).save(captor.capture());
        TaskStateHistory history = captor.getValue();
        assertThat(history.getTaskId()).isEqualTo(TASK_ID);
        assertThat(history.getNodeId()).isEqualTo("node-1");
        assertThat(history.getSource()).isEqualTo("WORKER_COMMAND");
        assertThat(history.getErrorMessage()).contains("STOP_TASK failed").contains("not running");
        assertThat(history.getMetadata()).containsEntry("eventType", "WORKER_COMMAND_REJECTED");
    }

    @Test
    void stopTask_givenDeliveryFailure_shouldRecordCommandFailureHistory() {
        CompletableFuture<List<WorkerCommandAck>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("worker offline"));
        when(taskManager.getTaskBasicInfo(TASK_ID)).thenReturn(Mono.just(taskBasicInfo(TaskStage.ONGOING)));
        when(taskInfoMetadataService.tryUpdateStage(
            eq(TASK_ID), eq(List.of(TaskStage.STARTING, TaskStage.ONGOING)), eq(TaskStage.SHUTTING),
            any(Instant.class))).thenReturn(Mono.just(true));
        when(taskManager.getTaskWorkerNodeIds(TASK_ID)).thenReturn(Mono.just(List.of("node-1")));
        when(workerCommandGateway.sendStopAll(TASK_ID, List.of("node-1"))).thenReturn(failed);

        var response = taskController.stopTask(TASK_ID, null).block();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        ArgumentCaptor<TaskStateHistory> captor = ArgumentCaptor.forClass(TaskStateHistory.class);
        org.mockito.Mockito.verify(taskStateHistoryRepository).save(captor.capture());
        TaskStateHistory history = captor.getValue();
        assertThat(history.getTaskId()).isEqualTo(TASK_ID);
        assertThat(history.getSource()).isEqualTo("WORKER_COMMAND");
        assertThat(history.getErrorMessage()).contains("STOP_TASK failed").contains("worker offline");
        assertThat(history.getMetadata()).containsEntry("eventType", "WORKER_COMMAND_DELIVERY_FAILED");
    }

    @Test
    void stopTask_givenAlreadyShutting_shouldReturnSuccessWithoutSendingCommand() {
        when(taskManager.getTaskBasicInfo(TASK_ID)).thenReturn(Mono.just(taskBasicInfo(TaskStage.SHUTTING)));

        var response = taskController.stopTask(TASK_ID, null).block();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        verify(workerCommandGateway, never()).sendStopAll(any(), any());
    }

    @Test
    void stopTask_givenConcurrentStateClaimLost_shouldReturnSuccessWithoutSendingCommand() {
        when(taskManager.getTaskBasicInfo(TASK_ID)).thenReturn(Mono.just(taskBasicInfo(TaskStage.ONGOING)));
        when(taskInfoMetadataService.tryUpdateStage(
            eq(TASK_ID), eq(List.of(TaskStage.STARTING, TaskStage.ONGOING)), eq(TaskStage.SHUTTING),
            any(Instant.class))).thenReturn(Mono.just(false));

        var response = taskController.stopTask(TASK_ID, null).block();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        verify(workerCommandGateway, never()).sendStopAll(any(), any());
    }

    @Test
    void confirmTask_givenConcurrentStateClaimLost_shouldNotPrepareOrSendCommand() {
        when(taskManager.getTaskBasicInfo(TASK_ID)).thenReturn(Mono.just(taskBasicInfo(TaskStage.ASSIGNED)));
        when(taskInfoMetadataService.tryUpdateStage(
            eq(TASK_ID), eq(TaskStage.ASSIGNED), eq(TaskStage.STARTING), any(Instant.class)))
            .thenReturn(Mono.just(false));

        var response = taskController.confirmTask(TASK_ID, null).block();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        verify(taskManager, never()).prepareTaskStartCommands(any());
        verify(workerCommandGateway, never()).sendStartAll(any());
    }

    private ApiResponse<TaskBasicInfoResponse> taskBasicInfo(TaskStage stage) {
        TaskBasicInfoResponse response = new TaskBasicInfoResponse();
        response.setTaskId(TASK_ID);
        response.setMainTaskView(TaskConfigView.builder()
            .taskId(TASK_ID)
            .taskWorkStage(stage)
            .template(TaskTemplate.PUBSUB_STANDARD)
            .build());
        return ApiResponse.success(response);
    }
}
