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

package org.apache.bifromq.testsuite.app.task;

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.app.bean.PipelineStageInfo;
import org.apache.bifromq.testsuite.app.bean.PipelineStagesConfig;
import org.apache.bifromq.testsuite.app.bean.TaskDetailResponse;
import org.apache.bifromq.testsuite.app.bean.TaskStatistics;
import org.apache.bifromq.testsuite.app.bean.dto.BrokerEntry;
import org.apache.bifromq.testsuite.app.bean.dto.NodeTaskAllocationRequest;
import org.apache.bifromq.testsuite.app.bean.dto.TaskRequest;
import org.apache.bifromq.testsuite.app.bean.vo.NodeTaskAllocationVO;
import org.apache.bifromq.testsuite.app.bean.vo.SubTaskDetail;
import org.apache.bifromq.testsuite.app.bean.vo.TaskBasicInfoResponse;
import org.apache.bifromq.testsuite.app.bean.vo.TaskConfigView;
import org.apache.bifromq.testsuite.app.bean.vo.TaskStatisticsResponse;
import org.apache.bifromq.testsuite.app.bean.vo.TaskSubTasksResponse;
import org.apache.bifromq.testsuite.app.cluster.core.ClusterDataManager;
import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot;
import org.apache.bifromq.testsuite.app.database.repository.MqttBrokerRepository;
import org.apache.bifromq.testsuite.app.database.repository.MqttGroupRepository;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskMetricsSnapshotRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskStateHistoryRepository;
import org.apache.bifromq.testsuite.app.database.service.TaskInfoMetadataService;
import org.apache.bifromq.testsuite.app.database.service.TaskMetricsSnapshotService;
import org.apache.bifromq.testsuite.app.profile.TaskProfile;
import org.apache.bifromq.testsuite.app.profile.TaskProfileService;
import org.apache.bifromq.testsuite.app.task.runtime.TaskRuntimeStates;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.metric.CounterMetricData;
import org.apache.bifromq.testsuite.metric.TimerMetricData;
import org.apache.bifromq.testsuite.payload.TemplatePayloadStrategy;
import org.apache.bifromq.testsuite.pipeline.PipelineStageSnapshot;
import org.apache.bifromq.testsuite.statemachine.StateMachine;
import org.apache.bifromq.testsuite.statemachine.TaskStateMachineConfig;
import org.apache.bifromq.testsuite.web.ApiException;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskCommand;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@ConditionalOnControlPlane
public class TaskManager {


    private static final String TOPIC_NODE_ID_PLACEHOLDER = "{nodeId}";
    private static final long DYNAMIC_QPS_START_DELAY_MS = 2000L;

    @Resource
    private ClusterDataManager clusterDataManager;

    @Resource
    private TaskProfileService taskProfileService;

    @Resource
    private TaskInfoMetadataService taskInfoMetadataService;

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Resource
    private NodeTaskRepository nodeTaskRepository;

    @Resource
    private MqttBrokerRepository mqttBrokerRepository;

    @Resource
    private MqttGroupRepository mqttGroupRepository;

    @Resource
    private TaskMetricsSnapshotRepository taskMetricsSnapshotRepository;

    @Resource
    private TaskMetricsSnapshotService taskMetricsSnapshotService;

    @Resource
    private TaskStateHistoryRepository taskStateHistoryRepository;

    @Resource
    private PipelineStagesConfig pipelineStagesConfig;


    private static String toExternalStageName(TaskStage stage) {
        if (stage == TaskStage.STARTING) {
            return "START";
        }
        return stage.name();
    }

    private static TaskProfile toProfileSummary(TaskProfile profile) {
        return TaskProfile.builder()
            .id(profile.getId())
            .name(profile.getName())
            .description(profile.getDescription())
            .group(profile.getGroup())
            .totalDurationMs(profile.getTotalDurationMs())
            .maxQps(profile.getMaxQps())
            .peakQps(profile.getPeakQps())
            .avgQps(profile.getAvgQps())
            .integral(profile.getIntegral())
            .targetTotalCount(profile.getTargetTotalCount())
            .createdAt(profile.getCreatedAt())
            .build();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        if (message == null || message.isBlank()) {
            return Messages.get("error.task.assignTimeout");
        }
        return message;
    }

    private Mono<TaskProfile> findPublishProfile(TaskConfig taskConfig) {
        if (taskConfig == null || taskConfig.getQpsMode() != TaskConfig.QpsMode.DYNAMIC
            || taskConfig.getProfileConfig() == null
            || taskConfig.getProfileConfig().getProfileId() == null
            || taskConfig.getProfileConfig().getProfileId().isBlank()) {
            return Mono.empty();
        }
        String profileId = taskConfig.getProfileConfig().getProfileId();
        return taskProfileService.getTaskProfileById(profileId)
            .map(TaskManager::toProfileSummary)
            .onErrorResume(ex -> {
                log.warn("Failed to load publish profile for task detail: profileId={}", profileId, ex);
                return Mono.empty();
            });
    }

    public Mono<TaskInfoMetadata> addTask(TaskRequest taskRequest) {
        if (taskRequest == null) {
            return Mono.error(new ApiException("Task request cannot be null"));
        }
        if (isDeprecatedChaosTask(taskRequest)) {
            return Mono.error(new ApiException(Messages.get("error.task.chaosDeprecated")));
        }
        if (taskRequest.getGroup() == null || taskRequest.getGroup().isBlank()) {
            return Mono.error(new ApiException(Messages.get("error.task.groupNotEmpty")));
        }
        if (taskRequest.getBrokers() == null || taskRequest.getBrokers().isEmpty()) {
            return Mono.error(new ApiException(Messages.get("error.task.brokerListNotEmpty")));
        }

        // Validate group exists
        String groupId = taskRequest.getGroup();
        return mqttGroupRepository.findById(groupId)
            .switchIfEmpty(Mono.error(new ApiException(Messages.get("error.group.notFound"))))
            .flatMap(group -> {
                // Validate group type is BROKER
                if (!"BROKER".equals(group.getType())) {
                    return Mono.error(new ApiException(Messages.get("error.group.typeMismatch")));
                }
                return Mono.just(true);
            })
            .flatMap(ignored -> doAddTask(taskRequest));
    }

    private Mono<TaskInfoMetadata> doAddTask(TaskRequest taskRequest) {

        if (PayloadMode.TEMPLATE == taskRequest.getPayloadMode()) {
            try {
                TemplatePayloadStrategy.validateTemplate(taskRequest.getPayloadTemplate());
            } catch (IllegalArgumentException e) {
                return Mono.error(new ApiException(Messages.get("error.task.msgTemplateFormat", e.getMessage())));
            }
        }


        return taskInfoMetadataRepository.findByTaskName(taskRequest.getTaskName())
            .flatMap(existing -> Mono.<TaskInfoMetadata>error(
                new ApiException(Messages.get("error.task.nameExists", taskRequest.getTaskName()))))
            .switchIfEmpty(Mono.defer(() -> {
                List<String> brokerIds = taskRequest.getBrokers().stream()
                    .map(BrokerEntry::getBrokerId)
                    .collect(Collectors.toList());


                return validateBrokersInSameGroup(brokerIds)
                    .flatMap(isValid -> {
                        if (!isValid) {
                            return Mono.error(new ApiException(Messages.get("error.task.brokerSameGroup")));
                        }

                        return Flux.fromIterable(brokerIds)
                            .flatMap(brokerId -> mqttBrokerRepository.findByBrokerId(brokerId))
                            .collectList()
                            .flatMap(dbBrokers -> {
                                Map<String, MqttBroker> brokerMap = dbBrokers.stream()
                                    .collect(Collectors.toMap(MqttBroker::getBrokerId, b -> b, (a, b) -> a));
                                List<MqttBroker> brokers = taskRequest.getBrokers().stream()
                                    .map(r -> {
                                        MqttBroker dbBroker = brokerMap.get(r.getBrokerId());
                                        if (dbBroker != null) {
                                            return MqttBroker.builder()
                                                .id(dbBroker.getId())
                                                .brokerId(dbBroker.getBrokerId())
                                                .name(dbBroker.getName())
                                                .host(dbBroker.getHost())
                                                .port(dbBroker.getPort())
                                                .group(dbBroker.getGroup())
                                                .build();
                                        }

                                        return MqttBroker.builder()
                                            .brokerId(r.getBrokerId())
                                            .host(r.getHost())
                                            .port(r.getPort())
                                            .build();
                                    })
                                    .toList();

                                TaskConfig mainTaskConfig = taskRequest.toTaskConfig();
                                String nextTaskId = RandomStringUtils.secure().next(8, true, true);
                                mainTaskConfig.setTaskId(nextTaskId);
                                mainTaskConfig.setGroup(taskRequest.getGroup());
                                TaskInfoMetadata taskInfoMetadata = TaskInfoMetadata.builder()
                                    .taskName(taskRequest.getTaskName())
                                    .taskConfig(mainTaskConfig)
                                    .createTime(LocalDateTime.now())
                                    .taskId(nextTaskId)
                                    .brokers(brokers)
                                    .group(taskRequest.getGroup())
                                    .build();
                                log.info("add task taskInfoMetadata: {}", taskInfoMetadata);
                                return taskInfoMetadataService.insertTaskInfoMetadata(taskInfoMetadata);
                            });
                    });
            }));
    }

    public Mono<ApiResponse<TaskConfig>> assignTask(String id, NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        return taskInfoMetadataRepository.findById(id)
            .flatMap(taskInfoMetadata -> {
                TaskConfig taskConfig = taskInfoMetadata.getTaskConfig();
                TaskStage currentStage = TaskRuntimeStates.mainStage(taskInfoMetadata);


                if (currentStage == TaskStage.ASSIGNED) {
                    return Mono.just(ApiResponse.success(taskConfig));
                }

                if (TaskRuntimeStates.isRunning(currentStage)) {
                    return Mono.just(
                        ApiResponse.<TaskConfig>error(Messages.get("error.task.invalidStateForAssign", currentStage)));
                }

                return inlineProfileDataPoints(taskConfig)
                    .flatMap(resolvedConfig ->
                        Mono.fromFuture(clusterDataManager.assignCheck(id, resolvedConfig, nodeTaskAllocationRequest))
                            .then(Mono.defer(() -> {
                                taskInfoMetadata.setTaskConfig(resolvedConfig);
                                TaskRuntimeStates.applyMainStage(
                                    taskInfoMetadata, TaskStage.ASSIGNED, java.time.Instant.now());
                                return taskInfoMetadataRepository.save(taskInfoMetadata).then();
                            }))
                            .thenReturn(ApiResponse.success(resolvedConfig)))
                    .onErrorResume(e -> {
                        String reason = rootMessage(e);
                        log.warn("Task assignment rejected, taskId={}, reason={}", id, reason, e);
                        return Mono.just(ApiResponse.error(reason));
                    });
            })
            .switchIfEmpty(Mono.just(ApiResponse.error(Messages.get("error.task.notFound"))));
    }

    public Mono<ApiResponse<NodeTaskAllocationVO>> calculateNodeTaskAllocation(String id) {
        return taskInfoMetadataRepository.findById(id)
            .map(taskInfoMetadata -> {
                TaskConfig taskConfig = taskInfoMetadata.getTaskConfig();
                return ApiResponse.success(clusterDataManager.calcuTasksToNodes(taskConfig));
            })
            .switchIfEmpty(Mono.just(ApiResponse.error(Messages.get("error.task.notFound"))));
    }

    public Mono<Void> prepareTaskStart(String id) {
        return prepareTaskStartCommands(id).then();
    }

    public Mono<List<WorkerTaskCommand>> prepareTaskStartCommands(String id) {
        long plannedStartAtMs = System.currentTimeMillis() + DYNAMIC_QPS_START_DELAY_MS;
        return taskInfoMetadataRepository.findById(id)
            .switchIfEmpty(Mono.error(new ApiException(Messages.get("error.task.notFound"))))
            .flatMap(taskInfoMetadata -> {
                TaskConfig mainTaskConfig = taskInfoMetadata.getTaskConfig();
                TaskRuntimeStates.applyMainPlannedStart(taskInfoMetadata, plannedStartAtMs);
                TaskRuntimeStates.applyMainStage(taskInfoMetadata, TaskStage.STARTING, java.time.Instant.now());
                return taskInfoMetadataRepository.updateTaskConfigById(id, mainTaskConfig)
                    .then(Mono.fromFuture(clusterDataManager.prepareAssignedTaskStart(id, plannedStartAtMs)))
                    .thenMany(nodeTaskRepository.findAllByTaskId(id))
                    .map(NodeTask::getWorkerTaskCommand)
                    .filter(command -> command != null && command.workerTaskSpec() != null)
                    .collectList()
                    .flatMap(commands -> {
                        if (commands.isEmpty()) {
                            return Mono.error(new ApiException("No worker command prepared for task: " + id));
                        }
                        return Mono.just(commands);
                    });
            });
    }

    public Mono<List<String>> getTaskWorkerNodeIds(String taskId) {
        return nodeTaskRepository.findAllByTaskId(taskId)
            .map(NodeTask::getNodeId)
            .filter(nodeId -> nodeId != null && !nodeId.isBlank())
            .distinct()
            .collectList()
            .flatMap(nodeIds -> {
                if (nodeIds.isEmpty()) {
                    return Mono.error(new ApiException("No worker nodes found for task: " + taskId));
                }
                return Mono.just(nodeIds);
            });
    }

    public Mono<TaskInfoMetadata> modifyTask(String taskId, TaskRequest taskRequest) {
        if (taskRequest == null) {
            return Mono.error(new ApiException("Task request cannot be null"));
        }
        if (isDeprecatedChaosTask(taskRequest)) {
            return Mono.error(new ApiException(Messages.get("error.task.chaosDeprecated")));
        }

        if (taskId == null) {
            return Mono.error(new ApiException("Task id cannot be null"));
        }

        return taskInfoMetadataRepository.findByTaskName(taskRequest.getTaskName())
            .filter(t -> !taskId.equals(t.getTaskId()))
            .hasElement()
            .flatMap(nameExists -> {
                if (nameExists) {
                    return Mono.error(
                        new ApiException(Messages.get("error.task.nameExists", taskRequest.getTaskName())));
                }

                List<String> brokerIds = taskRequest.getBrokers().stream()
                    .map(BrokerEntry::getBrokerId)
                    .collect(Collectors.toList());

                return validateBrokersInSameGroup(brokerIds)
                    .flatMap(isValid -> {
                        if (!isValid) {
                            return Mono.error(new ApiException(Messages.get("error.task.brokerSameGroup")));
                        }

                        return taskInfoMetadataRepository.findById(taskId)
                            .flatMap(existingMetadata -> {

                                return mqttBrokerRepository.findAllById(brokerIds)
                                    .collectList()
                                    .flatMap(dbBrokers -> {

                                        Map<String, MqttBroker> brokerMap = dbBrokers.stream()
                                            .collect(Collectors.toMap(MqttBroker::getId, b -> b, (a, b) -> a));

                                        List<MqttBroker> brokers = taskRequest.getBrokers().stream()
                                            .map(r -> {
                                                MqttBroker dbBroker = brokerMap.get(r.getBrokerId());
                                                if (dbBroker != null) {
                                                    return MqttBroker.builder()
                                                        .id(dbBroker.getId())
                                                        .brokerId(dbBroker.getBrokerId())
                                                        .name(dbBroker.getName())
                                                        .host(dbBroker.getHost())
                                                        .port(dbBroker.getPort())
                                                        .group(dbBroker.getGroup())
                                                        .build();
                                                }

                                                return MqttBroker.builder()
                                                    .brokerId(r.getBrokerId())
                                                    .host(r.getHost())
                                                    .port(r.getPort())
                                                    .build();
                                            })
                                            .toList();

                                        TaskConfig mainTaskConfig = taskRequest.toTaskConfig();
                                        mainTaskConfig.setTaskId(taskId);

                                        existingMetadata.setTaskConfig(mainTaskConfig);
                                        existingMetadata.setBrokers(brokers);
                                        existingMetadata.setTaskName(taskRequest.getTaskName());
                                        existingMetadata.setGroup(taskRequest.getGroup());

                                        log.info("modify task: {}", existingMetadata);

                                        return taskInfoMetadataRepository.save(existingMetadata);
                                    });
                            })
                            .switchIfEmpty(Mono.error(new ApiException("Task not found with id: " + taskId)));
                    });
            });
    }

    private boolean isDeprecatedChaosTask(TaskRequest taskRequest) {
        return taskRequest.getTaskType() == TaskConfig.TaskType.CHAOS
            || TaskTemplate.CHAOS_STANDARD.name().equals(taskRequest.getTemplate());
    }

    public Mono<ApiResponse<TaskDetailResponse>> delTask(String taskId) {
        return taskInfoMetadataRepository.findById(taskId)
            .flatMap(taskInfoMetadata -> {
                TaskStage taskStage = TaskRuntimeStates.mainStage(taskInfoMetadata);
                if (TaskRuntimeStates.isRunning(taskStage)) {
                    return Mono.just(ApiResponse.<TaskDetailResponse>error(Messages.get("error.task.alreadyStarted")));
                }
                return Mono.zip(
                    taskInfoMetadataRepository.deleteById(taskId),
                    nodeTaskRepository.deleteByTaskId(taskId),
                    taskMetricsSnapshotRepository.deleteByTaskId(taskId),
                    taskStateHistoryRepository.deleteByTaskId(taskId)
                ).thenReturn(ApiResponse.<TaskDetailResponse>success());
            })
            .switchIfEmpty(Mono.just(ApiResponse.error(Messages.get("error.task.notFound"))));
    }

    public Mono<ApiResponse<String>> batchDelTask(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Mono.just(ApiResponse.error(Messages.get("error.task.idListEmpty")));
        }

        ConcurrentLinkedQueue<String> deletedIds =
            new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> failedIds =
            new ConcurrentLinkedQueue<>();

        return Flux.fromIterable(taskIds)
            .flatMap(taskId -> taskInfoMetadataRepository.findById(taskId)
                .flatMap(taskInfoMetadata -> {
                    TaskStage taskStage = TaskRuntimeStates.mainStage(taskInfoMetadata);
                    if (TaskRuntimeStates.isRunning(taskStage)) {
                        failedIds.add(taskId + "(" + Messages.get("error.task.alreadyStarted") + ")");
                        return Mono.empty();
                    }
                    return Mono.zip(
                        taskInfoMetadataRepository.deleteById(taskId),
                        nodeTaskRepository.deleteByTaskId(taskId),
                        taskMetricsSnapshotRepository.deleteByTaskId(taskId),
                        taskStateHistoryRepository.deleteByTaskId(taskId)
                    ).doOnSuccess(v -> deletedIds.add(taskId));
                })
                .switchIfEmpty(
                    Mono.fromRunnable(() -> failedIds.add(taskId + "(" + Messages.get("error.task.notFound") + ")"))))
            .then(Mono.fromSupplier(() -> {
                if (failedIds.isEmpty()) {
                    return ApiResponse.success(Messages.get("msg.task.deleteSuccess", deletedIds.size()));
                } else {
                    return ApiResponse.success(
                        Messages.get("msg.task.deletePartial", deletedIds.size(), failedIds.size(),
                            String.join(", ", failedIds)));
                }
            }));
    }

    public Mono<Page<TaskInfoMetadata>> getAllTask(Pageable pageable) {
        return taskInfoMetadataService.findAll(pageable);
    }


    public Mono<Page<TaskInfoMetadata>> getAllTask(String taskName, String taskType, String group, String status,
                                                   Pageable pageable) {
        return taskInfoMetadataService.findByFilters(taskName, taskType, group, status, pageable);
    }

    public Mono<ApiResponse<TaskDetailResponse>> getTaskDetails(String taskId) {
        return taskInfoMetadataRepository.findById(taskId)
            .flatMap(taskInfoMetadata -> {
                TaskConfig mainTask = taskInfoMetadata.getTaskConfig();
                TaskStage taskStage = TaskRuntimeStates.mainStage(taskInfoMetadata);

                TaskDetailResponse response = new TaskDetailResponse();
                response.setSuccess(true);
                response.setTaskId(taskId);
                response.setMainTaskView(TaskConfigView.fromTaskConfig(
                    mainTask, taskStage, TaskRuntimeStates.mainPlannedStartAtMs(taskInfoMetadata)));
                response.setBrokers(taskInfoMetadata.getBrokers());
                response.setTaskName(taskInfoMetadata.getTaskName());
                response.setGroup(taskInfoMetadata.getGroup());
                if (taskInfoMetadata.getCreateTime() != null) {
                    response.setCreateTime(taskInfoMetadata.getCreateTime()
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                }


                if (taskInfoMetadata.getStartTime() != null) {
                    response.setStartTime(taskInfoMetadata.getStartTime()
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                }
                if (taskInfoMetadata.getEndTime() != null) {
                    response.setEndTime(taskInfoMetadata.getEndTime()
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                }


                boolean isCompleted = taskStage == TaskStage.SHUTDOWN || taskStage == TaskStage.STOPPED;
                response.setMetricsFromSnapshot(isCompleted);


                List<PipelineStageInfo> stages = pipelineStagesConfig.getStages(mainTask.getTemplate());
                response.setPipelineStages(stages);
                int stageIndex = pipelineStagesConfig.getStageIndex(mainTask.getTemplate(), taskStage.name());

                if (pipelineStagesConfig.isTerminalStage(taskStage.name())) {
                    stageIndex = stages.size();
                }
                response.setCurrentStageIndex(stageIndex);


                StateMachine<TaskStage, TaskEvent> displayMachine =
                    org.apache.bifromq.testsuite.statemachine.TaskStateMachineConfig.create();
                response.setStateTransitions(TaskStateMachineConfig.toTransitionMeta(displayMachine));

                return findPublishProfile(mainTask)
                    .doOnNext(response::setPublishProfile)
                    .then(nodeTaskRepository.findAllByTaskId(taskId).collectList())
                    .flatMap(nodeTasks -> {
                        Map<String, TaskConfigView> subTasks = nodeTasks.stream()
                            .collect(Collectors.toMap(
                                NodeTask::getNodeId,
                                nodeTask -> TaskConfigView.fromTaskConfig(
                                    nodeTask.getTaskConfig(),
                                    TaskRuntimeStates.nodeStage(nodeTask),
                                    TaskRuntimeStates.nodePlannedStartAtMs(nodeTask))
                            ));
                        response.setSubTasks(subTasks);

                        Map<String, SubTaskDetail> subTaskDetails = nodeTasks.stream()
                            .collect(Collectors.toMap(
                                NodeTask::getNodeId,
                                nt -> SubTaskDetail.builder()
                                    .nodeId(nt.getNodeId())
                                    .nodeName(nt.getNodeName() != null ? nt.getNodeName() : "-")
                                    .taskType(nt.getTaskConfig() != null && nt.getTaskConfig().getTaskType() != null
                                        ? nt.getTaskConfig().getTaskType().name() : "UNKNOWN")
                                    .totalClientCount(
                                        nt.getTaskConfig() != null ? nt.getTaskConfig().getTotalClientCount() : 0)
                                    .taskWorkStage(
                                        toExternalStageName(TaskRuntimeStates.nodeStage(nt)))
                                    .pipelineStages(nt.getPipelineStages() == null ? null :
                                        nt.getPipelineStages().stream()
                                        .filter(PipelineStageSnapshot::isVisible)
                                        .collect(Collectors.toList()))
                                    .build()
                            ));
                        response.setSubTaskDetails(subTaskDetails);


                        calculateStatistics(response, nodeTasks);


                        if (isCompleted) {
                            return taskMetricsSnapshotService.findMergedByTaskId(taskId)
                                .map(snapshot -> {

                                    if (snapshot.getNodeMetrics() != null) {
                                        snapshot.getNodeMetrics().forEach((nodeId, nodeSnapshot) -> {
                                            SubTaskDetail detail = response.getSubTaskDetails().get(nodeId);
                                            if (detail != null) {
                                                detail.setCounterMetrics(nodeSnapshot.getCounterMetrics());
                                                detail.setTimerMetrics(nodeSnapshot.getTimerMetrics());
                                            }
                                        });
                                    }


                                    populateRuntimeStatistics(response.getStatistics(), snapshot);

                                    return ApiResponse.success(response);
                                })
                                .defaultIfEmpty(ApiResponse.success(response));
                        } else {
                            return Mono.just(ApiResponse.success(response));
                        }
                    });
            })
            .switchIfEmpty(Mono.just(ApiResponse.error(Messages.get("error.task.notFound"))));
    }


    public Mono<ApiResponse<TaskBasicInfoResponse>> getTaskBasicInfo(String taskId) {
        return taskInfoMetadataRepository.findById(taskId)
            .flatMap(taskInfoMetadata -> {
                TaskConfig mainTask = taskInfoMetadata.getTaskConfig();

                TaskBasicInfoResponse response = new TaskBasicInfoResponse();
                response.setTaskId(taskId);
                response.setTaskName(taskInfoMetadata.getTaskName());
                response.setGroup(taskInfoMetadata.getGroup());
                response.setMainTaskView(TaskConfigView.fromTaskConfig(
                    mainTask,
                    TaskRuntimeStates.mainStage(taskInfoMetadata),
                    TaskRuntimeStates.mainPlannedStartAtMs(taskInfoMetadata)));
                response.setBrokers(taskInfoMetadata.getBrokers());

                if (taskInfoMetadata.getCreateTime() != null) {
                    response.setCreateTime(taskInfoMetadata.getCreateTime()
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                }
                if (taskInfoMetadata.getStartTime() != null) {
                    response.setStartTime(taskInfoMetadata.getStartTime()
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                }
                if (taskInfoMetadata.getEndTime() != null) {
                    response.setEndTime(taskInfoMetadata.getEndTime()
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                }

                return findPublishProfile(mainTask)
                    .doOnNext(response::setPublishProfile)
                    .thenReturn(ApiResponse.success(response));
            })
            .switchIfEmpty(Mono.just(ApiResponse.error(Messages.get("error.task.notFound"))));
    }

    public Mono<ApiResponse<TaskConfig>> getTaskConfig(String taskId) {
        return taskInfoMetadataRepository.findById(taskId)
            .map(taskInfoMetadata -> ApiResponse.success(taskInfoMetadata.getTaskConfig()))
            .switchIfEmpty(Mono.just(ApiResponse.error(Messages.get("error.task.notFound"))));
    }

    public Mono<ApiResponse<TaskStatisticsResponse>> getTaskStatistics(String taskId) {
        return taskInfoMetadataRepository.findById(taskId)
            .flatMap(taskInfoMetadata -> {
                TaskStage taskStage = TaskRuntimeStates.mainStage(taskInfoMetadata);
                boolean isCompleted = taskStage == TaskStage.SHUTDOWN || taskStage == TaskStage.STOPPED;

                TaskStatisticsResponse response = new TaskStatisticsResponse();
                response.setTaskId(taskId);
                response.setMetricsFromSnapshot(isCompleted);

                return nodeTaskRepository.findAllByTaskId(taskId).collectList()
                    .map(nodeTasks -> {
                        TaskStatistics stats = calculateStatisticsFromNodeTasks(nodeTasks);
                        response.setStatistics(stats);
                        return response;
                    })
                    .flatMap(resp -> {
                        if (isCompleted) {
                            return taskMetricsSnapshotService.findMergedByTaskId(taskId)
                                .map(snapshot -> {
                                    populateRuntimeStatistics(resp.getStatistics(), snapshot);
                                    return ApiResponse.success(resp);
                                })
                                .defaultIfEmpty(ApiResponse.success(resp));
                        }
                        return Mono.just(ApiResponse.success(resp));
                    });
            })
            .switchIfEmpty(Mono.just(ApiResponse.error(Messages.get("error.task.notFound"))));
    }

    public Mono<ApiResponse<TaskSubTasksResponse>> getTaskSubTasks(String taskId) {
        return taskInfoMetadataRepository.findById(taskId)
            .flatMap(taskInfoMetadata -> {
                TaskStage taskStage = TaskRuntimeStates.mainStage(taskInfoMetadata);
                boolean isCompleted = taskStage == TaskStage.SHUTDOWN || taskStage == TaskStage.STOPPED;

                return nodeTaskRepository.findAllByTaskId(taskId).collectList()
                    .map(nodeTasks -> {
                        TaskSubTasksResponse response = new TaskSubTasksResponse();
                        response.setTaskId(taskId);

                        Map<String, TaskConfigView> subTasks = nodeTasks.stream()
                            .collect(Collectors.toMap(
                                NodeTask::getNodeId,
                                nodeTask -> TaskConfigView.fromTaskConfig(
                                    nodeTask.getTaskConfig(),
                                    TaskRuntimeStates.nodeStage(nodeTask),
                                    TaskRuntimeStates.nodePlannedStartAtMs(nodeTask))
                            ));
                        response.setSubTasks(subTasks);

                        Map<String, SubTaskDetail> subTaskDetails = nodeTasks.stream()
                            .collect(Collectors.toMap(
                                NodeTask::getNodeId,
                                nt -> SubTaskDetail.builder()
                                    .nodeId(nt.getNodeId())
                                    .nodeName(nt.getNodeName() != null ? nt.getNodeName() : "-")

                                    .taskType(nt.getTaskConfig() != null && nt.getTaskConfig().getTaskType() != null
                                        ? nt.getTaskConfig().getTaskType().name() : "UNKNOWN")
                                    .totalClientCount(
                                        nt.getTaskConfig() != null ? nt.getTaskConfig().getTotalClientCount() : 0)
                                    .taskWorkStage(
                                        toExternalStageName(TaskRuntimeStates.nodeStage(nt)))
                                    .pipelineStages(nt.getPipelineStages() == null ? null :
                                        nt.getPipelineStages().stream()
                                        .filter(PipelineStageSnapshot::isVisible)
                                        .collect(Collectors.toList()))
                                    .build()
                            ));
                        response.setSubTaskDetails(subTaskDetails);

                        return response;
                    })
                    .flatMap(response -> {
                        if (isCompleted) {
                            return taskMetricsSnapshotService.findMergedByTaskId(taskId)
                                .map(snapshot -> {
                                    if (snapshot.getNodeMetrics() != null) {
                                        snapshot.getNodeMetrics().forEach((nodeId, nodeSnapshot) -> {
                                            SubTaskDetail detail = response.getSubTaskDetails().get(nodeId);
                                            if (detail != null) {
                                                detail.setCounterMetrics(nodeSnapshot.getCounterMetrics());
                                                detail.setTimerMetrics(nodeSnapshot.getTimerMetrics());
                                            }
                                        });
                                    }
                                    return ApiResponse.success(response);
                                })
                                .defaultIfEmpty(ApiResponse.success(response));
                        }
                        return Mono.just(ApiResponse.success(response));
                    });
            })
            .switchIfEmpty(Mono.just(ApiResponse.error(Messages.get("error.task.notFound"))));
    }

    private TaskStatistics calculateStatisticsFromNodeTasks(List<NodeTask> nodeTasks) {
        if (nodeTasks == null || nodeTasks.isEmpty()) {
            return new TaskStatistics();
        }

        int totalAssignedClients = 0;
        int minClientsPerNode = Integer.MAX_VALUE;
        int maxClientsPerNode = 0;
        Set<String> activeNodes = new HashSet<>();

        for (NodeTask nodeTask : nodeTasks) {
            TaskConfig subTask = nodeTask.getTaskConfig();
            int clientCount = subTask.getTotalClientCount();
            totalAssignedClients += clientCount;
            minClientsPerNode = Math.min(minClientsPerNode, clientCount);
            maxClientsPerNode = Math.max(maxClientsPerNode, clientCount);
            activeNodes.add(nodeTask.getNodeId());
        }

        TaskStatistics stats = new TaskStatistics();
        stats.setTotalNodes(activeNodes.size());
        stats.setTotalAssignedClients(totalAssignedClients);
        stats.setMinClientsPerNode(minClientsPerNode == Integer.MAX_VALUE ? 0 : minClientsPerNode);
        stats.setMaxClientsPerNode(maxClientsPerNode);
        stats.setAverageClientsPerNode(totalAssignedClients / activeNodes.size());
        return stats;
    }


    private void calculateStatistics(TaskDetailResponse response, List<NodeTask> nodeTasks) {
        if (nodeTasks == null || nodeTasks.isEmpty()) {
            return;
        }

        int totalAssignedClients = 0;
        int minClientsPerNode = Integer.MAX_VALUE;
        int maxClientsPerNode = 0;
        Set<String> activeNodes = new HashSet<>();

        for (NodeTask nodeTask : nodeTasks) {
            TaskConfig subTask = nodeTask.getTaskConfig();
            if (subTask == null) {
                continue;
            }
            int clientCount = subTask.getTotalClientCount();
            totalAssignedClients += clientCount;
            minClientsPerNode = Math.min(minClientsPerNode, clientCount);
            maxClientsPerNode = Math.max(maxClientsPerNode, clientCount);
            activeNodes.add(nodeTask.getNodeId());
        }

        TaskStatistics stats = new TaskStatistics();
        stats.setTotalNodes(activeNodes.size());
        stats.setTotalAssignedClients(totalAssignedClients);
        stats.setMinClientsPerNode(minClientsPerNode == Integer.MAX_VALUE ? 0 : minClientsPerNode);
        stats.setMaxClientsPerNode(maxClientsPerNode);
        stats.setAverageClientsPerNode(activeNodes.isEmpty() ? 0 : totalAssignedClients / activeNodes.size());

        response.setStatistics(stats);
    }


    private void populateRuntimeStatistics(TaskStatistics stats, TaskMetricsSnapshot snapshot) {
        if (stats == null || snapshot == null) {
            return;
        }

        if (snapshot.getDurationMs() != null) {
            stats.setActualDurationMs(snapshot.getDurationMs());
        }

        if (snapshot.getCounterMetrics() != null) {
            for (CounterMetricData counter : snapshot.getCounterMetrics()) {
                switch (counter.getName()) {
                    case "bifro_task_metric_connect_success_count" ->
                        stats.setTotalConnectSuccess((long) counter.getCount());
                    case "bifro_task_metric_connect_exception_count" ->
                        stats.setTotalConnectException((long) counter.getCount());
                    case "bifro_task_metric_message_received_count" ->
                        stats.setTotalMessageReceived((long) counter.getCount());
                    case "bifro_task_metric_message_duplicate_count" ->
                        stats.setTotalMessageDuplicate((long) counter.getCount());
                    case "bifro_task_metric_publish_completion_count" ->
                        stats.setTotalPublishCompletion((long) counter.getCount());
                    case "bifro_task_metric_reconnect_count" -> stats.setTotalReconnect((long) counter.getCount());
                    case "bifro_task_metric_client_created_count" ->
                        stats.setTotalClientCreated((long) counter.getCount());
                    case "bifro_task_metric_client_failure_count" ->
                        stats.setTotalClientFailure((long) counter.getCount());
                    default -> { /* ignore unmapped metric */ }
                }
            }
        }

        if (snapshot.getTimerMetrics() != null) {
            for (TimerMetricData timer : snapshot.getTimerMetrics()) {
                if (!timer.isHasData()) {
                    continue;
                }
                switch (timer.getName()) {
                    case "bifro_task_metric_connect_latency" -> {
                        stats.setConnectLatencyP50(timer.getP50());
                        stats.setAvgConnectLatencyP95(timer.getP95());
                        stats.setConnectLatencyP99(timer.getP99());
                        stats.setConnectLatencyMax(timer.getMax());
                    }
                    case "bifro_task_metric_end_to_end_latency" -> {
                        stats.setEndToEndLatencyP50(timer.getP50());
                        stats.setEndToEndLatencyP95(timer.getP95());
                        stats.setEndToEndLatencyP99(timer.getP99());
                    }
                    case "bifro_task_metric_puback_latency" -> stats.setPubackLatencyP95(timer.getP95());
                    default -> { /* ignore unmapped metric */ }
                }
            }
        }
    }

    private Mono<Boolean> validateBrokersInSameGroup(List<String> brokerIds) {
        if (brokerIds == null || brokerIds.isEmpty()) {
            return Mono.just(true);
        }
        return mqttBrokerRepository.findAllById(brokerIds)
            .collectList()
            .map(brokers -> {
                String commonGroup = null;
                boolean hasGroup = false;
                for (MqttBroker broker : brokers) {
                    String group = broker.getGroup();
                    if (group != null && !group.isEmpty()) {
                        if (!hasGroup) {
                            commonGroup = group;
                            hasGroup = true;
                        } else if (!group.equals(commonGroup)) {
                            return false;
                        }
                    } else if (hasGroup) {
                        return false;
                    }
                }
                return true;
            });
    }

    private Mono<TaskConfig> inlineProfileDataPoints(TaskConfig taskConfig) {

        Mono<TaskConfig> result;
        if (taskConfig.getQpsMode() == TaskConfig.QpsMode.DYNAMIC) {
            TaskConfig.ProfileConfig profileConfig = taskConfig.getProfileConfig();
            if (profileConfig == null || profileConfig.getProfileId() == null
                || profileConfig.getProfileId().isBlank()) {
                return Mono.error(new ApiException(Messages.get("error.task.brokerListNotEmpty")));
            }
            result = taskProfileService.getTaskProfileById(profileConfig.getProfileId())
                .map(profile -> {
                    profileConfig.setDataPoints(profile.getDataPoints());
                    profileConfig.setTotalDurationMs(profile.getTotalDurationMs());
                    taskConfig.setStressDurationInSec(toDurationSeconds(profile.getTotalDurationMs()));
                    return taskConfig;
                })
                .onErrorMap(ex -> ex instanceof ApiException ? ex
                    : new ApiException(Messages.get("error.profile.notFound", ex.getMessage())));
        } else {
            result = Mono.just(taskConfig);
        }

        if (taskConfig.getConnectProfileId() != null && !taskConfig.getConnectProfileId().isBlank()) {
            result = result.flatMap(cfg -> taskProfileService.getTaskProfileById(cfg.getConnectProfileId())
                .map(profile -> {
                    cfg.setConnectProfileDataPoints(profile.getDataPoints());
                    return cfg;
                })
                .onErrorMap(ex -> ex instanceof ApiException ? ex
                    : new ApiException(Messages.get("error.profile.notFound", ex.getMessage()))));
        }

        if (taskConfig.getDisconnectProfileId() != null && !taskConfig.getDisconnectProfileId().isBlank()) {
            result = result.flatMap(cfg -> taskProfileService.getTaskProfileById(cfg.getDisconnectProfileId())
                .map(profile -> {
                    cfg.setDisconnectProfileDataPoints(profile.getDataPoints());
                    return cfg;
                })
                .onErrorMap(ex -> ex instanceof ApiException ? ex
                    : new ApiException(Messages.get("error.profile.notFound", ex.getMessage()))));
        }

        if (taskConfig.getSubscribeProfileId() != null && !taskConfig.getSubscribeProfileId().isBlank()) {
            result = result.flatMap(cfg -> taskProfileService.getTaskProfileById(cfg.getSubscribeProfileId())
                .map(profile -> {
                    cfg.setSubscribeProfileDataPoints(profile.getDataPoints());
                    return cfg;
                })
                .onErrorMap(ex -> ex instanceof ApiException ? ex
                    : new ApiException(Messages.get("error.profile.notFound", ex.getMessage()))));
        }
        return result;
    }

    private int toDurationSeconds(long totalDurationMs) {
        if (totalDurationMs <= 0) {
            return 1;
        }
        long durationSeconds = (totalDurationMs + 999L) / 1000L;
        return durationSeconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) durationSeconds;
    }

}
