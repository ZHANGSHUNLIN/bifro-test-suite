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

import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.repository.NodeTaskRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.app.task.runtime.TaskRuntimeStates;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnControlPlane
public class NodeTimeoutTaskReconciler {

    private final Set<String> handledTimeoutNodeIds = ConcurrentHashMap.newKeySet();

    @Resource
    private NodeTaskRepository nodeTaskRepository;
    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    public void handleNodeTimeout(String nodeId) {
        if (!handledTimeoutNodeIds.add(nodeId)) {
            log.debug("Skip duplicate node timeout handling: nodeId={}", nodeId);
            return;
        }
        log.warn("Handling node timeout on control plane: nodeId={}", nodeId);
        markNodeTasksFailed(nodeId)
            .flatMapMany(Flux::fromIterable)
            .flatMap(this::checkAndUpdateMainTaskToFailed)
            .then()
            .subscribe(
                v -> log.debug("Node timeout task reconciliation completed: nodeId={}", nodeId),
                e -> log.error("Failed to reconcile tasks for timeout node: nodeId={}", nodeId, e)
            );
    }

    private Mono<Set<String>> markNodeTasksFailed(String nodeId) {
        return nodeTaskRepository.findAllByNodeId(nodeId)
            .collectList()
            .flatMap(nodeTasks -> {
                if (nodeTasks == null || nodeTasks.isEmpty()) {
                    return Mono.just(Set.of());
                }
                log.info("Found {} NodeTasks for timeout node: nodeId={}", nodeTasks.size(), nodeId);
                Set<String> affectedTaskIds = new HashSet<>();
                Instant now = Instant.now();
                return Flux.fromIterable(nodeTasks)
                    .filter(nodeTask -> !TaskRuntimeStates.isTerminal(TaskRuntimeStates.nodeStage(nodeTask)))
                    .flatMap(nodeTask -> {
                        affectedTaskIds.add(nodeTask.getTaskId());
                        TaskRuntimeStates.applyNodeStage(nodeTask, TaskStage.FAILED, now);
                        return nodeTaskRepository.save(nodeTask)
                            .doOnSuccess(v -> log.info(
                                "NodeTask marked as FAILED due to timeout: taskId={}, nodeId={}",
                                nodeTask.getTaskId(), nodeId));
                    })
                    .then(Mono.just(affectedTaskIds));
            });
    }

    private Mono<Void> checkAndUpdateMainTaskToFailed(String taskId) {
        return nodeTaskRepository.findAllByTaskId(taskId)
            .collectList()
            .flatMap(nodeTasks -> {
                if (nodeTasks == null || nodeTasks.isEmpty()) {
                    return Mono.empty();
                }
                boolean allTerminal = nodeTasks.stream()
                    .map(TaskRuntimeStates::nodeStage)
                    .allMatch(TaskRuntimeStates::isTerminal);
                if (!allTerminal) {
                    return Mono.empty();
                }
                return taskInfoMetadataRepository.findById(taskId)
                    .flatMap(taskInfoMetadata -> {
                        TaskStage currentStage = TaskRuntimeStates.mainStage(taskInfoMetadata);
                        if (TaskRuntimeStates.isTerminal(currentStage)) {
                            return Mono.empty();
                        }
                        return taskInfoMetadataRepository.updateStageById(
                                taskId, TaskStage.FAILED.name(), Instant.now())
                            .doOnSuccess(v -> log.info(
                                "Main task marked as FAILED after node timeout: taskId={}", taskId));
                    });
            })
            .then();
    }
}
