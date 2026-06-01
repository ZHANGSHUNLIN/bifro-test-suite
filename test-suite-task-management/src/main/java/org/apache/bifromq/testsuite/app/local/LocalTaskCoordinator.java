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

package org.apache.bifromq.testsuite.app.local;

import com.google.common.collect.Maps;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.TaskSchedule;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.app.cluster.core.ClusterDataManager;
import org.apache.bifromq.testsuite.app.cluster.shared.HazelcastDataManager;
import org.apache.bifromq.testsuite.app.cluster.shared.ShareDataAddr;
import org.apache.bifromq.testsuite.app.config.LocalPortModeProperties;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.app.database.service.TaskMetricsSnapshotService;
import org.apache.bifromq.testsuite.app.task.runtime.TaskRuntimeStates;
import org.apache.bifromq.testsuite.client.LocalAddressProvider;
import org.apache.bifromq.testsuite.client.LocalPortAllocator;
import org.apache.bifromq.testsuite.client.LocalPortCapacity;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.client.LocalPortUsage;
import org.apache.bifromq.testsuite.eventbus.EventBusAddresses;
import org.apache.bifromq.testsuite.metric.NodeMetricsRequest;
import org.apache.bifromq.testsuite.metric.NodeMetricsResponse;
import org.apache.bifromq.testsuite.worker.BaseTaskWorker;
import org.apache.bifromq.testsuite.worker.ClientQueryService;
import org.apache.bifromq.testsuite.worker.MetricsQueryService;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.TaskWorker;
import org.apache.bifromq.testsuite.worker.TaskWorkerFactory;
import org.apache.bifromq.testsuite.worker.WorkerTaskCommand;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryRequest;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryResponse;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckRequest;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class LocalTaskCoordinator {

    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();

    @Getter
    private final Map<String, TaskWorker> runningTaskMap = Maps.newConcurrentMap();

    private final Set<String> handledTimeoutNodeIds = ConcurrentHashMap.newKeySet();

    private final Set<String> processedMessageIds = java.util.Collections.synchronizedSet(
        new LinkedHashSet<String>() {
            @Override
            public boolean add(String e) {
                if (size() >= 1000) {
                    iterator().remove();
                }
                return super.add(e);
            }
        });
    private final MetricsQueryService metricsQueryService = new MetricsQueryService();
    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;
    @Resource
    private Vertx vertx;
    @Resource
    private ClusterDataManager clusterDataManager;
    @Resource
    private HazelcastDataManager hazelcastDataManager;
    @Resource
    private NodeTaskRepository nodeTaskRepository;
    @Resource
    private TaskMetricsSnapshotService taskMetricsSnapshotService;
    @Resource
    private LocalPortModeProperties localPortModeProperties;
    @Value("${bifro.nodeName}")
    private String nodeName;

    public void startTask(String id) {
        if (runningTasks.contains(id)) {
            log.warn("Task {} is running", id);
            return;
        }
        if (localPortModeProperties.isEnabled() && hasOtherRunningTask(id)) {
            String reason = "Local port mode allows only one running task per node, runningTasks=" + runningTasks;
            log.warn("Local port mode rejected task start, taskId={}, reason={}", id, reason);
            markTaskFailed(id, reason).subscribe(
                v -> {
                },
                e -> log.error("Failed to mark rejected task as failed, taskId={}, reason={}", id, reason, e)
            );
            return;
        }

        if (runningTasks.add(id)) {
            String currentNodeId = clusterDataManager.getCurrentNodeIdCache();

            taskInfoMetadataRepository.findById(id)
                .flatMap(taskInfoMetadata -> {
                    TaskConfig mainTask = taskInfoMetadata.getTaskConfig();
                    String taskId = mainTask.getTaskId();
                    return nodeTaskRepository.findByTaskIdAndNodeId(taskId, currentNodeId)
                        .flatMap(nodeTask -> {
                            try {
                                applyLocalPortModeConfig(nodeTask);
                            } catch (RuntimeException e) {
                                return markTaskFailed(id, nodeTask, e.getMessage())
                                    .then(Mono.empty());
                            }
                            WorkerTaskCommand command = resolveWorkerTaskCommand(nodeTask);
                            if (command == null) {
                                return markTaskFailed(id, nodeTask, "Worker task command is missing")
                                    .then(Mono.empty());
                            }
                            log.info("Start local task: taskId={}, nodeId={}, taskType={}, template={}, clients={}",
                                command.taskId(), command.nodeId(), command.taskType(),
                                command.template(), command.totalClientCount());
                            return executeTask(command, id)
                                .thenReturn(nodeTask);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            log.debug("Task {} has no task", taskId);
                            runningTasks.remove(id);
                            return Mono.empty();
                        }));
                })
                .subscribe(
                    result -> {

                        LocalDateTime startTime = LocalDateTime.now();
                        taskInfoMetadataRepository.updateStartTimeById(id, startTime).subscribe(
                            v -> log.debug("Task start time set: {}", id),
                            e -> log.warn("Failed to set task start time: {}", id, e)
                        );
                        log.info("Task started: {}", id);
                    },
                    e -> {
                        log.error("Failed to start task: {}", id, e);
                        runningTasks.remove(id);
                    }
                );
        }
    }

    private Mono<Void> executeTask(WorkerTaskCommand command, String id) {
        return Mono.defer(() -> {
            switch (command.taskType()) {
                case CONN, PUBSUB, CHAOS:
                    TaskWorker connWorker = TaskWorkerFactory.create(vertx, command.createWorkerPlanSpec());
                    runningTaskMap.put(id, connWorker);
                    if (connWorker instanceof BaseTaskWorker baseWorker) {
                        baseWorker.terminalFuture()
                            .whenComplete((stage, error) -> removeLocalRunningTask(id));
                    }
                    connWorker.startTask();
                    break;
                default:
                    break;
            }
            return Mono.empty();
        });
    }

    private WorkerTaskCommand resolveWorkerTaskCommand(NodeTask nodeTask) {
        if (nodeTask == null) {
            return null;
        }
        WorkerTaskCommand command = nodeTask.getWorkerTaskCommand();
        if (command != null && command.workerTaskSpec() != null) {
            return command;
        }
        return null;
    }

    private boolean hasOtherRunningTask(String taskId) {
        return runningTasks.stream().anyMatch(runningTaskId -> !runningTaskId.equals(taskId));
    }

    private void applyLocalPortModeConfig(NodeTask nodeTask) {
        if (nodeTask == null) {
            return;
        }
        TaskConfig taskConfig = nodeTask.getTaskConfig();
        WorkerTaskCommand command = nodeTask.getWorkerTaskCommand();
        LocalPortRangeConfig config = mergeLocalPortModeConfig(taskConfig, command);
        if (taskConfig != null) {
            taskConfig.setLocalPortRangeConfig(config);
        }
        if (command != null && command.workerTaskSpec() != null) {
            command.workerTaskSpec().setLocalPortRangeConfig(config);
        }
        if (!config.isEnabled()) {
            return;
        }
        if (command == null || command.workerTaskSpec() == null) {
            return;
        }
        if (command.workerTaskSpec().isEnableAutoMultiAddress()) {
            log.info("Local port mode uses auto multi-address discovery, taskId={}", command.taskId());
        } else {
            log.info("Local port mode uses primary local address only, taskId={}", command.taskId());
        }
    }

    private LocalPortRangeConfig mergeLocalPortModeConfig(TaskConfig taskConfig, WorkerTaskCommand command) {
        LocalPortRangeConfig currentConfig = localPortModeProperties.toConfig();
        Set<Integer> excludedPorts = new LinkedHashSet<>(currentConfig.getExcludedPorts());
        addExcludedPorts(excludedPorts, taskConfig == null ? null : taskConfig.getLocalPortRangeConfig());
        if (command != null && command.workerTaskSpec() != null) {
            addExcludedPorts(excludedPorts, command.workerTaskSpec().getLocalPortRangeConfig());
        }
        return LocalPortRangeConfig.builder()
            .enabled(currentConfig.isEnabled())
            .startPort(currentConfig.getStartPort())
            .endPort(currentConfig.getEndPort())
            .excludedPorts(excludedPorts.stream().toList())
            .build()
            .normalized();
    }

    private void addExcludedPorts(Set<Integer> excludedPorts, LocalPortRangeConfig config) {
        if (excludedPorts == null || config == null || config.getExcludedPorts() == null) {
            return;
        }
        excludedPorts.addAll(config.getExcludedPorts());
    }

    private Mono<Void> markTaskFailed(String taskId, NodeTask nodeTask, String reason) {
        log.error("Task failed before local execution, taskId={}, nodeId={}, reason={}",
            taskId, nodeTask == null ? "" : nodeTask.getNodeId(), reason);
        runningTasks.remove(taskId);
        Instant now = Instant.now();
        Mono<Void> mainUpdate = taskInfoMetadataRepository.updateStageById(taskId, TaskStage.FAILED.name(), now);
        if (nodeTask == null) {
            return mainUpdate;
        }
        TaskRuntimeStates.applyNodeStage(nodeTask, TaskStage.FAILED, now);
        return mainUpdate.then(nodeTaskRepository.save(nodeTask)).then();
    }

    private Mono<Void> markTaskFailed(String taskId, String reason) {
        String currentNodeId = clusterDataManager.getCurrentNodeIdCache();
        return nodeTaskRepository.findByTaskIdAndNodeId(taskId, currentNodeId)
            .flatMap(nodeTask -> markTaskFailed(taskId, nodeTask, reason).thenReturn(true))
            .switchIfEmpty(markTaskFailed(taskId, null, reason).thenReturn(true))
            .then();
    }

    @PostConstruct
    public void registerGlobalTaskScheduler() {
        vertx.eventBus().<TaskSchedule>consumer(EventBusAddresses.CLUSTER_TASK_COMMAND,
            message -> handleTaskCommand(message.body()));
        String metricsAddr = EventBusAddresses.nodeMetrics(clusterDataManager.getCurrentNodeIdCache());
        vertx.eventBus().<NodeMetricsRequest>consumer(metricsAddr, message -> {
            NodeMetricsResponse response = metricsQueryService.query(message.body());
            message.reply(response);
        });
        String clientsAddr = EventBusAddresses.nodeClients(clusterDataManager.getCurrentNodeIdCache());
        ClientQueryService clientQueryService = new ClientQueryService();
        vertx.eventBus().<ClientQueryRequest>consumer(clientsAddr, message -> {
            ClientQueryRequest request = message.body();
            TaskWorker taskWorker = runningTaskMap.get(request.getTaskId());
            if (taskWorker instanceof BaseTaskWorker baseWorker) {
                Map<String, MqttClientTask> clientMap =
                    baseWorker.getClientTaskMap(request.getClientType());
                ClientQueryResponse response = clientQueryService.query(request, clientMap);
                message.reply(response);
            } else {
                message.reply(ClientQueryResponse.builder()
                    .success(true)
                    .clients(List.of())
                    .total(0)
                    .page(request.getPage())
                    .size(request.getSize())
                    .totalPages(0)
                    .build());
            }
        });

        String localPortCapacityAddr = EventBusAddresses.localPortCapacity(clusterDataManager.getCurrentNodeIdCache());
        vertx.eventBus().<LocalPortCapacityCheckRequest>consumer(localPortCapacityAddr,
            this::handleLocalPortCapacityRequest);
        clusterDataManager.regClusterNodeInfoDirect(nodeName);

    }

    void handleTaskCommand(TaskSchedule taskSchedule) {
        if (taskSchedule == null) {
            log.warn("Ignore empty task command");
            return;
        }
        String messageId = taskSchedule.getMessageId();
        if (messageId != null && !messageId.isEmpty()) {
            if (processedMessageIds.contains(messageId)) {
                log.debug("Duplicate message ignored: messageId={}", messageId);
                return;
            }
            processedMessageIds.add(messageId);
        }

        TaskSchedule.Op op = taskSchedule.getOp();
        String id = taskSchedule.getId();
        String nodeId = clusterDataManager.getCurrentNodeIdCache();

        switch (op) {
            case REG:
                runWorkerHandler("task start command", () -> startTask(id));
                break;
            case UN_REG:
                runWorkerHandler("task stop command", () -> stopTask(id));
                break;
            case TASK_FINISH:
                handleTaskFinishCommand(id, nodeId, taskSchedule.getSourceNodeId());
                break;
            default:
                log.warn("Unknown operation: {}", op);
                break;
        }
    }

    private void handleTaskFinishCommand(String taskId, String localNodeId, String sourceNodeId) {
        String finishedNodeId = sourceNodeId;
        if (finishedNodeId == null || finishedNodeId.isBlank()) {
            finishedNodeId = localNodeId;
            log.warn("TASK_FINISH without sourceNodeId, fallback to local node: taskId={}, nodeId={}",
                taskId, localNodeId);
        }
        String finalFinishedNodeId = finishedNodeId;
        runWorkerHandler("task finish command", () ->
            nodeTaskRepository.findByTaskIdAndNodeId(taskId, finalFinishedNodeId)
                .subscribe(
                    nodeTask -> {
                        if (finalFinishedNodeId.equals(localNodeId)) {
                            saveLocalNodeMetricsSnapshot(taskId, nodeTask, TaskStage.SHUTDOWN.name());
                        }
                        taskFinish(taskId, finalFinishedNodeId);
                    },
                    e -> log.warn("Failed to handle TASK_FINISH: taskId={}, nodeId={}",
                        taskId, finalFinishedNodeId, e),
                    () -> log.warn("Ignore TASK_FINISH for unknown node: taskId={}, nodeId={}",
                        taskId, finalFinishedNodeId)
                ));
    }

    private void handleLocalPortCapacityRequest(Message<LocalPortCapacityCheckRequest> message) {
        vertx.executeBlocking(() -> checkLocalPortCapacity(message.body()))
            .onSuccess(message::reply)
            .onFailure(e -> {
                log.warn("Failed to check local port capacity", e);
                LocalPortCapacityCheckRequest request = message.body();
                message.reply(LocalPortCapacityCheckResponse.builder()
                    .success(false)
                    .taskId(request == null ? null : request.getTaskId())
                    .nodeId(request == null ? null : request.getNodeId())
                    .errorMessage("Failed to check local port capacity: " + e.getMessage())
                    .build());
            });
    }

    private void runWorkerHandler(String operation, Runnable handler) {
        vertx.executeBlocking(() -> {
            handler.run();
            return null;
        }).onFailure(e -> log.error("Failed to handle {}", operation, e));
    }

    private LocalPortCapacityCheckResponse checkLocalPortCapacity(LocalPortCapacityCheckRequest request) {
        try {
            LocalPortRangeConfig config = LocalPortRangeConfig.builder()
                .enabled(request.isSourcePortPreallocationEnabled())
                .startPort(request.getStartPort())
                .endPort(request.getEndPort())
                .build()
                .normalized();
            List<String> localAddresses = resolveCapacityAddresses(request);
            List<LocalPortCapacityCheckResponse.OccupiedPort> occupiedPorts =
                config.isEnabled() ? occupiedPorts(localAddresses, config) : List.of();
            config.setExcludedPorts(occupiedPorts.stream()
                .map(LocalPortCapacityCheckResponse.OccupiedPort::getPort)
                .distinct()
                .sorted()
                .toList());
            config = config.normalized();
            long capacity = LocalPortCapacity.calculate(
                request.isMultiAddressEnabled(),
                config,
                localAddresses);
            int portCapacityPerAddress = LocalPortCapacity.portCapacity(config);
            int reservedFallbackPortsPerAddress =
                config.isEnabled() ? LocalPortAllocator.fallbackPortCount(config) : 0;
            long missingCount = Math.max(0L, (long) request.getAssignedClients() - capacity);
            boolean success = missingCount == 0;
            String errorMessage = null;
            if (missingCount > 0) {
                errorMessage = buildLocalPortCapacityError(
                    request, capacity, localAddresses.size(), portCapacityPerAddress, reservedFallbackPortsPerAddress,
                    missingCount, config);
            }
            return LocalPortCapacityCheckResponse.builder()
                .success(success)
                .errorMessage(errorMessage)
                .taskId(request.getTaskId())
                .nodeId(request.getNodeId())
                .assignedClients(request.getAssignedClients())
                .capacity(capacity)
                .localAddressCount(localAddresses.size())
                .localAddresses(localAddresses)
                .multiAddressEnabled(request.isMultiAddressEnabled())
                .sourcePortPreallocationEnabled(config.isEnabled())
                .startPort(config.getStartPort())
                .endPort(config.getEndPort())
                .portCapacityPerAddress(portCapacityPerAddress)
                .reservedFallbackPortsPerAddress(reservedFallbackPortsPerAddress)
                .missingCount(missingCount)
                .occupiedPortCount(occupiedPorts.size())
                .occupiedPorts(limitOccupiedPorts(occupiedPorts))
                .excludedPorts(config.getExcludedPorts())
                .build();
        } catch (RuntimeException e) {
            return LocalPortCapacityCheckResponse.builder()
                .success(false)
                .errorMessage("Failed to check local port capacity: " + e.getMessage())
                .taskId(request == null ? null : request.getTaskId())
                .nodeId(request == null ? null : request.getNodeId())
                .build();
        }
    }

    private List<LocalPortCapacityCheckResponse.OccupiedPort> occupiedPorts(
        List<String> localAddresses, LocalPortRangeConfig config) {
        return LocalPortUsage.findOccupied(localAddresses, config.getStartPort(), config.getEndPort()).stream()
            .map(port -> LocalPortCapacityCheckResponse.OccupiedPort.builder()
                .localAddress(port.getLocalAddress())
                .port(port.getPort())
                .state(port.getState())
                .build())
            .toList();
    }

    private List<LocalPortCapacityCheckResponse.OccupiedPort> limitOccupiedPorts(
        List<LocalPortCapacityCheckResponse.OccupiedPort> occupiedPorts) {
        int limit = 50;
        if (occupiedPorts.size() <= limit) {
            return occupiedPorts;
        }
        return occupiedPorts.subList(0, limit);
    }

    private List<String> resolveCapacityAddresses(LocalPortCapacityCheckRequest request) {
        if (!request.isMultiAddressEnabled() || !request.isSourcePortPreallocationEnabled()) {
            return List.of(primaryLocalAddress());
        }
        if (request.getConfiguredLocalAddresses() != null && !request.getConfiguredLocalAddresses().isEmpty()) {
            return List.copyOf(request.getConfiguredLocalAddresses());
        }
        List<String> discovered = LocalAddressProvider.discoverAll();
        return discovered.isEmpty() ? List.of(primaryLocalAddress()) : discovered;
    }

    private String primaryLocalAddress() {
        List<String> discovered = LocalAddressProvider.discoverAll();
        if (discovered.isEmpty()) {
            return "primary";
        }
        return discovered.get(0);
    }

    private String buildLocalPortCapacityError(LocalPortCapacityCheckRequest request, long capacity,
                                               int localAddressCount, int portCapacityPerAddress,
                                               int reservedFallbackPortsPerAddress,
                                               long missingCount, LocalPortRangeConfig config) {
        return "Source port capacity is insufficient: nodeId=" + request.getNodeId()
            + ", assignedClients=" + request.getAssignedClients()
            + ", capacity=" + capacity
            + ", localAddressCount=" + localAddressCount
            + ", multiAddressEnabled=" + request.isMultiAddressEnabled()
            + ", sourcePortPreallocationEnabled=" + config.isEnabled()
            + ", portRange=" + (config.isEnabled() ? config.getStartPort() + "-" + config.getEndPort() : "1-65535")
            + ", portCapacityPerAddress=" + portCapacityPerAddress
            + ", reservedFallbackPortsPerAddress=" + reservedFallbackPortsPerAddress
            + ", missingCount=" + missingCount;
    }

    private String buildLocalPortOccupiedError(LocalPortCapacityCheckRequest request,
                                               List<LocalPortCapacityCheckResponse.OccupiedPort> occupiedPorts,
                                               LocalPortRangeConfig config) {
        String samples = limitOccupiedPorts(occupiedPorts).stream()
            .map(port -> port.getLocalAddress() + ":" + port.getPort() + "(" + port.getState() + ")")
            .collect(Collectors.joining(", "));
        return "Source port range has occupied ports: nodeId=" + request.getNodeId()
            + ", portRange=" + config.getStartPort() + "-" + config.getEndPort()
            + ", occupiedPortCount=" + occupiedPorts.size()
            + ", occupiedPorts=[" + samples + "]";
    }

    private void taskFinish(String taskId, String nodeId) {

        HazelcastDataManager.IMapWrapper<String, Set<String>> map =
            hazelcastDataManager.map(ShareDataAddr.FINISH_NODE_TASKS);
        map.key(taskId)
            .atomicCompute(existingSet -> {
                if (existingSet == null) {
                    HashSet<String> newSet = new HashSet<>();
                    newSet.add(nodeId);
                    return newSet;
                }
                existingSet.add(nodeId);
                return existingSet;
            })
            .thenAccept(result -> {
                runningTaskMap.remove(taskId);
                runningTasks.remove(taskId);
                log.info("task finish CAS, taskId: {}, nodeId: {}, finish set: {}", taskId, nodeId, result);
                checkAllTasksComplete(map, taskId, result);
            });

    }

    private void checkAllTasksComplete(HazelcastDataManager.IMapWrapper<String, Set<String>> map, String taskId,
                                       Set<String> finishNodeIds) {

        nodeTaskRepository.findAllByTaskId(taskId)
            .collectList()
            .flatMap(nodeTasks -> {
                Map<String, TaskConfig> subTasks = nodeTasks.stream()
                    .collect(Collectors.toMap(NodeTask::getNodeId, NodeTask::getTaskConfig));

                Set<String> allNodeTasks = subTasks.keySet();
                if (allNodeTasks.size() == finishNodeIds.size() && finishNodeIds.containsAll(allNodeTasks)) {
                    log.info("All nodes finished close task, taskId={}, nodes={}", taskId, finishNodeIds);
                    map.key(taskId).remove();

                    return reconcileNodeTasksToStage(nodeTasks, TaskStage.SHUTDOWN)
                        .flatMap(reconciledNodeTasks -> {
                            LocalDateTime endTime = LocalDateTime.now();
                            return taskInfoMetadataRepository.updateStageById(
                                    taskId, TaskStage.SHUTDOWN.name(), Instant.now())
                                .then(taskInfoMetadataRepository.updateEndTimeById(taskId, endTime))
                                .doOnSuccess(v -> log.debug("Task stage/endTime set: {}", taskId))
                                .doOnError(e -> log.warn("Failed to set task stage/endTime: {}", taskId, e));
                        });
                } else {
                    log.info(
                        "Task not finished on all nodes, skip close task stage, taskId={}, finishedNodes={}, allNodes={}",
                        taskId, finishNodeIds, allNodeTasks);
                    return Mono.empty();
                }
            })
            .subscribe(
                v -> {
                },
                e -> log.error("Failed to check all tasks complete: {}", taskId, e)
            );
    }

    private Mono<List<NodeTask>> reconcileNodeTasksToStage(List<NodeTask> nodeTasks, TaskStage stage) {
        if (nodeTasks == null || nodeTasks.isEmpty()) {
            return Mono.just(List.of());
        }
        Instant now = Instant.now();
        List<Mono<NodeTask>> saves = nodeTasks.stream()
            .filter(nodeTask -> {
                return TaskRuntimeStates.nodeStage(nodeTask) != stage
                    || nodeTask.getTaskConfig() != null && nodeTask.getTaskConfig().getTaskWorkStage() != stage;
            })
            .map(nodeTask -> {
                TaskRuntimeStates.applyNodeStage(nodeTask, stage, now);
                return nodeTaskRepository.save(nodeTask);
            })
            .collect(Collectors.toList());

        if (saves.isEmpty()) {
            return Mono.just(nodeTasks);
        }
        return Flux.concat(saves).collectList();
    }

    private void saveLocalNodeMetricsSnapshot(String taskId, NodeTask nodeTask, String taskStage) {
        if (nodeTask == null || nodeTask.getNodeId() == null) {
            return;
        }
        String currentNodeId = clusterDataManager.getCurrentNodeIdCache();
        if (!nodeTask.getNodeId().equals(currentNodeId)) {
            return;
        }
        NodeMetricsRequest request = NodeMetricsRequest.builder()
            .nodeId(currentNodeId)
            .taskId(taskId)
            .build();
        NodeMetricsResponse response = metricsQueryService.query(request);

        taskInfoMetadataRepository.findById(taskId)
            .flatMap(metadata -> taskMetricsSnapshotService.saveNodeSnapshot(
                taskId,
                metadata.getTaskName(),
                taskStage,
                currentNodeId,
                nodeTask.getNodeName(),
                metadata.getStartTime(),
                LocalDateTime.now(),
                response))
            .subscribe(
                v -> log.info("Local node metrics snapshot saved, taskId={}, nodeId={}", taskId, currentNodeId),
                e -> log.warn("Failed to save local node metrics snapshot, taskId={}, nodeId={}",
                    taskId, currentNodeId, e)
            );
    }

    public Map<String, TaskStage> runningTask() {
        return runningTaskMap.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().getTaskState()));
    }

    void removeLocalRunningTask(String taskId) {
        runningTaskMap.remove(taskId);
        runningTasks.remove(taskId);
    }

    public void stopTask(String taskId) {

        TaskWorker taskWorker = runningTaskMap.get(taskId);
        if (taskWorker != null) {
            log.info("cluster task STOP , {}", taskId);
            taskWorker.stopTask()
                .thenAccept(r -> {
                    log.info("taskId stopped: {}", taskId);

                    taskInfoMetadataRepository.findById(taskId)
                        .flatMap(metadata -> {

                            LocalDateTime endTime = LocalDateTime.now();
                            return taskInfoMetadataRepository.updateEndTimeById(taskId, endTime)
                                .then(nodeTaskRepository.findByTaskIdAndNodeId(
                                    taskId, clusterDataManager.getCurrentNodeIdCache()))
                                .doOnNext(nodeTask ->
                                    saveLocalNodeMetricsSnapshot(taskId, nodeTask, TaskStage.STOPPED.name()))
                                .then();
                        })
                        .subscribe(
                            v -> {
                                runningTaskMap.remove(taskId);
                                runningTasks.remove(taskId);
                            },
                            e -> log.warn("Failed to handle stopped task: {}", taskId, e)
                        );
                });
        } else {
            handleStopWithoutWorker(taskId);
        }
    }

    private void handleStopWithoutWorker(String taskId) {
        nodeTaskRepository.findAllByTaskId(taskId)
            .collectList()
            .flatMap(nodeTasks -> {
                if (nodeTasks == null || nodeTasks.isEmpty()) {
                    log.warn("No NodeTasks found for stop: taskId={}", taskId);
                    return Mono.empty();
                }

                List<Mono<NodeTask>> saveMonos = new ArrayList<>();
                for (NodeTask nodeTask : nodeTasks) {
                    TaskStage currentStage = TaskRuntimeStates.nodeStage(nodeTask);
                    if (!TaskRuntimeStates.isTerminal(currentStage)) {
                        log.info("Force stopping NodeTask: taskId={}, nodeId={}, stage={}",
                            taskId, nodeTask.getNodeId(), currentStage);
                        TaskRuntimeStates.applyNodeStage(nodeTask, TaskStage.STOPPED, Instant.now());
                        saveMonos.add(nodeTaskRepository.save(nodeTask));
                    }
                }

                if (!saveMonos.isEmpty()) {
                    return Flux.concat(saveMonos)
                        .then(Mono.fromRunnable(() -> checkAndUpdateMainTaskToStopped(taskId)));
                } else {
                    log.info("All NodeTasks already terminal, checking main task state: taskId={}", taskId);
                    return Mono.fromRunnable(() -> checkAndUpdateMainTaskToStopped(taskId));
                }
            })
            .subscribe(
                v -> {
                },
                e -> log.error("Failed to handle stop without worker: taskId={}", taskId, e)
            );
    }

    private void checkAndUpdateMainTaskToStopped(String taskId) {
        nodeTaskRepository.findAllByTaskId(taskId)
            .collectList()
            .flatMap(nodeTasks -> {
                if (nodeTasks == null || nodeTasks.isEmpty()) {
                    return Mono.empty();
                }
                Set<String> onlineNodeIds = clusterDataManager.getCurrentNodeIds();
                List<Mono<NodeTask>> cleanupMonos = new ArrayList<>();
                for (NodeTask nodeTask : nodeTasks) {
                    TaskStage stage = TaskRuntimeStates.nodeStage(nodeTask);
                    if (!onlineNodeIds.contains(nodeTask.getNodeId())
                        && !TaskRuntimeStates.isTerminal(stage)) {
                        log.info("Node {} is offline, marking NodeTask as STOPPED: taskId={}",
                            nodeTask.getNodeId(), taskId);
                        TaskRuntimeStates.applyNodeStage(nodeTask, TaskStage.STOPPED, Instant.now());
                        cleanupMonos.add(nodeTaskRepository.save(nodeTask));
                    }
                }

                Mono<List<NodeTask>> refreshedTasks;
                if (!cleanupMonos.isEmpty()) {
                    refreshedTasks = Flux.concat(cleanupMonos)
                        .then(nodeTaskRepository.findAllByTaskId(taskId).collectList());
                } else {
                    refreshedTasks = Mono.just(nodeTasks);
                }

                return refreshedTasks.flatMap(tasks -> {
                    boolean allTerminal = tasks.stream()
                        .map(TaskRuntimeStates::nodeStage)
                        .allMatch(TaskRuntimeStates::isTerminal);

                    if (allTerminal) {
                        return taskInfoMetadataRepository.findById(taskId)
                            .flatMap(metadata -> {
                                TaskStage stage = TaskRuntimeStates.mainStage(metadata);
                                if (!TaskRuntimeStates.isTerminal(stage)) {
                                    return taskInfoMetadataRepository.updateStageById(taskId,
                                            TaskStage.STOPPED.name(), java.time.Instant.now())
                                        .then(taskInfoMetadataRepository.updateEndTimeById(taskId,
                                            LocalDateTime.now()))
                                        .doOnSuccess(v -> log.info("Main task marked as STOPPED: taskId={}",
                                            taskId));
                                }
                                return Mono.empty();
                            });
                    } else {
                        log.info("Not all sub-tasks are terminal, skip main task STOPPED update: taskId={}",
                            taskId);
                        return Mono.empty();
                    }
                });
            })
            .subscribe(
                v -> {
                },
                e -> log.error("Failed to check and update main task to stopped: taskId={}", taskId, e)
            );
    }

    public void handleNodeTimeout(String nodeId) {
        if (!handledTimeoutNodeIds.add(nodeId)) {
            log.debug("Skip duplicate node timeout handling: nodeId={}", nodeId);
            return;
        }
        log.warn("Handling node timeout: nodeId={}", nodeId);
        nodeTaskRepository.findAllByNodeId(nodeId)
            .collectList()
            .flatMap(nodeTasks -> {
                Set<String> affectedTaskIds = new HashSet<>();

                if (nodeTasks != null && !nodeTasks.isEmpty()) {
                    log.info("Found {} NodeTasks in DB for timeout node: nodeId={}",
                        nodeTasks.size(), nodeId);

                    for (NodeTask nodeTask : nodeTasks) {
                        TaskConfig taskConfig = nodeTask.getTaskConfig();
                        if (taskConfig == null) {
                            continue;
                        }
                        TaskStage currentStage = TaskRuntimeStates.nodeStage(nodeTask);

                        if (!TaskRuntimeStates.isTerminal(currentStage)) {
                            TaskRuntimeStates.applyNodeStage(nodeTask, TaskStage.FAILED, Instant.now());
                            nodeTaskRepository.save(nodeTask)
                                .doOnSuccess(
                                    v -> log.info("NodeTask marked as FAILED due to timeout: taskId={}, nodeId={}",
                                        nodeTask.getTaskId(), nodeId))
                                .subscribe();
                            affectedTaskIds.add(nodeTask.getTaskId());
                        }
                    }
                }

                return Mono.just(affectedTaskIds);
            })
            .flatMap(affectedTaskIds -> {

                List<String> localTaskIdsToRemove = new ArrayList<>();
                runningTaskMap.forEach((taskId, worker) -> {
                    if (worker instanceof BaseTaskWorker baseWorker) {
                        String workerNodeId = baseWorker.getNodeId();
                        if (nodeId.equals(workerNodeId)) {
                            localTaskIdsToRemove.add(taskId);
                        }
                    }
                });

                for (String taskId : localTaskIdsToRemove) {
                    runningTaskMap.remove(taskId);
                    runningTasks.remove(taskId);
                    affectedTaskIds.add(taskId);
                }

                affectedTaskIds.forEach(this::checkAndUpdateMainTaskToFailed);

                return Mono.just(localTaskIdsToRemove);
            })
            .subscribe(
                localTaskIdsToRemove -> {

                    if (!localTaskIdsToRemove.isEmpty()) {
                        log.debug("Cleaned up {} stale local tasks during node timeout handling",
                            localTaskIdsToRemove.size());
                    }
                },
                e -> log.error("Failed to handle node timeout: nodeId={}", nodeId, e)
            );
    }

    private void checkAndUpdateMainTaskToFailed(String taskId) {
        nodeTaskRepository.findAllByTaskId(taskId)
            .collectList()
            .subscribe(nodeTasks -> {
                if (nodeTasks == null || nodeTasks.isEmpty()) {
                    return;
                }
                boolean allTerminal = nodeTasks.stream()
                    .map(TaskRuntimeStates::nodeStage)
                    .allMatch(TaskRuntimeStates::isTerminal);

                if (allTerminal) {

                    taskInfoMetadataRepository.findById(taskId).subscribe(taskInfoMetadata -> {
                        if (taskInfoMetadata != null) {
                            TaskStage currentStage = TaskRuntimeStates.mainStage(taskInfoMetadata);
                            if (!TaskRuntimeStates.isTerminal(currentStage)) {
                                taskInfoMetadataRepository.updateStageById(
                                    taskId, TaskStage.FAILED.name(), Instant.now()).subscribe(
                                    v -> log.info(
                                        "Main task marked as FAILED due to all sub-tasks done/failed: taskId={}",
                                        taskId),
                                    e -> log.warn("Failed to update main task to FAILED: taskId={}", taskId, e)
                                );
                            }
                        }
                    }, e -> log.warn("Failed to find TaskInfoMetadata for FAILED update: taskId={}", taskId, e));
                }
            }, e -> log.warn("Failed to check all tasks for FAILED update: taskId={}", taskId, e));
    }

}
