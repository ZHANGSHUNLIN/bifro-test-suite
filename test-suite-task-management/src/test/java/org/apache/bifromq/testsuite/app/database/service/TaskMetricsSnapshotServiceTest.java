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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot;
import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot.NodeMetricsSnapshot;
import org.apache.bifromq.testsuite.app.database.repository.TaskMetricsSnapshotRepository;
import org.apache.bifromq.testsuite.metric.CounterMetricData;
import org.apache.bifromq.testsuite.metric.TimerMetricData;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class TaskMetricsSnapshotServiceTest {

    @Mock
    private TaskMetricsSnapshotRepository taskMetricsSnapshotRepository;

    @InjectMocks
    private TaskMetricsSnapshotService taskMetricsSnapshotService;

    @Test
    void findMergedByTaskId_shouldMergeLatestNodeSnapshots() {
        TaskMetricsSnapshot oldNode1 = nodeSnapshot("node-1", "node-1", 10, 5, 10);
        oldNode1.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        TaskMetricsSnapshot latestNode1 = nodeSnapshot("node-1", "node-1", 20, 7, 20);
        latestNode1.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 2));
        TaskMetricsSnapshot node2 = nodeSnapshot("node-2", "node-2", 30, 11, 30);
        node2.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 1));

        when(taskMetricsSnapshotRepository.findAllByTaskIdOrderByCreateTimeDesc("task-1"))
            .thenReturn(Flux.just(latestNode1, node2, oldNode1));

        TaskMetricsSnapshot merged = taskMetricsSnapshotService.findMergedByTaskId("task-1").block();

        assertThat(merged).isNotNull();
        assertThat(merged.getNodeMetrics()).containsOnlyKeys("node-1", "node-2");
        assertThat(counterValue(merged, "publish")).isEqualTo(50);
        assertThat(counterValue(merged, "received")).isEqualTo(18);
        assertThat(merged.getTimerMetrics()).singleElement()
            .satisfies(timer -> {
                assertThat(timer.getCount()).isEqualTo(50);
                assertThat(timer.getP95()).isEqualTo(30);
            });
    }

    private static TaskMetricsSnapshot nodeSnapshot(String nodeId, String nodeName, double publish, double received,
                                                    double p95) {
        NodeMetricsSnapshot nodeMetrics = NodeMetricsSnapshot.builder()
            .nodeId(nodeId)
            .nodeName(nodeName)
            .counterMetrics(List.of(counter("publish", publish), counter("received", received)))
            .timerMetrics(List.of(timer("latency", publish, p95)))
            .build();
        return TaskMetricsSnapshot.builder()
            .taskId("task-1")
            .taskName("task")
            .taskWorkStage("SHUTDOWN")
            .nodeId(nodeId)
            .nodeName(nodeName)
            .counterMetrics(nodeMetrics.getCounterMetrics())
            .timerMetrics(nodeMetrics.getTimerMetrics())
            .nodeMetrics(Map.of(nodeId, nodeMetrics))
            .durationMs(1000L)
            .build();
    }

    private static CounterMetricData counter(String name, double count) {
        return CounterMetricData.builder()
            .name(name)
            .tags(Map.of())
            .count(count)
            .build();
    }

    private static TimerMetricData timer(String name, double count, double p95) {
        return TimerMetricData.builder()
            .name(name)
            .tags(Map.of())
            .count((long) count)
            .mean(p95)
            .p50(p95)
            .p95(p95)
            .p99(p95)
            .max(p95)
            .totalTime(count * p95)
            .hasData(true)
            .build();
    }

    private static double counterValue(TaskMetricsSnapshot snapshot, String name) {
        return snapshot.getCounterMetrics().stream()
            .filter(counter -> name.equals(counter.getName()))
            .findFirst()
            .orElseThrow()
            .getCount();
    }
}
