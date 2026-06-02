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

import com.mongodb.DuplicateKeyException;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.pojo.TaskStateHistory;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskStateHistoryRepository;
import org.apache.bifromq.testsuite.app.database.service.TaskMetricsSnapshotService;
import org.apache.bifromq.testsuite.app.task.runtime.TaskRuntimeStates;
import org.apache.bifromq.testsuite.eventbus.EventBusAddresses;
import org.apache.bifromq.testsuite.worker.pojo.TaskStateChangeEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnControlPlane
public class TaskStateEventHandler {

    private final ConcurrentHashMap<String, Object> taskLocks = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> lastProcessedEventSeq = new ConcurrentHashMap<>();
    @Resource
    private Vertx vertx;
    @Resource
    private TaskStateHistoryRepository taskStateHistoryRepository;
    @Resource
    private NodeTaskRepository nodeTaskRepository;
    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;
    @Resource
    private TaskMetricsSnapshotService taskMetricsSnapshotService;

    private static String toExternalStageName(TaskStage stage) {
        if (stage == TaskStage.STARTING) {
            return "START";
        }
        return stage.name();
    }

    @PostConstruct
    public void register() {
        vertx.eventBus().<TaskStateChangeEvent>consumer(
            EventBusAddresses.TASK_STATE_CHANGE,
            message -> handleStateChange(message.body())
        );
        log.info("TaskStateEventHandler registered for {}", EventBusAddresses.TASK_STATE_CHANGE);
    }

    private void handleStateChange(TaskStateChangeEvent event) {
        log.debug("Received state change event: {}", event);
        vertx.executeBlocking(() -> {
            String lockKey = event.getTaskId() + ":" + event.getNodeId();
            Object lock = taskLocks.computeIfAbsent(lockKey, k -> new Object());
            synchronized (lock) {
                try {
                    String eventKey = event.getTaskId() + ":" + event.getNodeId();
                    long eventSeq = event.getEventSeq();
                    Long lastSeq = lastProcessedEventSeq.get(eventKey);
                    if (lastSeq != null && eventSeq <= lastSeq && eventSeq > 0) {
                        log.debug("Duplicate state change event ignored: taskId={}, nodeId={}, eventSeq={}",
                            event.getTaskId(), event.getNodeId(), eventSeq);
                        return null;
                    }
                    lastProcessedEventSeq.put(eventKey, eventSeq);

                    saveStateHistoryBlocking(event);

                    updateNodeTaskStageBlocking(event);

                    saveTerminalMetricsSnapshotBlocking(event);

                    maybeUpdateMainTaskBlocking(event);

                } catch (Exception e) {
                    log.error("Failed to handle state change event: {}", event, e);
                }
            }
            return null;
        });
    }

    private void saveStateHistoryBlocking(TaskStateChangeEvent event) {
        try {
            TaskStateHistory history = TaskStateHistory.builder()
                .taskId(event.getTaskId())
                .nodeId(event.getNodeId())
                .nodeName(event.getNodeName())
                .fromStage(event.getFromStage())
                .toStage(event.getToStage())
                .triggerEvent(event.getTriggerEvent())
                .timestamp(event.getTimestamp())
                .source(event.getNodeId() == null ? "MAIN_TASK" : "SUB_TASK")
                .eventSeq(event.getEventSeq())
                .build();

            taskStateHistoryRepository.save(history).block();
            log.debug("State history saved: {}", history);
        } catch (Exception e) {

            if (isDuplicateKeyException(e)) {
                log.debug("Duplicate state history ignored: taskId={}, nodeId={}, eventSeq={}",
                    event.getTaskId(), event.getNodeId(), event.getEventSeq());
            } else {
                log.error("Failed to save state history", e);
            }
        }
    }

    private boolean isDuplicateKeyException(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof DuplicateKeyException
                || (cause.getClass().getName().contains("DuplicateKey"))
                || (cause.getMessage() != null && cause.getMessage().contains("E11000"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }


    private void updateNodeTaskStageBlocking(TaskStateChangeEvent event) {
        if (event.getNodeId() == null) {
            return;
        }
        try {
            nodeTaskRepository.findByTaskIdAndNodeId(event.getTaskId(), event.getNodeId())
                .flatMap(nodeTask -> {
                    TaskRuntimeStates.applyNodeStage(nodeTask, event.getToStage(), event.getTimestamp());
                    return nodeTaskRepository.save(nodeTask);
                })
                .block();
            log.debug("NodeTask stage updated: taskId={}, nodeId={}, stage={}",
                event.getTaskId(), event.getNodeId(), event.getToStage());
        } catch (Exception e) {
            log.error("Failed to update NodeTask stage", e);
        }
    }

    private void saveTerminalMetricsSnapshotBlocking(TaskStateChangeEvent event) {
        if (event.getNodeId() == null || !TaskRuntimeStates.isTerminal(event.getToStage())) {
            return;
        }
        try {
            NodeTask nodeTask = nodeTaskRepository.findByTaskIdAndNodeId(event.getTaskId(), event.getNodeId()).block();
            if (nodeTask == null) {
                log.warn("Skip terminal metrics snapshot because node task is missing, taskId={}, nodeId={}",
                    event.getTaskId(), event.getNodeId());
                return;
            }
            TaskInfoMetadata metadata = taskInfoMetadataRepository.findById(event.getTaskId()).block();
            String taskName = metadata != null ? metadata.getTaskName() : null;
            String nodeName = event.getNodeName() != null ? event.getNodeName() : nodeTask.getNodeName();
            if (nodeName == null) {
                nodeName = event.getNodeId();
            }
            LocalDateTime startTime = resolveNodeStartTime(event);
            LocalDateTime endTime = toLocalDateTime(event.getTimestamp());

            taskMetricsSnapshotService.collectAndSaveNodeSnapshot(
                event.getTaskId(),
                taskName,
                event.getToStage().name(),
                event.getNodeId(),
                nodeName,
                startTime,
                endTime
            ).block();
        } catch (Exception e) {
            log.warn("Failed to save terminal metrics snapshot, taskId={}, nodeId={}, stage={}",
                event.getTaskId(), event.getNodeId(), event.getToStage(), e);
        }
    }

    private LocalDateTime resolveNodeStartTime(TaskStateChangeEvent event) {
        List<TaskStateHistory> histories = taskStateHistoryRepository
            .findByTaskIdAndNodeIdOrderByTimestampDesc(event.getTaskId(), event.getNodeId())
            .collectList()
            .block();
        if (histories == null || histories.isEmpty()) {
            return toLocalDateTime(event.getTimestamp());
        }
        return histories.stream()
            .map(TaskStateHistory::getTimestamp)
            .filter(java.util.Objects::nonNull)
            .min(Instant::compareTo)
            .map(TaskStateEventHandler::toLocalDateTime)
            .orElseGet(() -> toLocalDateTime(event.getTimestamp()));
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private void maybeUpdateMainTaskBlocking(TaskStateChangeEvent event) {
        String taskId = event.getTaskId();
        try {
            List<NodeTask> nodeTasks = nodeTaskRepository.findAllByTaskId(taskId)
                .collectList()
                .block();

            if (nodeTasks == null || nodeTasks.isEmpty()) {
                return;
            }

            TaskStage aggregatedStage = aggregateStage(nodeTasks);

            TaskInfoMetadata mainTask = taskInfoMetadataRepository.findById(taskId).block();
            if (mainTask == null || TaskRuntimeStates.mainStage(mainTask) == aggregatedStage) {
                return;
            }

            Instant now = Instant.now();
            taskInfoMetadataRepository.updateStageById(taskId, aggregatedStage.name(), now).block();
            log.info("Main task stage updated: {} -> {}", taskId, aggregatedStage);
        } catch (Exception e) {
            log.error("Failed to update main task stage: {}", taskId, e);
        }
    }

    private TaskStage aggregateStage(List<NodeTask> nodeTasks) {
        Set<TaskStage> stages = nodeTasks.stream()
            .map(TaskRuntimeStates::nodeStage)
            .collect(Collectors.toSet());
        if (stages.contains(TaskStage.FAILED)) {
            return TaskStage.FAILED;
        }
        if (stages.contains(TaskStage.TIMEOUT)) {
            return TaskStage.TIMEOUT;
        }
        if (stages.contains(TaskStage.SHUTTING)) {
            return TaskStage.SHUTTING;
        }
        if (stages.contains(TaskStage.ONGOING)) {
            return TaskStage.ONGOING;
        }
        if (stages.size() == 1) {
            TaskStage single = stages.iterator().next();
            if (single == TaskStage.SHUTDOWN || single == TaskStage.STOPPED) {
                return single;
            }
        }

        boolean allTerminal = stages.stream()
            .allMatch(s -> s == TaskStage.SHUTDOWN || s == TaskStage.STOPPED || s == TaskStage.FAILED);
        if (allTerminal) {
            if (stages.contains(TaskStage.FAILED)) {
                return TaskStage.FAILED;
            }
            if (stages.contains(TaskStage.STOPPED)) {
                return TaskStage.STOPPED;
            }
            return TaskStage.SHUTDOWN;
        }
        if (stages.contains(TaskStage.STARTING)) {
            return TaskStage.STARTING;
        }
        if (stages.contains(TaskStage.ASSIGNED)) {
            return TaskStage.ASSIGNED;
        }
        return TaskStage.INIT;
    }
}
