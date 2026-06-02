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

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.app.bean.TaskDetailResponse;
import org.apache.bifromq.testsuite.app.bean.diagnostics.TaskDiagnosticsResponse;
import org.apache.bifromq.testsuite.app.bean.diagnostics.TaskLogSummaryResponse;
import org.apache.bifromq.testsuite.app.bean.dto.NodeTaskAllocationRequest;
import org.apache.bifromq.testsuite.app.bean.dto.TaskRequest;
import org.apache.bifromq.testsuite.app.bean.vo.TaskBasicInfoResponse;
import org.apache.bifromq.testsuite.app.bean.vo.TaskListVO;
import org.apache.bifromq.testsuite.app.bean.vo.TaskReportResponse;
import org.apache.bifromq.testsuite.app.bean.vo.TaskStateHistoryVO;
import org.apache.bifromq.testsuite.app.bean.vo.TaskStatisticsResponse;
import org.apache.bifromq.testsuite.app.bean.vo.TaskSubTasksResponse;
import org.apache.bifromq.testsuite.app.controller.ApiController;
import org.apache.bifromq.testsuite.app.database.pojo.TaskStateHistory;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.repository.TaskStateHistoryRepository;
import org.apache.bifromq.testsuite.app.database.service.TaskReportService;
import org.apache.bifromq.testsuite.app.eventbus.WorkerCommandGateway;
import org.apache.bifromq.testsuite.app.task.TaskManager;
import org.apache.bifromq.testsuite.app.task.diagnostics.TaskDiagnosticsService;
import org.apache.bifromq.testsuite.app.task.diagnostics.TaskLogSummaryService;
import org.apache.bifromq.testsuite.audit.application.AuditLogService;
import org.apache.bifromq.testsuite.audit.domain.AuditAction;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.web.PageInfo;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandAck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Tag(name = "Task Management", description = "MQTT test task management API")
@RestController
@RequestMapping("/api/task")
@ConditionalOnControlPlane
public class TaskController implements ApiController {

    @Resource
    private Vertx vertx;

    @Resource
    private WorkerCommandGateway workerCommandGateway;

    @Resource
    private TaskManager taskManager;

    @Resource
    private TaskReportService taskReportService;

    @Resource
    private TaskStateHistoryRepository taskStateHistoryRepository;

    @Resource
    private TaskDiagnosticsService taskDiagnosticsService;

    @Resource
    private TaskLogSummaryService taskLogSummaryService;

    @Resource
    private AuditLogService auditLogService;

    @Operation(summary = "Create Test Task", description = "Create a new MQTT stress test task")
    @PostMapping()
    public Mono<ApiResponse<TaskInfoMetadata>> addTask(
        @Valid @RequestBody @Parameter(description = "Task configuration request") TaskRequest taskRequest,
        ServerWebExchange exchange) {
        return taskManager.addTask(taskRequest)
            .flatMap(task -> auditLogService.record(exchange, AuditAction.TASK_CREATE, "TASK", task.getTaskId(), true,
                    "Create task")
                .thenReturn(ApiResponse.success(task)));
    }

    @Operation(summary = "Get Task List", description = "Paginated task list query, supports filter by name, type and group")
    @GetMapping("/list")
    public Mono<ApiResponse<PageInfo<TaskListVO>>> getAllTasks(
        @Parameter(description = "Page number", example = "1") @RequestParam(name = "pageNum", defaultValue = "1")
        Integer pageNum,
        @Parameter(description = "Page size", example = "20") @RequestParam(name = "pageSize", defaultValue = "20")
        Integer pageSize,
        @Parameter(description = "Task name (fuzzy search)") @RequestParam(name = "taskName", required = false)
        String taskName,
        @Parameter(description = "Task type") @RequestParam(name = "taskType", required = false) String taskType,
        @Parameter(description = "groupID") @RequestParam(name = "group", required = false) String group,
        @Parameter(description = "Task status") @RequestParam(name = "status", required = false) String status
    ) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Mono<Page<TaskInfoMetadata>> allTaskMono;
        if (taskName != null && !taskName.isEmpty() || taskType != null && !taskType.isEmpty()
            || group != null && !group.isEmpty() || status != null && !status.isEmpty()) {
            allTaskMono = taskManager.getAllTask(taskName, taskType, group, status, pageable);
        } else {
            allTaskMono = taskManager.getAllTask(pageable);
        }
        return ApiResponse.pageSuccessMono(allTaskMono, TaskListVO::fromTaskConfig);
    }

    @Operation(summary = "Get Task Details", description = "Get task details by task ID")
    @GetMapping("/{id}")
    public Mono<ApiResponse<TaskDetailResponse>> getTaskDetails(
        @PathVariable(name = "id") @Parameter(description = "Task ID") String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.task.idNotEmpty")));
        }
        return taskManager.getTaskDetails(id);
    }

    @Operation(summary = "Get Task Basic Info", description = "Get task basic info by task ID, including name, config, Broker, etc.")
    @GetMapping("/{id}/basic")
    public Mono<ApiResponse<TaskBasicInfoResponse>> getTaskBasicInfo(
        @PathVariable(name = "id") @Parameter(description = "Task ID") String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.task.idNotEmpty")));
        }
        return taskManager.getTaskBasicInfo(id);
    }

    @Operation(summary = "Get Task Config", description = "Get full task config for editing or copying a task")
    @GetMapping("/{id}/config")
    public Mono<ApiResponse<TaskConfig>> getTaskConfig(
        @PathVariable(name = "id") @Parameter(description = "Task ID") String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.task.idNotEmpty")));
        }
        return taskManager.getTaskConfig(id);
    }

    @Operation(summary = "Get Task Statistics", description = "Get task statistics by task ID, including node allocation, client count, etc.")
    @GetMapping("/{id}/statistics")
    public Mono<ApiResponse<TaskStatisticsResponse>> getTaskStatistics(
        @PathVariable(name = "id") @Parameter(description = "Task ID") String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.task.idNotEmpty")));
        }
        return taskManager.getTaskStatistics(id);
    }

    @Operation(summary = "Get Task Subtask Info", description = "Get subtask details by task ID, including per-node allocation and metrics")
    @GetMapping("/{id}/subtasks")
    public Mono<ApiResponse<TaskSubTasksResponse>> getTaskSubTasks(
        @PathVariable(name = "id") @Parameter(description = "Task ID") String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.task.idNotEmpty")));
        }
        return taskManager.getTaskSubTasks(id);
    }

    @Operation(summary = "Update Test Task", description = "Update task configuration")
    @PutMapping("/{id}")
    public Mono<ApiResponse<TaskInfoMetadata>> updateTask(
        @PathVariable(value = "id") @Parameter(description = "Task ID") String id,
        @Valid @RequestBody @Parameter(description = "Task configuration request") TaskRequest taskRequest,
        ServerWebExchange exchange) {
        return taskManager.modifyTask(id, taskRequest)
            .flatMap(task -> auditLogService.record(exchange, AuditAction.TASK_UPDATE, "TASK", id, true,
                    "Update task")
                .thenReturn(ApiResponse.success(task)));
    }

    @Operation(summary = "Stop Task", description = "Manually stop a running task")
    @PostMapping("/stop/{id}")
    public Mono<ApiResponse<String>> stopTask(
        @PathVariable(value = "id") @Parameter(description = "Task ID") String id,
        ServerWebExchange exchange) {
        return taskManager.getTaskWorkerNodeIds(id)
            .flatMap(nodeIds -> Mono.fromFuture(workerCommandGateway.sendStopAll(id, nodeIds)))
            .flatMap(acks -> recordRejectedWorkerCommandAcks(id, "STOP_TASK", acks)
                .thenReturn(workerStopAckResponse(acks)))
            .onErrorResume(e -> recordWorkerCommandException(id, "STOP_TASK", e)
                .thenReturn(ApiResponse.<String>error(rootMessage(e))))
            .flatMap(result -> auditLogService.record(exchange, AuditAction.TASK_STOP, "TASK", id, result.isSuccess(),
                    "Stop task")
                .thenReturn(result));
    }

    @Operation(summary = "Delete Task", description = "Delete the specified task")
    @DeleteMapping("/{id}")
    public Mono<ApiResponse<TaskDetailResponse>> del(
        @PathVariable(value = "id") @Parameter(description = "Task ID") String id,
        ServerWebExchange exchange) {
        return taskManager.delTask(id)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.TASK_DELETE, "TASK", id,
                    response.isSuccess(), "Delete task")
                .thenReturn(response));
    }

    @Operation(summary = "Batch Delete Tasks", description = "Batch delete multiple tasks")
    @DeleteMapping("/batch")
    public Mono<ApiResponse<String>> batchDel(
        @RequestBody @Parameter(description = "Task ID list") List<String> taskIds,
        ServerWebExchange exchange) {
        return taskManager.batchDelTask(taskIds)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.TASK_DELETE, "TASK",
                    String.join(",", taskIds), response.isSuccess(), "Batch delete tasks")
                .thenReturn(response));
    }

    @Operation(summary = "Assign task to nodes", description = "Assign task to the specified nodes")
    @PostMapping("/assign/{id}")
    public Mono<ApiResponse<TaskConfig>> assign(
        @PathVariable(value = "id") @Parameter(description = "Task ID") String id,
        @RequestBody(required = false) @Parameter(description = "Node task allocation request")
        NodeTaskAllocationRequest nodeTaskAllocationRequest,
        ServerWebExchange exchange) {

        return taskManager.assignTask(id, nodeTaskAllocationRequest)
            .flatMap(response -> auditLogService
                .record(exchange, AuditAction.TASK_ALLOCATE, "TASK", id, response.isSuccess(), "Assign task")
                .thenReturn(response))
            .timeout(Duration.ofSeconds(10))
            .onErrorResume(java.util.concurrent.TimeoutException.class,
                e -> Mono.just(ApiResponse.error(Messages.get("error.task.assignTimeout"))));
    }

    @Operation(summary = "Confirm Task", description = "Confirm the task and notify all nodes to start execution")
    @PostMapping("/{id}/confirmTask")
    public Mono<ApiResponse<Void>> confirmTask(
        @PathVariable(value = "id") @Parameter(description = "Task ID") String id,
        ServerWebExchange exchange) {
        return taskManager.getTaskBasicInfo(id)
            .flatMap(response -> {
                if (!response.isSuccess()) {
                    return Mono.just(ApiResponse.error(Messages.get("error.task.notFound")));
                }
                var mainTaskView = response.getData().getMainTaskView();
                TaskStage currentStage = mainTaskView != null ? mainTaskView.taskWorkStage() : null;

                if (currentStage != TaskStage.ASSIGNED) {
                    return Mono.just(ApiResponse.error(Messages.get("error.task.invalidStateForStart", currentStage)));
                }
                TaskTemplate template = mainTaskView.template();
                if (template == null) {
                    return Mono.just(ApiResponse.error(Messages.get("error.task.templateNotEmpty")));
                }

                return taskManager.prepareTaskStartCommands(id)
                    .flatMap(workerCommands -> Mono.fromFuture(workerCommandGateway.sendStartAll(workerCommands)))
                    .flatMap(acks -> recordRejectedWorkerCommandAcks(id, "START_TASK", acks)
                        .thenReturn(workerStartAckResponse(acks)))
                    .onErrorResume(e -> recordWorkerCommandException(id, "START_TASK", e)
                        .thenReturn(ApiResponse.<Void>error(rootMessage(e))))
                    .flatMap(result ->
                        auditLogService.record(exchange, AuditAction.TASK_START, "TASK", id, result.isSuccess(),
                                "Start task")
                            .thenReturn(result));
            });
    }

    private ApiResponse<Void> workerStartAckResponse(List<WorkerCommandAck> acks) {
        return workerAckResponse(acks);
    }

    private ApiResponse<String> workerStopAckResponse(List<WorkerCommandAck> acks) {
        ApiResponse<Void> response = workerAckResponse(acks);
        if (!response.isSuccess()) {
            return ApiResponse.error(response.getMessage());
        }
        return ApiResponse.success(Messages.get("msg.task.submitted"));
    }

    private ApiResponse<Void> workerAckResponse(List<WorkerCommandAck> acks) {
        List<WorkerCommandAck> failedAcks = acks.stream()
            .filter(ack -> ack == null || !ack.accepted())
            .toList();
        if (failedAcks.isEmpty()) {
            return ApiResponse.success();
        }
        String reason = failedAcks.stream()
            .map(ack -> ack == null
                ? "missing ACK"
                : "nodeId=" + ack.getNodeId() + ", status=" + ack.getStatus() + ", reason=" + ack.getReason())
            .reduce((left, right) -> left + "; " + right)
            .orElse("Worker command rejected");
        return ApiResponse.error(reason);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null) {
            return "Worker command failed";
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.toString() : message;
    }

    private Mono<Void> recordRejectedWorkerCommandAcks(String taskId, String commandType, List<WorkerCommandAck> acks) {
        if (acks == null || acks.isEmpty()) {
            return recordWorkerCommandFailure(taskId, null, commandType, "missing ACK", Map.of());
        }
        List<WorkerCommandAck> failedAcks = acks.stream()
            .filter(ack -> ack == null || !ack.accepted())
            .toList();
        if (failedAcks.isEmpty()) {
            return Mono.empty();
        }
        return reactor.core.publisher.Flux.fromIterable(failedAcks)
            .flatMap(ack -> {
                if (ack == null) {
                    return recordWorkerCommandFailure(taskId, null, commandType, "missing ACK", Map.of());
                }
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("eventType", "WORKER_COMMAND_REJECTED");
                metadata.put("commandType", commandType);
                metadata.put("messageId", ack.getMessageId());
                metadata.put("status", String.valueOf(ack.getStatus()));
                metadata.put("ackAtMs", ack.getAckAtMs());
                return recordWorkerCommandFailure(taskId, ack.getNodeId(), commandType, ack.getReason(), metadata);
            })
            .then();
    }

    private Mono<Void> recordWorkerCommandException(String taskId, String commandType, Throwable error) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("eventType", "WORKER_COMMAND_DELIVERY_FAILED");
        metadata.put("commandType", commandType);
        metadata.put("errorType", error == null ? "unknown" : error.getClass().getName());
        return recordWorkerCommandFailure(taskId, null, commandType, rootMessage(error), metadata);
    }

    private Mono<Void> recordWorkerCommandFailure(String taskId, String nodeId, String commandType,
                                                  String reason, Map<String, Object> metadata) {
        TaskStateHistory history = TaskStateHistory.builder()
            .taskId(taskId)
            .nodeId(nodeId)
            .timestamp(Instant.now())
            .source("WORKER_COMMAND")
            .errorMessage(commandType + " failed: " + (reason == null || reason.isBlank() ? "unknown" : reason))
            .metadata(metadata)
            .eventSeq(-Math.abs(System.nanoTime()))
            .build();
        return taskStateHistoryRepository.save(history)
            .then()
            .onErrorResume(e -> {
                log.warn("Failed to record worker command failure, taskId={}, nodeId={}, commandType={}",
                    taskId, nodeId, commandType, e);
                return Mono.empty();
            });
    }

    @GetMapping("/stop")
    public Boolean stop(@RequestParam(name = "id") Long id) {
        return vertx.cancelTimer(id);
    }

    @Operation(summary = "Get Task Report", description = "Generate complete test report by task ID, including throughput, latency, connection metrics")
    @GetMapping("/{id}/report")
    public Mono<ApiResponse<TaskReportResponse>> getTaskReport(
        @PathVariable(name = "id") @Parameter(description = "Task ID") String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.task.idNotEmpty")));
        }
        return taskReportService.generateReport(id)
            .map(ApiResponse::success)
            .defaultIfEmpty(ApiResponse.error(Messages.get("msg.task.noReport")))
            .onErrorResume(e -> {
                log.error("Failed to generate report for task: {}", id, e);
                return Mono.just(ApiResponse.error(Messages.get("error.data.timeout")));
            });
    }

    @Operation(summary = "Get Task Template List", description = "Return all available task template types")
    @GetMapping("/templates")
    public ApiResponse<List<Map<String, String>>> getTemplates() {

        List<Map<String, String>> templates = Arrays.stream(TaskTemplate.values())
            .filter(t -> t != TaskTemplate.CONN_IMMEDIATE_DISCONNECT)
            .filter(t -> t != TaskTemplate.CHAOS_STANDARD)
            .map(t -> {
                String type = t.name().startsWith("CONN") ? "CONN"
                    : t.name().startsWith("PUBSUB") ? "PUBSUB"
                      : t.name().startsWith("CHAOS") ? "CHAOS" : "OTHER";
                return Map.of("value", t.name(), "label", t.getLabel(), "type", type);
            })
            .toList();
        return ApiResponse.success(templates);
    }

    @Operation(summary = "Get Task Status Change History", description = "Query state transition timestamp records by task ID (and optional node ID), sorted by time ascending")
    @GetMapping("/{id}/state-history")
    public Mono<ApiResponse<List<TaskStateHistoryVO>>> getStateHistory(
        @PathVariable(name = "id") @Parameter(description = "Task ID") String id,
        @Parameter(description = "Node ID (if not provided, queries main task history)")
        @RequestParam(name = "nodeId", required = false)
        String nodeId) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.task.idNotEmpty")));
        }
        var flux = (nodeId != null && !nodeId.isBlank())
            ? taskStateHistoryRepository.findByTaskIdAndNodeIdOrderByTimestampDesc(id, nodeId)
            : taskStateHistoryRepository.findByTaskIdOrderByTimestampDesc(id);
        return flux
            .filter(history -> history.getFromStage() != null
                && history.getToStage() != null
                && history.getTimestamp() != null)
            .map(TaskStateHistoryVO::from)
            .collectList()
            .map(list -> {
                java.util.Collections.reverse(list);
                return ApiResponse.success(list);
            });
    }

    @Operation(summary = "Get Task Diagnostics", description = "Get aggregated task troubleshooting diagnostics by task ID")
    @GetMapping("/{id}/diagnostics")
    public Mono<ApiResponse<TaskDiagnosticsResponse>> getDiagnostics(
        @PathVariable(name = "id") @Parameter(description = "Task ID") String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.task.idNotEmpty")));
        }
        return taskDiagnosticsService.getDiagnostics(id);
    }

    @Operation(summary = "Get Task Log Summary", description = "Get recent whitelisted log lines that contain task ID")
    @GetMapping("/{id}/log-summary")
    public Mono<ApiResponse<TaskLogSummaryResponse>> getLogSummary(
        @PathVariable(name = "id") @Parameter(description = "Task ID") String id,
        @Parameter(description = "Maximum matched lines, capped at 1000")
        @RequestParam(name = "lines", required = false) Integer lines) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.task.idNotEmpty")));
        }
        return taskLogSummaryService.getLogSummary(id, lines);
    }

}
