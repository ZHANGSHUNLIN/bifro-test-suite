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

package org.apache.bifromq.testsuite.app.database.service;

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import org.apache.bifromq.testsuite.app.cluster.core.NodeMetricsService;
import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot;
import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot.NodeMetricsSnapshot;
import org.apache.bifromq.testsuite.app.database.repository.TaskMetricsSnapshotRepository;
import org.apache.bifromq.testsuite.metric.CounterMetricData;
import org.apache.bifromq.testsuite.metric.NodeMetricsResponse;
import org.apache.bifromq.testsuite.metric.TimerMetricData;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@ConditionalOnControlPlane
public class TaskMetricsSnapshotService {

    @Resource
    private TaskMetricsSnapshotRepository taskMetricsSnapshotRepository;

    @Resource
    private NodeMetricsService nodeMetricsService;

    public Mono<TaskMetricsSnapshot> collectAndSaveNodeSnapshot(
        String taskId,
        String taskName,
        String taskStage,
        String nodeId,
        String nodeName,
        LocalDateTime startTime,
        LocalDateTime endTime) {
        try {
            NodeMetricsResponse response = nodeMetricsService.queryNodeMetrics(nodeId, taskId, null);
            return saveNodeSnapshot(taskId, taskName, taskStage, nodeId, nodeName, startTime, endTime, response);
        } catch (Exception e) {
            log.warn("Failed to collect node metrics snapshot, taskId={}, nodeId={}, stage={}",
                taskId, nodeId, taskStage, e);
            return Mono.empty();
        }
    }

    public Mono<TaskMetricsSnapshot> saveNodeSnapshot(
        String taskId,
        String taskName,
        String taskStage,
        String nodeId,
        String nodeName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        NodeMetricsResponse response) {

        if (response == null || !response.isSuccess()) {
            String errorCode = response != null ? response.getErrorCode() : "NO_RESPONSE";
            String errorMessage = response != null ? response.getErrorMessage() : "No metrics response";
            log.warn("Skip node metrics snapshot, taskId={}, nodeId={}, stage={}, errorCode={}, errorMessage={}",
                taskId, nodeId, taskStage, errorCode, errorMessage);
            return Mono.empty();
        }
        if (response.getCounterMetrics() == null) {
            log.warn("Skip node metrics snapshot with null counters, taskId={}, nodeId={}, stage={}",
                taskId, nodeId, taskStage);
            return Mono.empty();
        }

        NodeMetricsSnapshot nodeSnapshot = NodeMetricsSnapshot.builder()
            .nodeId(nodeId)
            .nodeName(nodeName != null ? nodeName : nodeId)
            .counterMetrics(response.getCounterMetrics())
            .timerMetrics(response.getTimerMetrics() != null ? response.getTimerMetrics() : List.of())
            .build();

        List<CounterMetricData> counters = new ArrayList<>();
        aggregateCounterMetrics(counters, nodeSnapshot.getCounterMetrics());
        List<TimerMetricData> timers = new ArrayList<>();
        aggregateTimerMetrics(timers, nodeSnapshot.getTimerMetrics());

        Long durationMs = null;
        if (startTime != null && endTime != null) {
            durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }

        TaskMetricsSnapshot snapshot = TaskMetricsSnapshot.builder()
            .taskId(taskId)
            .taskName(taskName)
            .taskWorkStage(taskStage)
            .nodeId(nodeId)
            .nodeName(nodeName)
            .startTime(startTime)
            .endTime(endTime)
            .durationMs(durationMs)
            .counterMetrics(counters)
            .timerMetrics(timers)
            .nodeMetrics(Map.of(nodeId, nodeSnapshot))
            .createTime(LocalDateTime.now())
            .build();

        return taskMetricsSnapshotRepository
            .findFirstByTaskIdAndNodeIdAndTaskWorkStageOrderByCreateTimeDesc(taskId, nodeId, taskStage)
            .flatMap(existing -> {
                snapshot.setId(existing.getId());
                return taskMetricsSnapshotRepository.save(snapshot);
            })
            .switchIfEmpty(taskMetricsSnapshotRepository.save(snapshot))
            .doOnSuccess(s -> log.info("Node metrics snapshot saved, taskId={}, nodeId={}, stage={}",
                taskId, nodeId, taskStage))
            .doOnError(e -> log.error("Failed to save node metrics snapshot, taskId={}, nodeId={}",
                taskId, nodeId, e));
    }
    
    public Mono<TaskMetricsSnapshot> saveSnapshot(
        String taskId,
        String taskName,
        String taskStage,
        LocalDateTime startTime,
        LocalDateTime endTime,
        List<String> nodeIds,
        Map<String, String> nodeIdNameMap) {

        log.info("Saving metrics snapshot for task: {}, stage: {}", taskId, taskStage);

        Map<String, NodeMetricsSnapshot> nodeMetricsMap = new HashMap<>();
        List<CounterMetricData> aggregatedCounters = new ArrayList<>();
        List<TimerMetricData> aggregatedTimers = new ArrayList<>();

        for (String nodeId : nodeIds) {
            try {
                NodeMetricsResponse response = nodeMetricsService.queryNodeMetrics(nodeId, taskId, null);
                if (response.isSuccess() && response.getCounterMetrics() != null) {
                    String nodeName = nodeIdNameMap.getOrDefault(nodeId, nodeId);
                    
                    NodeMetricsSnapshot nodeSnapshot = NodeMetricsSnapshot.builder()
                        .nodeId(nodeId)
                        .nodeName(nodeName)
                        .counterMetrics(response.getCounterMetrics())
                        .timerMetrics(response.getTimerMetrics() != null ? response.getTimerMetrics() : List.of())
                        .build();
                    nodeMetricsMap.put(nodeId, nodeSnapshot);

                    aggregateCounterMetrics(aggregatedCounters, response.getCounterMetrics());

                    if (response.getTimerMetrics() != null) {
                        aggregateTimerMetrics(aggregatedTimers, response.getTimerMetrics());
                    }
                } else {
                    log.warn("Skip metrics snapshot for node, taskId={}, nodeId={}, success={}, errorCode={}, "
                            + "errorMessage={}, counterMetricsNull={}",
                        taskId, nodeId, response.isSuccess(), response.getErrorCode(), response.getErrorMessage(),
                        response.getCounterMetrics() == null);
                }
            } catch (Exception e) {
                log.warn("Failed to collect metrics from node: {}", nodeId, e);
            }
        }

        
        Long durationMs = null;
        if (startTime != null && endTime != null) {
            durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }

        TaskMetricsSnapshot snapshot = TaskMetricsSnapshot.builder()
            .taskId(taskId)
            .taskName(taskName)
            .taskWorkStage(taskStage)
            .startTime(startTime)
            .endTime(endTime)
            .durationMs(durationMs)
            .counterMetrics(aggregatedCounters)
            .timerMetrics(aggregatedTimers)
            .nodeMetrics(nodeMetricsMap)
            .createTime(LocalDateTime.now())
            .build();

        return taskMetricsSnapshotRepository.save(snapshot)
            .doOnSuccess(s -> log.info("Metrics snapshot saved successfully for task: {}", taskId))
            .doOnError(e -> log.error("Failed to save metrics snapshot for task: {}", taskId, e));
    }

    
    public Mono<TaskMetricsSnapshot> findByTaskId(String taskId) {
        return findMergedByTaskId(taskId);
    }

    public Mono<TaskMetricsSnapshot> findMergedByTaskId(String taskId) {
        return taskMetricsSnapshotRepository.findAllByTaskIdOrderByCreateTimeDesc(taskId)
            .collectList()
            .flatMap(snapshots -> {
                if (snapshots.isEmpty()) {
                    return Mono.empty();
                }
                return Mono.just(mergeSnapshots(snapshots));
            });
    }

    public Flux<TaskMetricsSnapshot> findSnapshotsByTaskId(String taskId) {
        return taskMetricsSnapshotRepository.findAllByTaskIdOrderByCreateTimeDesc(taskId);
    }

    
    public Mono<Void> deleteByTaskId(String taskId) {
        return taskMetricsSnapshotRepository.deleteByTaskId(taskId);
    }

    
    private void aggregateCounterMetrics(List<CounterMetricData> aggregated, List<CounterMetricData> toAdd) {
        if (toAdd == null) {
            return;
        }

        for (CounterMetricData metric : toAdd) {
            
            CounterMetricData existing = aggregated.stream()
                .filter(m -> m.getName().equals(metric.getName())
                    && java.util.Objects.equals(m.getTags(), metric.getTags()))
                .findFirst()
                .orElse(null);

            if (existing != null) {
                
                existing.setCount(existing.getCount() + metric.getCount());
            } else {
                
                aggregated.add(CounterMetricData.builder()
                    .name(metric.getName())
                    .tags(metric.getTags())
                    .count(metric.getCount())
                    .build());
            }
        }
    }

    private TaskMetricsSnapshot mergeSnapshots(List<TaskMetricsSnapshot> snapshots) {
        Map<String, NodeMetricsSnapshot> latestNodeMetrics = new LinkedHashMap<>();
        TaskMetricsSnapshot latestTaskSnapshot = snapshots.get(0);
        LocalDateTime startTime = latestTaskSnapshot.getStartTime();
        LocalDateTime endTime = latestTaskSnapshot.getEndTime();
        Long durationMs = latestTaskSnapshot.getDurationMs();

        for (TaskMetricsSnapshot snapshot : snapshots) {
            if (snapshot.getStartTime() != null && (startTime == null || snapshot.getStartTime().isBefore(startTime))) {
                startTime = snapshot.getStartTime();
            }
            if (snapshot.getEndTime() != null && (endTime == null || snapshot.getEndTime().isAfter(endTime))) {
                endTime = snapshot.getEndTime();
            }
            if (snapshot.getDurationMs() != null && (durationMs == null || snapshot.getDurationMs() > durationMs)) {
                durationMs = snapshot.getDurationMs();
            }
            if (snapshot.getNodeMetrics() != null) {
                snapshot.getNodeMetrics().forEach((nodeId, nodeSnapshot) ->
                    latestNodeMetrics.putIfAbsent(nodeId, nodeSnapshot));
            } else if (snapshot.getNodeId() != null) {
                latestNodeMetrics.putIfAbsent(snapshot.getNodeId(), NodeMetricsSnapshot.builder()
                    .nodeId(snapshot.getNodeId())
                    .nodeName(snapshot.getNodeName())
                    .counterMetrics(snapshot.getCounterMetrics())
                    .timerMetrics(snapshot.getTimerMetrics())
                    .build());
            }
        }

        List<CounterMetricData> aggregatedCounters = new ArrayList<>();
        List<TimerMetricData> aggregatedTimers = new ArrayList<>();
        latestNodeMetrics.values().stream()
            .filter(Objects::nonNull)
            .forEach(nodeSnapshot -> {
                aggregateCounterMetrics(aggregatedCounters, nodeSnapshot.getCounterMetrics());
                aggregateTimerMetrics(aggregatedTimers, nodeSnapshot.getTimerMetrics());
            });

        return TaskMetricsSnapshot.builder()
            .id(latestTaskSnapshot.getId())
            .taskId(latestTaskSnapshot.getTaskId())
            .taskName(latestTaskSnapshot.getTaskName())
            .taskWorkStage(latestTaskSnapshot.getTaskWorkStage())
            .startTime(startTime)
            .endTime(endTime)
            .durationMs(durationMs)
            .counterMetrics(aggregatedCounters)
            .timerMetrics(aggregatedTimers)
            .nodeMetrics(latestNodeMetrics)
            .createTime(latestTaskSnapshot.getCreateTime())
            .build();
    }
    
    private void aggregateTimerMetrics(List<TimerMetricData> aggregated, List<TimerMetricData> toAdd) {
        if (toAdd == null) {
            return;
        }

        for (TimerMetricData metric : toAdd) {
            if (!metric.isHasData()) {
                continue;
            }

            TimerMetricData existing = aggregated.stream()
                .filter(m -> m.getName().equals(metric.getName()))
                .findFirst()
                .orElse(null);

            if (existing != null) {
                
                existing.setCount(existing.getCount() + metric.getCount());
                if (metric.getP95() > existing.getP95()) {
                    existing.setP95(metric.getP95());
                }
                if (metric.getP99() > existing.getP99()) {
                    existing.setP99(metric.getP99());
                }
                if (metric.getMax() > existing.getMax()) {
                    existing.setMax(metric.getMax());
                }
                
                double totalWeight = existing.getCount() + metric.getCount();
                double weightedMean = (existing.getMean() * existing.getCount()
                    + metric.getMean() * metric.getCount()) / totalWeight;
                existing.setMean(weightedMean);
            } else {
                aggregated.add(TimerMetricData.builder()
                    .name(metric.getName())
                    .tags(metric.getTags())
                    .count(metric.getCount())
                    .mean(metric.getMean())
                    .p50(metric.getP50())
                    .p95(metric.getP95())
                    .p99(metric.getP99())
                    .max(metric.getMax())
                    .totalTime(metric.getTotalTime())
                    .hasData(true)
                    .build());
            }
        }
    }
}
