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
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.TaskSchedule;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.app.cluster.core.ClusterDataManager;
import org.apache.bifromq.testsuite.app.config.LocalPortModeProperties;
import org.apache.bifromq.testsuite.app.shutdown.GracefulShutdownProperties;
import org.apache.bifromq.testsuite.config.role.ConditionalOnWorkerPlane;
import org.apache.bifromq.testsuite.client.LocalAddressProvider;
import org.apache.bifromq.testsuite.client.LocalPortAllocator;
import org.apache.bifromq.testsuite.client.LocalPortCapacity;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.client.LocalPortUsage;
import org.apache.bifromq.testsuite.eventbus.EventBusAddresses;
import org.apache.bifromq.testsuite.metric.NodeMetricsRequest;
import org.apache.bifromq.testsuite.metric.NodeMetricsResponse;
import org.apache.bifromq.testsuite.scheduler.DelayedTaskScheduler;
import org.apache.bifromq.testsuite.scheduler.ScheduledTaskEventBusRegistrar;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.TaskWorker;
import org.apache.bifromq.testsuite.worker.TaskWorkerRuntime;
import org.apache.bifromq.testsuite.worker.WorkerTaskCommand;
import org.apache.bifromq.testsuite.worker.command.WorkerCommand;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandAck;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandAckStatus;
import org.apache.bifromq.testsuite.worker.command.WorkerCommandType;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryRequest;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryResponse;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckRequest;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckResponse;
import org.apache.bifromq.testsuite.worker.pojo.TaskMetricsCleanupRequest;
import org.apache.bifromq.testsuite.worker.pojo.TaskMetricsCleanupResponse;
import org.apache.bifromq.testsuite.worker.pojo.TaskStopContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnWorkerPlane
public class LocalTaskCoordinator {

    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();

    @Getter
    private final Map<String, TaskWorker> runningTaskMap = Maps.newConcurrentMap();

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

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
    @Resource
    private Vertx vertx;
    @Resource
    private ClusterDataManager clusterDataManager;
    @Resource
    private LocalPortModeProperties localPortModeProperties;
    @Resource
    private GracefulShutdownProperties gracefulShutdownProperties;
    @Resource
    private DelayedTaskScheduler delayedTaskScheduler;
    @Resource
    private TaskWorkerRuntime taskWorkerRuntime;

    public void startTask(String id) {
        log.warn("Ignore legacy task start by id on worker node: taskId={}", id);
    }

    public void startTask(WorkerTaskCommand command) {
        String id = command == null ? null : command.taskId();
        if (id == null || id.isBlank()) {
            log.warn("Ignore worker command without taskId");
            return;
        }
        if (shouldRejectStartWhenShuttingDown()) {
            log.warn("Reject local task start because node is shutting down, taskId={}", id);
            return;
        }
        if (runningTasks.contains(id)) {
            log.warn("Task {} is running", id);
            return;
        }
        if (localPortModeProperties.isEnabled() && hasOtherRunningTask(id)) {
            log.warn("Local port mode rejected task start, taskId={}, runningTasks={}", id, runningTasks);
            return;
        }
        if (!runningTasks.add(id)) {
            return;
        }
        try {
            applyLocalPortModeConfig(command);
        } catch (RuntimeException e) {
            log.error("Task failed before local execution, taskId={}, nodeId={}, reason={}",
                id, command.nodeId(), e.getMessage(), e);
            runningTasks.remove(id);
            return;
        }
        log.info("Start local task: taskId={}, nodeId={}, taskType={}, template={}, clients={}",
            command.taskId(), command.nodeId(), command.taskType(), command.template(), command.totalClientCount());
        executeTask(command, id)
            .subscribe(
                ignored -> log.info("Task started: {}", id),
                e -> {
                    log.error("Failed to start task: {}", id, e);
                    runningTasks.remove(id);
                }
            );
    }

    private Mono<Void> executeTask(WorkerTaskCommand command, String id) {
        return Mono.defer(() -> {
            switch (command.taskType()) {
                case CONN, PUBSUB, CHAOS:
                    TaskWorker connWorker = taskWorkerRuntime.create(vertx, command);
                    runningTaskMap.put(id, connWorker);
                    connWorker.terminalFuture()
                        .whenComplete((stage, error) -> removeLocalRunningTask(id));
                    connWorker.startTask();
                    break;
                default:
                    break;
            }
            return Mono.empty();
        });
    }

    private boolean hasOtherRunningTask(String taskId) {
        return runningTasks.stream().anyMatch(runningTaskId -> !runningTaskId.equals(taskId));
    }

    private void applyLocalPortModeConfig(WorkerTaskCommand command) {
        LocalPortRangeConfig config = mergeLocalPortModeConfig(null, command);
        if (command.workerTaskSpec() != null) {
            command.workerTaskSpec().setLocalPortRangeConfig(config);
        }
        if (!config.isEnabled()) {
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

    @PostConstruct
    public void registerGlobalTaskScheduler() {
        vertx.eventBus().<TaskSchedule>consumer(EventBusAddresses.CLUSTER_TASK_COMMAND,
            message -> handleTaskCommand(message.body()));
        String commandAddr = EventBusAddresses.workerCommand(clusterDataManager.getCurrentNodeIdCache());
        vertx.eventBus().<WorkerCommand>consumer(commandAddr, this::handleWorkerCommandMessage);
        String metricsAddr = EventBusAddresses.nodeMetrics(clusterDataManager.getCurrentNodeIdCache());
        vertx.eventBus().<NodeMetricsRequest>consumer(metricsAddr, message -> {
            NodeMetricsResponse response = taskWorkerRuntime.queryMetrics(message.body());
            message.reply(response);
        });
        String metricsCleanupAddr = EventBusAddresses.taskMetricsCleanup(clusterDataManager.getCurrentNodeIdCache());
        vertx.eventBus().<TaskMetricsCleanupRequest>consumer(metricsCleanupAddr, this::handleTaskMetricsCleanupRequest);
        new ScheduledTaskEventBusRegistrar(
            vertx,
            delayedTaskScheduler,
            clusterDataManager.getCurrentNodeIdCache()
        ).register();
        String clientsAddr = EventBusAddresses.nodeClients(clusterDataManager.getCurrentNodeIdCache());
        vertx.eventBus().<ClientQueryRequest>consumer(clientsAddr, message -> {
            ClientQueryRequest request = message.body();
            TaskWorker taskWorker = runningTaskMap.get(request.getTaskId());
            ClientQueryResponse response = taskWorkerRuntime.queryClients(request, taskWorker);
            message.reply(response);
        });

        String localPortCapacityAddr = EventBusAddresses.localPortCapacity(clusterDataManager.getCurrentNodeIdCache());
        vertx.eventBus().<LocalPortCapacityCheckRequest>consumer(localPortCapacityAddr,
            this::handleLocalPortCapacityRequest);
        clusterDataManager.regClusterNodeInfoDirect();

    }

    void handleWorkerCommand(WorkerTaskCommand command) {
        if (command == null || command.workerTaskSpec() == null) {
            log.warn("Ignore empty worker command");
            return;
        }
        String localNodeId = clusterDataManager.getCurrentNodeIdCache();
        if (command.nodeId() != null && !command.nodeId().equals(localNodeId)) {
            log.debug("Ignore worker command for another node: taskId={}, commandNodeId={}, localNodeId={}",
                command.taskId(), command.nodeId(), localNodeId);
            return;
        }
        runWorkerHandler("worker start command", () -> startTask(command));
    }

    private void handleWorkerCommandMessage(Message<WorkerCommand> message) {
        WorkerCommandAck ack = handleWorkerCommand(message.body());
        message.reply(ack);
    }

    WorkerCommandAck handleWorkerCommand(WorkerCommand command) {
        WorkerCommandAck ack = validateWorkerCommand(command);
        if (!ack.accepted() || ack.getStatus() == WorkerCommandAckStatus.IGNORED_DUPLICATE) {
            return ack;
        }
        if (command.getMessageId() != null && !command.getMessageId().isBlank()) {
            processedMessageIds.add(command.getMessageId());
        }
        if (command.getType() == WorkerCommandType.START_TASK) {
            runWorkerHandler("worker start command", () -> startTask(command.getStartCommand()));
        } else if (command.getType() == WorkerCommandType.STOP_TASK) {
            runWorkerHandler("worker stop command", () -> stopTask(command.getTaskId(), command.getStopContext()));
        }
        return ack;
    }

    private WorkerCommandAck validateWorkerCommand(WorkerCommand command) {
        String localNodeId = clusterDataManager.getCurrentNodeIdCache();
        if (command == null) {
            return workerCommandAck(null, null, localNodeId, null,
                WorkerCommandAckStatus.REJECTED_INVALID_PAYLOAD, "Worker command is empty");
        }
        if (command.getMessageId() != null && !command.getMessageId().isBlank()
            && processedMessageIds.contains(command.getMessageId())) {
            return workerCommandAck(command, WorkerCommandAckStatus.IGNORED_DUPLICATE, "Duplicate worker command");
        }
        if (command.getTaskId() == null || command.getTaskId().isBlank()) {
            return workerCommandAck(command, WorkerCommandAckStatus.REJECTED_INVALID_PAYLOAD, "Task id is empty");
        }
        if (command.getNodeId() != null && !command.getNodeId().equals(localNodeId)) {
            return workerCommandAck(command, WorkerCommandAckStatus.REJECTED_INVALID_PAYLOAD,
                "Command node does not match local node: " + localNodeId);
        }
        if (command.getType() == WorkerCommandType.START_TASK) {
            if (shouldRejectStartWhenShuttingDown()) {
                return workerCommandAck(command, WorkerCommandAckStatus.REJECTED_INVALID_STATE,
                    "Node is shutting down");
            }
            WorkerTaskCommand startCommand = command.getStartCommand();
            if (startCommand == null || startCommand.workerTaskSpec() == null) {
                return workerCommandAck(command, WorkerCommandAckStatus.REJECTED_INVALID_PAYLOAD,
                    "Start command payload is missing");
            }
            if (runningTasks.contains(command.getTaskId())) {
                return workerCommandAck(command, WorkerCommandAckStatus.REJECTED_INVALID_STATE,
                    "Task is already running");
            }
            if (localPortModeProperties.isEnabled() && hasOtherRunningTask(command.getTaskId())) {
                return workerCommandAck(command, WorkerCommandAckStatus.REJECTED_INVALID_STATE,
                    "Local port mode allows only one running task per node");
            }
            return workerCommandAck(command, WorkerCommandAckStatus.ACCEPTED, null);
        }
        if (command.getType() == WorkerCommandType.STOP_TASK) {
            return workerCommandAck(command, WorkerCommandAckStatus.ACCEPTED, null);
        }
        return workerCommandAck(command, WorkerCommandAckStatus.REJECTED_INVALID_PAYLOAD, "Unknown worker command type");
    }

    private WorkerCommandAck workerCommandAck(WorkerCommand command, WorkerCommandAckStatus status, String reason) {
        String localNodeId = clusterDataManager.getCurrentNodeIdCache();
        return workerCommandAck(command == null ? null : command.getMessageId(),
            command == null ? null : command.getTaskId(),
            command == null ? localNodeId : command.getNodeId(),
            command == null ? null : command.getType(),
            status,
            reason);
    }

    private WorkerCommandAck workerCommandAck(String messageId, String taskId, String nodeId, WorkerCommandType type,
                                             WorkerCommandAckStatus status, String reason) {
        return WorkerCommandAck.builder()
            .messageId(messageId)
            .taskId(taskId)
            .nodeId(nodeId)
            .type(type)
            .status(status)
            .reason(reason)
            .ackAtMs(System.currentTimeMillis())
            .build();
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
                log.warn("Ignore legacy task start command without worker payload: taskId={}", id);
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
        if (finishedNodeId.equals(localNodeId)) {
            removeLocalRunningTask(taskId);
        }
        log.info("Handled local TASK_FINISH cleanup: taskId={}, nodeId={}", taskId, finishedNodeId);
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

    private void handleTaskMetricsCleanupRequest(Message<TaskMetricsCleanupRequest> message) {
        vertx.executeBlocking(() -> taskWorkerRuntime.cleanupMetrics(message.body()))
            .onSuccess(message::reply)
            .onFailure(e -> {
                log.warn("Failed to cleanup task metrics", e);
                TaskMetricsCleanupRequest request = message.body();
                message.reply(TaskMetricsCleanupResponse.builder()
                    .success(false)
                    .taskId(request == null ? null : request.getTaskId())
                    .nodeId(request == null ? null : request.getNodeId())
                    .errorMessage("Failed to cleanup task metrics: " + e.getMessage())
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

    public Map<String, TaskStage> runningTask() {
        return taskWorkerRuntime.runningTaskStages(runningTaskMap);
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    public void markShuttingDown() {
        if (shuttingDown.compareAndSet(false, true)) {
            log.info("Local task coordinator entered shutdown mode");
        }
    }

    private boolean shouldRejectStartWhenShuttingDown() {
        return shuttingDown.get()
            && (gracefulShutdownProperties == null || gracefulShutdownProperties.isRejectStartWhenShuttingDown());
    }

    void removeLocalRunningTask(String taskId) {
        runningTaskMap.remove(taskId);
        runningTasks.remove(taskId);
    }

    public void stopTask(String taskId) {
        stopTask(taskId, TaskStopContext.userStop());
    }

    public CompletableFuture<Void> stopTask(String taskId, TaskStopContext context) {

        TaskWorker taskWorker = runningTaskMap.get(taskId);
        if (taskWorker != null) {
            log.info("cluster task STOP , {}", taskId);
            return taskWorker.stopTask(context == null ? TaskStopContext.userStop() : context.normalized())
                .whenComplete((r, error) -> {
                    if (error != null) {
                        log.warn("Failed to stop local task: taskId={}", taskId, error);
                    }
                    log.info("taskId stopped: {}", taskId);
                    removeLocalRunningTask(taskId);
                });
        } else {
            log.info("No local worker found for stop command: taskId={}", taskId);
            removeLocalRunningTask(taskId);
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<Void> stopAllRunningTasks(TaskStopContext context) {
        return stopAllRunningTasks(context, null);
    }

    public CompletableFuture<Void> stopAllRunningTasks(TaskStopContext context, Duration perTaskTimeout) {
        markShuttingDown();
        List<String> taskIds = List.copyOf(runningTaskMap.keySet());
        if (taskIds.isEmpty()) {
            log.info("No local running tasks to stop during node shutdown");
            return CompletableFuture.completedFuture(null);
        }
        log.info("Stopping {} local running tasks during node shutdown", taskIds.size());
        List<CompletableFuture<Void>> futures = taskIds.stream()
            .map(taskId -> withTaskTimeout(taskId, stopTask(taskId, context), perTaskTimeout))
            .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> withTaskTimeout(String taskId, CompletableFuture<Void> future, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return future;
        }
        return future.whenComplete((ignored, error) -> {
            if (error == null) {
                return;
            }
            log.warn("Local task stop failed during node shutdown: taskId={}", taskId, error);
        }).completeOnTimeout(null, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

}
