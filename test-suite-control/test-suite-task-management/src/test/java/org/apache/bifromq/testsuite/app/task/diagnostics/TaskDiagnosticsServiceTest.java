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

package org.apache.bifromq.testsuite.app.task.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.app.bean.TaskDetailResponse;
import org.apache.bifromq.testsuite.app.bean.diagnostics.TaskDiagnosticsResponse;
import org.apache.bifromq.testsuite.app.bean.vo.SubTaskDetail;
import org.apache.bifromq.testsuite.app.bean.vo.TaskConfigView;
import org.apache.bifromq.testsuite.app.database.pojo.TaskStateHistory;
import org.apache.bifromq.testsuite.app.database.repository.TaskStateHistoryRepository;
import org.apache.bifromq.testsuite.app.task.TaskManager;
import org.apache.bifromq.testsuite.pipeline.PipelineStageSnapshot;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TaskDiagnosticsServiceTest {
    @Mock
    private TaskManager taskManager;

    @Mock
    private TaskStateHistoryRepository taskStateHistoryRepository;

    private TaskDiagnosticsService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TaskDiagnosticsService();
        inject("taskManager", taskManager);
        inject("taskStateHistoryRepository", taskStateHistoryRepository);
    }

    @Test
    void getDiagnostics_shouldBuildPackageWithPipelineAndConnectionSymptoms() {
        TaskDetailResponse detail = taskDetail(PipelineStageSnapshot.builder()
            .key("StartConnClients-CONN_CLIENTS")
            .label("connect")
            .status("RUNNING")
            .durationMs(120_000L)
            .started(10)
            .completed(5)
            .failed(2)
            .pending(5)
            .pendingSamples(List.of("client-5", "client-6"))
            .failureReasons(Map.of("connect_timeout", 2))
            .build());
        when(taskManager.getTaskDetails("task-1")).thenReturn(Mono.just(ApiResponse.success(detail)));
        when(taskStateHistoryRepository.findByTaskIdOrderByTimestampDesc("task-1"))
            .thenReturn(Flux.just(history(TaskStage.STARTING, TaskStage.ONGOING, 2),
                history(TaskStage.ASSIGNED, TaskStage.STARTING, 1)));

        ApiResponse<TaskDiagnosticsResponse> response = service.getDiagnostics("task-1").block();

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(200);
        TaskDiagnosticsResponse diagnostics = response.getData();
        assertThat(diagnostics.getTaskId()).isEqualTo("task-1");
        assertThat(diagnostics.getPipelineDiagnostics()).singleElement()
            .satisfies(pipeline -> {
                assertThat(pipeline.getNodeId()).isEqualTo("node-1");
                assertThat(pipeline.getStages()).singleElement()
                    .extracting(PipelineStageSnapshot::getPending)
                    .isEqualTo(5);
            });
        assertThat(diagnostics.getSymptoms())
            .extracting("type")
            .contains("PIPELINE_STAGE_STUCK", "CONNECTION_FAILURE");
        assertThat(diagnostics.getNextActions())
            .contains("For connect_timeout, check network path, broker backlog and node CPU");
        assertThat(diagnostics.getStateHistory())
            .extracting("toStage")
            .containsExactly("STARTING", "ONGOING");
        assertThat(diagnostics.getLogQueryKeys())
            .contains("taskId=task-1", "nodeId=node-1", "stage=StartConnClients-CONN_CLIENTS");
    }

    @Test
    void getDiagnostics_shouldDetectPartialNodeFailure() {
        TaskDetailResponse detail = taskDetail(PipelineStageSnapshot.builder()
            .key("StartConnClients-CONN_CLIENTS")
            .label("connect")
            .status("FAILED")
            .build());
        detail.getSubTaskDetails().get("node-1").setTaskWorkStage("FAILED");
        when(taskManager.getTaskDetails("task-1")).thenReturn(Mono.just(ApiResponse.success(detail)));
        when(taskStateHistoryRepository.findByTaskIdOrderByTimestampDesc("task-1")).thenReturn(Flux.empty());

        ApiResponse<TaskDiagnosticsResponse> response = service.getDiagnostics("task-1").block();

        assertThat(response).isNotNull();
        assertThat(response.getData().getSymptoms())
            .extracting("type")
            .contains("NODE_PARTIAL_FAILURE");
    }

    private TaskDetailResponse taskDetail(PipelineStageSnapshot stage) {
        TaskDetailResponse detail = new TaskDetailResponse();
        detail.setSuccess(true);
        detail.setTaskId("task-1");
        detail.setTaskName("task");
        detail.setCreateTime(1000L);
        detail.setStartTime(2000L);
        detail.setMainTaskView(TaskConfigView.builder()
            .taskId("task-1")
            .nodeId("main")
            .taskType(TaskConfig.TaskType.CONN)
            .template(TaskTemplate.CONN_STANDARD)
            .taskWorkStage(TaskStage.ONGOING)
            .totalClientCount(10)
            .build());
        detail.setSubTaskDetails(Map.of("node-1", SubTaskDetail.builder()
            .nodeId("node-1")
            .nodeName("node one")
            .taskType("CONN")
            .taskWorkStage("ONGOING")
            .totalClientCount(10)
            .pipelineStages(List.of(stage))
            .build()));
        return detail;
    }

    private TaskStateHistory history(TaskStage from, TaskStage to, int seconds) {
        return TaskStateHistory.builder()
            .taskId("task-1")
            .fromStage(from)
            .toStage(to)
            .triggerEvent(TaskEvent.START_TASK)
            .timestamp(Instant.ofEpochSecond(seconds))
            .source("main")
            .build();
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = TaskDiagnosticsService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }
}
