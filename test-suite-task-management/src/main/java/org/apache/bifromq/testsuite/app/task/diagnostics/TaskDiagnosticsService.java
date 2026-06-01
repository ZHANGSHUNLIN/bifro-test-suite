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

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.app.bean.TaskDetailResponse;
import org.apache.bifromq.testsuite.app.bean.diagnostics.TaskDiagnosticSymptom;
import org.apache.bifromq.testsuite.app.bean.diagnostics.TaskDiagnosticsResponse;
import org.apache.bifromq.testsuite.app.bean.diagnostics.TaskPipelineDiagnostic;
import org.apache.bifromq.testsuite.app.bean.vo.SubTaskDetail;
import org.apache.bifromq.testsuite.app.bean.vo.TaskStateHistoryVO;
import org.apache.bifromq.testsuite.app.database.repository.TaskStateHistoryRepository;
import org.apache.bifromq.testsuite.app.task.TaskManager;
import org.apache.bifromq.testsuite.pipeline.PipelineStageSnapshot;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TaskDiagnosticsService {
    private static final long DEFAULT_STAGE_STUCK_MS = 60_000L;
    private static final Set<String> TERMINAL_TASK_STAGES =
        Set.of(TaskStage.SHUTDOWN.name(), TaskStage.STOPPED.name(), TaskStage.FAILED.name(), TaskStage.TIMEOUT.name());
    private static final Set<String> CONNECTION_STAGE_KEYS =
        Set.of("CONN_CLIENT_CONN", "PUB_CLIENT_CONN", "SUB_CLIENT_CONN",
            "StartConnClients-CONN_CLIENTS", "StartConnClients-PUB_CLIENTS", "StartConnClients-SUB_CLIENTS");

    @Resource
    private TaskManager taskManager;

    @Resource
    private TaskStateHistoryRepository taskStateHistoryRepository;

    public Mono<ApiResponse<TaskDiagnosticsResponse>> getDiagnostics(String taskId) {
        return taskManager.getTaskDetails(taskId)
            .flatMap(taskResponse -> {
                if (!taskResponse.isSuccess() || taskResponse.getData() == null) {
                    return Mono.just(ApiResponse.error(taskResponse.getMessage()));
                }
                TaskDetailResponse task = taskResponse.getData();
                return stateHistory(taskId)
                    .map(history -> ApiResponse.success(buildResponse(task, history)));
            });
    }

    private Mono<List<TaskStateHistoryVO>> stateHistory(String taskId) {
        return taskStateHistoryRepository.findByTaskIdOrderByTimestampDesc(taskId)
            .map(TaskStateHistoryVO::from)
            .collectList()
            .map(list -> {
                Collections.reverse(list);
                return list;
            });
    }

    private TaskDiagnosticsResponse buildResponse(TaskDetailResponse task, List<TaskStateHistoryVO> stateHistory) {
        List<SubTaskDetail> subtasks = subtasks(task);
        List<TaskPipelineDiagnostic> pipelineDiagnostics = pipelineDiagnostics(subtasks);
        List<TaskDiagnosticSymptom> symptoms = new ArrayList<>();
        Set<String> nextActions = new LinkedHashSet<>();

        detectPipelineSymptoms(pipelineDiagnostics, symptoms, nextActions);
        detectNodeSymptoms(task, subtasks, symptoms, nextActions);
        detectStateSymptoms(stateHistory, symptoms, nextActions);

        return TaskDiagnosticsResponse.builder()
            .taskId(task.getTaskId())
            .generatedAt(System.currentTimeMillis())
            .window(TaskDiagnosticsResponse.DiagnosticWindow.builder()
                .startMs(task.getStartTime() != null ? task.getStartTime() : task.getCreateTime())
                .endMs(task.getEndTime() != null ? task.getEndTime() : System.currentTimeMillis())
                .build())
            .taskSnapshot(task)
            .subtasks(subtasks)
            .stateHistory(stateHistory)
            .pipelineDiagnostics(pipelineDiagnostics)
            .symptoms(symptoms)
            .nextActions(new ArrayList<>(nextActions))
            .logFiles(List.of("task-pipeline.log", "task-state-machine.log", "conn.log", "error.log",
                "task/" + task.getTaskId() + ".log"))
            .logQueryKeys(logQueryKeys(task, pipelineDiagnostics))
            .build();
    }

    private List<SubTaskDetail> subtasks(TaskDetailResponse task) {
        if (task.getSubTaskDetails() == null || task.getSubTaskDetails().isEmpty()) {
            return List.of();
        }
        return task.getSubTaskDetails().values().stream()
            .sorted(Comparator.comparing(SubTaskDetail::getNodeId, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    private List<TaskPipelineDiagnostic> pipelineDiagnostics(List<SubTaskDetail> subtasks) {
        return subtasks.stream()
            .map(subtask -> TaskPipelineDiagnostic.builder()
                .nodeId(subtask.getNodeId())
                .nodeName(subtask.getNodeName())
                .taskType(subtask.getTaskType())
                .taskWorkStage(subtask.getTaskWorkStage())
                .totalClientCount(subtask.getTotalClientCount())
                .stages(subtask.getPipelineStages())
                .build())
            .toList();
    }

    private void detectPipelineSymptoms(List<TaskPipelineDiagnostic> diagnostics,
                                        List<TaskDiagnosticSymptom> symptoms,
                                        Set<String> nextActions) {
        for (TaskPipelineDiagnostic diagnostic : diagnostics) {
            if (diagnostic.getStages() == null) {
                continue;
            }
            for (PipelineStageSnapshot stage : diagnostic.getStages()) {
                detectStuckStage(diagnostic, stage, symptoms, nextActions);
                detectConnectionFailure(diagnostic, stage, symptoms, nextActions);
            }
        }
    }

    private void detectStuckStage(TaskPipelineDiagnostic diagnostic, PipelineStageSnapshot stage,
                                  List<TaskDiagnosticSymptom> symptoms, Set<String> nextActions) {
        if (!"RUNNING".equals(stage.getStatus())) {
            return;
        }
        long durationMs = stage.getDurationMs() == null ? 0L : stage.getDurationMs();
        int pending = stage.getPending() == null ? 0 : stage.getPending();
        if (durationMs <= DEFAULT_STAGE_STUCK_MS || pending <= 0) {
            return;
        }
        symptoms.add(TaskDiagnosticSymptom.builder()
            .type("PIPELINE_STAGE_STUCK")
            .severity("WARN")
            .nodeId(diagnostic.getNodeId())
            .stage(stage.getKey())
            .message("Pipeline stage is running with pending futures beyond the default threshold")
            .details(Map.of(
                "durationMs", durationMs,
                "pending", pending,
                "pendingSamples", stage.getPendingSamples() == null ? List.of() : stage.getPendingSamples()))
            .build());
        nextActions.add("Check task-pipeline.log for taskId and stage progress");
        nextActions.add("Fetch /api/task/{taskId}/log-summary for recent task-related log lines");
        nextActions.add("Collect thread dump if pending remains unchanged");
    }

    private void detectConnectionFailure(TaskPipelineDiagnostic diagnostic, PipelineStageSnapshot stage,
                                         List<TaskDiagnosticSymptom> symptoms, Set<String> nextActions) {
        int failed = stage.getFailed() == null ? 0 : stage.getFailed();
        boolean hasReasons = stage.getFailureReasons() != null && !stage.getFailureReasons().isEmpty();
        if (failed <= 0 && !hasReasons) {
            return;
        }
        if (!isConnectionStage(stage.getKey())) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("failed", failed);
        details.put("failureReasons", stage.getFailureReasons() == null ? Map.of() : stage.getFailureReasons());
        symptoms.add(TaskDiagnosticSymptom.builder()
            .type("CONNECTION_FAILURE")
            .severity("WARN")
            .nodeId(diagnostic.getNodeId())
            .stage(stage.getKey())
            .message("Connection stage recorded failed client operations")
            .details(details)
            .build());
        nextActions.add("Check conn.log for taskId, nodeId, stage and reasonSummary");
        nextActions.add("Fetch /api/task/{taskId}/log-summary for recent task-related log lines");
        addConnectionReasonActions(stage.getFailureReasons(), nextActions);
    }

    private boolean isConnectionStage(String stageKey) {
        if (stageKey == null) {
            return false;
        }
        return CONNECTION_STAGE_KEYS.contains(stageKey) || stageKey.endsWith("_CONN")
            || stageKey.startsWith("StartConnClients");
    }

    private void addConnectionReasonActions(Map<String, Integer> reasons, Set<String> nextActions) {
        if (reasons == null || reasons.isEmpty()) {
            return;
        }
        if (reasons.containsKey("connection_refused")) {
            nextActions.add("For connection_refused, check broker host, port and broker group");
        }
        if (reasons.containsKey("connect_timeout")) {
            nextActions.add("For connect_timeout, check network path, broker backlog and node CPU");
        }
        if (reasons.containsKey("local_addr_or_ephemeral_port_exhausted")) {
            nextActions.add("For local address exhaustion, check local address pool and ephemeral port range");
        }
        if (reasons.containsKey("local_port_address_in_use")) {
            nextActions.add("For local port conflicts, check local port allocation and concurrent tasks");
        }
    }

    private void detectNodeSymptoms(TaskDetailResponse task, List<SubTaskDetail> subtasks,
                                    List<TaskDiagnosticSymptom> symptoms, Set<String> nextActions) {
        String mainStage = task.getMainTaskView() == null || task.getMainTaskView().taskWorkStage() == null
            ? null : task.getMainTaskView().taskWorkStage().name();
        boolean mainTerminal = isTerminalStage(mainStage);
        List<String> partialFailedNodes = subtasks.stream()
            .filter(subtask -> !mainTerminal && isTerminalStage(subtask.getTaskWorkStage())
                && !"SHUTDOWN".equals(subtask.getTaskWorkStage()))
            .map(SubTaskDetail::getNodeId)
            .toList();
        if (!partialFailedNodes.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("mainStage", mainStage);
            details.put("nodes", partialFailedNodes);
            symptoms.add(TaskDiagnosticSymptom.builder()
                .type("NODE_PARTIAL_FAILURE")
                .severity("WARN")
                .message("Some subtasks reached terminal failure state while main task is not terminal")
                .details(details)
                .build());
            nextActions.add("Check node logs and state-history for failed node task transitions");
        }
    }

    private void detectStateSymptoms(List<TaskStateHistoryVO> stateHistory,
                                     List<TaskDiagnosticSymptom> symptoms,
                                     Set<String> nextActions) {
        boolean terminalSeen = false;
        for (TaskStateHistoryVO history : stateHistory) {
            String toStage = history.getToStage();
            if (terminalSeen && !isTerminalStage(toStage)) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("toStage", toStage);
                details.put("timestamp", history.getTimestamp());
                symptoms.add(TaskDiagnosticSymptom.builder()
                    .type("STATE_INCONSISTENCY")
                    .severity("WARN")
                    .nodeId(history.getNodeId())
                    .message("State history contains a non-terminal transition after a terminal stage")
                    .details(details)
                    .build());
                nextActions.add("Check state machine events and LocalTaskCoordinator aggregation logic");
                return;
            }
            terminalSeen = terminalSeen || isTerminalStage(toStage);
        }
    }

    private boolean isTerminalStage(String stage) {
        return stage != null && TERMINAL_TASK_STAGES.contains(stage);
    }

    private List<String> logQueryKeys(TaskDetailResponse task, List<TaskPipelineDiagnostic> diagnostics) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("taskId=" + task.getTaskId());
        diagnostics.stream()
            .map(TaskPipelineDiagnostic::getNodeId)
            .filter(nodeId -> nodeId != null && !nodeId.isBlank())
            .forEach(nodeId -> keys.add("nodeId=" + nodeId));
        diagnostics.stream()
            .flatMap(diagnostic -> diagnostic.getStages() == null ? java.util.stream.Stream.empty()
                : diagnostic.getStages().stream())
            .map(PipelineStageSnapshot::getKey)
            .filter(stage -> stage != null && !stage.isBlank())
            .forEach(stage -> keys.add("stage=" + stage));
        return new ArrayList<>(keys);
    }
}
