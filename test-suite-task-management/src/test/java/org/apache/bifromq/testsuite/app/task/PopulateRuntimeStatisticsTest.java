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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.apache.bifromq.testsuite.app.bean.TaskStatistics;
import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot;
import org.apache.bifromq.testsuite.metric.CounterMetricData;
import org.apache.bifromq.testsuite.metric.TimerMetricData;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PopulateRuntimeStatisticsTest {

    private TaskManager taskManager;
    private Method populateMethod;

    private static CounterMetricData counter(String name, int count) {
        CounterMetricData c = new CounterMetricData();
        c.setName(name);
        c.setCount(count);
        return c;
    }

    private static TimerMetricData timer(String name, double p50, double p95, double p99, double max) {
        TimerMetricData t = new TimerMetricData();
        t.setName(name);
        t.setP50(p50);
        t.setP95(p95);
        t.setP99(p99);
        t.setMax(max);
        t.setHasData(true);
        return t;
    }

    

    private static TimerMetricData timerNoData(String name) {
        TimerMetricData t = new TimerMetricData();
        t.setName(name);
        t.setHasData(false);
        return t;
    }

    @BeforeEach
    void setUp() throws Exception {
        
        taskManager = new TaskManager();
        populateMethod = TaskManager.class.getDeclaredMethod(
            "populateRuntimeStatistics", TaskStatistics.class, TaskMetricsSnapshot.class);
        populateMethod.setAccessible(true);
    }

    private void invoke(TaskStatistics stats, TaskMetricsSnapshot snapshot) throws Exception {
        populateMethod.invoke(taskManager, stats, snapshot);
    }

    

    @Test
    void populate_nullStats_doesNotThrow() throws Exception {
        TaskMetricsSnapshot snap = new TaskMetricsSnapshot();
        invoke(null, snap);    
    }

    @Test
    void populate_nullSnapshot_doesNotThrow() throws Exception {
        invoke(new TaskStatistics(), null);
    }

    

    @Test
    void populate_counterMetrics_allMappedFieldsAreFilled() throws Exception {
        TaskStatistics stats = new TaskStatistics();
        TaskMetricsSnapshot snap = new TaskMetricsSnapshot();
        snap.setDurationMs(60000L);
        snap.setCounterMetrics(Arrays.asList(
            counter("bifro_task_metric_connect_success_count", 1000),
            counter("bifro_task_metric_connect_exception_count", 5),
            counter("bifro_task_metric_message_received_count", 2000),
            counter("bifro_task_metric_message_duplicate_count", 3),
            counter("bifro_task_metric_publish_completion_count", 1800),
            counter("bifro_task_metric_reconnect_count", 10),
            counter("bifro_task_metric_client_created_count", 1005),
            counter("bifro_task_metric_client_failure_count", 2)
        ));

        invoke(stats, snap);

        assertThat(stats.getActualDurationMs()).isEqualTo(60000L);
        assertThat(stats.getTotalConnectSuccess()).isEqualTo(1000L);
        assertThat(stats.getTotalConnectException()).isEqualTo(5L);
        assertThat(stats.getTotalMessageReceived()).isEqualTo(2000L);
        assertThat(stats.getTotalMessageDuplicate()).isEqualTo(3L);
        assertThat(stats.getTotalPublishCompletion()).isEqualTo(1800L);
        assertThat(stats.getTotalReconnect()).isEqualTo(10L);
        assertThat(stats.getTotalClientCreated()).isEqualTo(1005L);
        assertThat(stats.getTotalClientFailure()).isEqualTo(2L);
    }

    @Test
    void populate_unknownCounterMetric_isIgnored() throws Exception {
        TaskStatistics stats = new TaskStatistics();
        TaskMetricsSnapshot snap = new TaskMetricsSnapshot();
        snap.setCounterMetrics(List.of(
            counter("bifro_task_metric_unknown_xyz", 999)
        ));

        invoke(stats, snap);

        
        assertThat(stats.getTotalConnectSuccess()).isNull();
    }

    @Test
    void populate_emptyCounterMetrics_allFieldsRemainNull() throws Exception {
        TaskStatistics stats = new TaskStatistics();
        TaskMetricsSnapshot snap = new TaskMetricsSnapshot();
        snap.setCounterMetrics(Collections.emptyList());

        invoke(stats, snap);

        assertThat(stats.getTotalConnectSuccess()).isNull();
        assertThat(stats.getTotalMessageReceived()).isNull();
    }

    

    @Test
    void populate_connectLatency_allPercentilesAreFilled() throws Exception {
        TaskStatistics stats = new TaskStatistics();
        TaskMetricsSnapshot snap = new TaskMetricsSnapshot();
        snap.setTimerMetrics(List.of(
            timer("bifro_task_metric_connect_latency", 50.0, 95.0, 99.0, 200.0)
        ));

        invoke(stats, snap);

        assertThat(stats.getConnectLatencyP50()).isCloseTo(50.0, within(0.001));
        assertThat(stats.getAvgConnectLatencyP95()).isCloseTo(95.0, within(0.001));
        assertThat(stats.getConnectLatencyP99()).isCloseTo(99.0, within(0.001));
        assertThat(stats.getConnectLatencyMax()).isCloseTo(200.0, within(0.001));
    }

    @Test
    void populate_endToEndLatency_filledCorrectly() throws Exception {
        TaskStatistics stats = new TaskStatistics();
        TaskMetricsSnapshot snap = new TaskMetricsSnapshot();
        snap.setTimerMetrics(List.of(
            timer("bifro_task_metric_end_to_end_latency", 100.0, 300.0, 500.0, 1000.0)
        ));

        invoke(stats, snap);

        assertThat(stats.getEndToEndLatencyP50()).isCloseTo(100.0, within(0.001));
        assertThat(stats.getEndToEndLatencyP95()).isCloseTo(300.0, within(0.001));
        assertThat(stats.getEndToEndLatencyP99()).isCloseTo(500.0, within(0.001));
        
    }

    @Test
    void populate_pubackLatency_p95IsFilled() throws Exception {
        TaskStatistics stats = new TaskStatistics();
        TaskMetricsSnapshot snap = new TaskMetricsSnapshot();
        snap.setTimerMetrics(List.of(
            timer("bifro_task_metric_puback_latency", 20.0, 80.0, 120.0, 500.0)
        ));

        invoke(stats, snap);

        assertThat(stats.getPubackLatencyP95()).isCloseTo(80.0, within(0.001));
    }

    @Test
    void populate_timerWithNoData_isSkipped() throws Exception {
        TaskStatistics stats = new TaskStatistics();
        TaskMetricsSnapshot snap = new TaskMetricsSnapshot();
        snap.setTimerMetrics(List.of(
            timerNoData("bifro_task_metric_connect_latency")
        ));

        invoke(stats, snap);

        assertThat(stats.getAvgConnectLatencyP95()).isNull();
    }

    @Test
    void populate_mixedTimers_onlyHasDataTimersAreFilled() throws Exception {
        TaskStatistics stats = new TaskStatistics();
        TaskMetricsSnapshot snap = new TaskMetricsSnapshot();
        snap.setTimerMetrics(Arrays.asList(
            timer("bifro_task_metric_connect_latency", 50.0, 95.0, 99.0, 200.0),
            timerNoData("bifro_task_metric_end_to_end_latency")
        ));

        invoke(stats, snap);

        assertThat(stats.getAvgConnectLatencyP95()).isNotNull();
        assertThat(stats.getEndToEndLatencyP95()).isNull(); 
    }

    

    @Test
    void populate_durationMs_isSetFromSnapshot() throws Exception {
        TaskStatistics stats = new TaskStatistics();
        TaskMetricsSnapshot snap = new TaskMetricsSnapshot();
        snap.setDurationMs(120000L);

        invoke(stats, snap);

        assertThat(stats.getActualDurationMs()).isEqualTo(120000L);
    }

    @Test
    void populate_nullDurationMs_isNotOverwritten() throws Exception {
        TaskStatistics stats = new TaskStatistics();
        TaskMetricsSnapshot snap = new TaskMetricsSnapshot();
        snap.setDurationMs(null);

        invoke(stats, snap);

        assertThat(stats.getActualDurationMs()).isNull();
    }
}
