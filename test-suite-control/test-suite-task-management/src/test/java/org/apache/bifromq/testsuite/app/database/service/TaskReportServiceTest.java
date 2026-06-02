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

import org.apache.bifromq.testsuite.app.bean.vo.TaskReportResponse;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot;
import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot.NodeMetricsSnapshot;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.metric.CounterMetricData;
import org.apache.bifromq.testsuite.metric.TimerMetricData;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TaskReportServiceTest {

    private static final String TASK_ID = "test-task-001";
    @Mock
    private TaskMetricsSnapshotService taskMetricsSnapshotService;
    @Mock
    private TaskInfoMetadataRepository taskInfoMetadataRepository;
    @InjectMocks
    private TaskReportService taskReportService;

    @BeforeEach
    void setUp() {
    }

    
    
    

    private TaskMetricsSnapshot buildSnapshot(long sent, long received) {
        return buildSnapshot(sent, received, 0, 60_000L, 0);
    }

    private TaskMetricsSnapshot buildSnapshot(long sent, long received, long connectSuccess, long durationMs,
                                              long nodeConnectSuccess) {
        CounterMetricData sentCounter = new CounterMetricData();
        sentCounter.setName("bifro_task_metric_publish_completion_count");
        sentCounter.setCount(sent);
        sentCounter.setTags(java.util.Map.of());

        CounterMetricData receivedCounter = new CounterMetricData();
        receivedCounter.setName("bifro_task_metric_message_received_count");
        receivedCounter.setCount(received);
        receivedCounter.setTags(java.util.Map.of());

        CounterMetricData connectCounter = new CounterMetricData();
        connectCounter.setName("bifro_task_metric_connect_success_count");
        connectCounter.setCount(connectSuccess);
        connectCounter.setTags(java.util.Map.of());

        Map<String, NodeMetricsSnapshot> nodeMetrics = new HashMap<>();
        if (nodeConnectSuccess > 0) {
            CounterMetricData nodeConnectCounter = new CounterMetricData();
            nodeConnectCounter.setName("bifro_task_metric_connect_success_count");
            nodeConnectCounter.setCount(nodeConnectSuccess);
            nodeConnectCounter.setTags(java.util.Map.of());
            nodeMetrics.put("node-1", NodeMetricsSnapshot.builder()
                .nodeId("node-1")
                .nodeName("node-1")
                .counterMetrics(List.of(nodeConnectCounter))
                .timerMetrics(List.of())
                .build());
        }

        return TaskMetricsSnapshot.builder()
            .taskId(TASK_ID)
            .taskName("test-task")
            .durationMs(durationMs)
            .counterMetrics(List.of(sentCounter, receivedCounter, connectCounter))
            .nodeMetrics(nodeMetrics)
            .build();
    }

    private CounterMetricData counter(String name, long count) {
        CounterMetricData data = new CounterMetricData();
        data.setName(name);
        data.setCount(count);
        data.setTags(java.util.Map.of());
        return data;
    }

    private CounterMetricData counter(String name, long count, Map<String, String> tags) {
        CounterMetricData data = new CounterMetricData();
        data.setName(name);
        data.setCount(count);
        data.setTags(tags);
        return data;
    }

    private TaskInfoMetadata buildMetadata(int fanOut) {
        TaskConfig config = new TaskConfig();
        config.setFanOut(fanOut);
        config.setTaskType(TaskConfig.TaskType.PUBSUB);

        return TaskInfoMetadata.builder()
            .taskId(TASK_ID)
            .taskConfig(config)
            .build();
    }

    private TaskReportResponse report(long sent, long received, int fanOut) {
        when(taskMetricsSnapshotService.findByTaskId(TASK_ID))
            .thenReturn(Mono.just(buildSnapshot(sent, received)));
        when(taskInfoMetadataRepository.findById(TASK_ID))
            .thenReturn(Mono.just(buildMetadata(fanOut)));
        return taskReportService.generateReport(TASK_ID).block();
    }

    
    
    

    @Test
    void generateReport_noLoss_whenReceivedEqualsExpected_fanOut1() {
        
        TaskReportResponse r = report(100, 100, 1);
        assertThat(r.getEstimatedMessageLoss()).isNull();
        assertThat(r.getMessageLossRate()).isNull();
    }

    @Test
    void generateReport_correctLossRate_fanOut1() {
        
        TaskReportResponse r = report(100, 80, 1);
        assertThat(r.getEstimatedMessageLoss()).isEqualTo(20);
        assertThat(r.getMessageLossRate()).isEqualTo(20.0);
        assertThat(r.getTaskType()).isEqualTo("PUBSUB");
    }

    
    
    

    @Test
    void generateReport_noLoss_whenReceivedEqualsExpected_fanOut2() {
        
        TaskReportResponse r = report(100, 200, 2);
        assertThat(r.getEstimatedMessageLoss()).isNull();
        assertThat(r.getMessageLossRate()).isNull();
    }

    @Test
    void generateReport_correctLossRate_fanOut2() {
        
        TaskReportResponse r = report(150, 200, 2);
        assertThat(r.getEstimatedMessageLoss()).isEqualTo(100);
        assertThat(r.getMessageLossRate()).isEqualTo(33.33);
    }

    @Test
    void generateReport_correctLossRate_fanOut3() {
        
        TaskReportResponse r = report(100, 240, 3);
        assertThat(r.getEstimatedMessageLoss()).isEqualTo(60);
        assertThat(r.getMessageLossRate()).isEqualTo(20.0);
    }

    
    
    

    @Test
    void generateReport_defaultsFanOutTo1_whenMetadataNotFound() {
        
        when(taskMetricsSnapshotService.findByTaskId(TASK_ID))
            .thenReturn(Mono.just(buildSnapshot(100, 80)));
        when(taskInfoMetadataRepository.findById(TASK_ID))
            .thenReturn(Mono.empty());

        TaskReportResponse r = taskReportService.generateReport(TASK_ID).block();
        assertThat(r.getEstimatedMessageLoss()).isEqualTo(20);
        assertThat(r.getMessageLossRate()).isEqualTo(20.0);
    }

    
    
    

    @Test
    void generateReport_noLossCalculation_whenZeroSent() {
        TaskReportResponse r = report(0, 0, 2);
        assertThat(r.getEstimatedMessageLoss()).isNull();
        assertThat(r.getMessageLossRate()).isNull();
    }

    @Test
    void generateReport_avgConnectQps_usesProfileDuration_whenConnectProfileDataPointsPresent() {
        TaskConfig config = new TaskConfig();
        config.setFanOut(1);
        config.setConnectProfileDataPoints(List.of(
            new long[] {0L, 50L},
            new long[] {300_000L, 0L}
        ));

        when(taskMetricsSnapshotService.findByTaskId(TASK_ID))
            .thenReturn(Mono.just(buildSnapshot(0, 0, 99_994L, 732_184L, 33_331L)));
        when(taskInfoMetadataRepository.findById(TASK_ID))
            .thenReturn(Mono.just(TaskInfoMetadata.builder().taskId(TASK_ID).taskConfig(config).build()));

        TaskReportResponse report = taskReportService.generateReport(TASK_ID).block();
        assertThat(report).isNotNull();
        assertThat(report.getAvgConnectQps()).isEqualTo(333.31);
        assertThat(report.getNodeReports()).isNotNull();
        assertThat(report.getNodeReports()).hasSize(1);
        assertThat(report.getNodeReports().get(0).getAvgConnectQps()).isEqualTo(111.1);
    }

    @Test
    void generateReport_avgConnectQps_fallbacksToDuration_whenConnectProfileDataPointsAbsent() {
        TaskConfig config = new TaskConfig();
        config.setFanOut(1);
        config.setConnectRate(0);

        when(taskMetricsSnapshotService.findByTaskId(TASK_ID))
            .thenReturn(Mono.just(buildSnapshot(0, 0, 120L, 60_000L, 60L)));
        when(taskInfoMetadataRepository.findById(TASK_ID))
            .thenReturn(Mono.just(TaskInfoMetadata.builder().taskId(TASK_ID).taskConfig(config).build()));

        TaskReportResponse report = taskReportService.generateReport(TASK_ID).block();
        assertThat(report).isNotNull();
        assertThat(report.getAvgConnectQps()).isEqualTo(2.0);
        assertThat(report.getNodeReports()).isNotNull();
        assertThat(report.getNodeReports()).hasSize(1);
        assertThat(report.getNodeReports().get(0).getAvgConnectQps()).isEqualTo(1.0);
    }

    @Test
    void generateReport_avgConnectQps_usesFixedConnectRate_whenProfileDataPointsAbsent() {
        TaskConfig config = new TaskConfig();
        config.setFanOut(1);
        config.setTotalClientCount(100);
        config.setConnectRate(100);

        when(taskMetricsSnapshotService.findByTaskId(TASK_ID))
            .thenReturn(Mono.just(buildSnapshot(0, 0, 100L, 180_195L, 34L)));
        when(taskInfoMetadataRepository.findById(TASK_ID))
            .thenReturn(Mono.just(TaskInfoMetadata.builder().taskId(TASK_ID).taskConfig(config).build()));

        TaskReportResponse report = taskReportService.generateReport(TASK_ID).block();
        assertThat(report).isNotNull();
        assertThat(report.getAvgConnectQps()).isEqualTo(100.0);
        assertThat(report.getTotalClients()).isEqualTo(100);
        assertThat(report.getNodeReports()).isNotNull();
        assertThat(report.getNodeReports()).hasSize(1);
        assertThat(report.getNodeReports().get(0).getAssignedClients()).isEqualTo(34);
        assertThat(report.getNodeReports().get(0).getAvgConnectQps()).isEqualTo(34.0);
    }

    @Test
    void generateReport_retryLimitExceededCountsAsConnectFailure() {
        TaskMetricsSnapshot snapshot = TaskMetricsSnapshot.builder()
            .taskId(TASK_ID)
            .taskName("test-task")
            .durationMs(60_000L)
            .counterMetrics(List.of(
                counter("bifro_task_metric_connect_success_count", 96),
                counter("bifro_task_metric_connect_exception_count", 4),
                counter("bifro_task_metric_reconnect_limit_exceeded", 4),
                counter("bifro_task_metric_local_port_bind_failure_count", 12)
            ))
            .nodeMetrics(Map.of("node-1", NodeMetricsSnapshot.builder()
                .nodeId("node-1")
                .nodeName("node-1")
                .counterMetrics(List.of(
                    counter("bifro_task_metric_connect_success_count", 96),
                    counter("bifro_task_metric_connect_exception_count", 4)
                ))
                .timerMetrics(List.of())
                .build()))
            .build();
        TaskConfig config = new TaskConfig();
        config.setTotalClientCount(100);
        config.setConnectRate(100);

        when(taskMetricsSnapshotService.findByTaskId(TASK_ID)).thenReturn(Mono.just(snapshot));
        when(taskInfoMetadataRepository.findById(TASK_ID))
            .thenReturn(Mono.just(TaskInfoMetadata.builder().taskId(TASK_ID).taskConfig(config).build()));

        TaskReportResponse report = taskReportService.generateReport(TASK_ID).block();

        assertThat(report).isNotNull();
        assertThat(report.getTotalConnectSuccess()).isEqualTo(96);
        assertThat(report.getTotalConnectFailure()).isEqualTo(4);
        assertThat(report.getFailedClients()).isEqualTo(4);
        assertThat(report.getConnectSuccessRate()).isEqualTo(96.0);
        assertThat(report.getErrorCounts()).containsEntry("reconnectLimitExceeded", 4L);
        assertThat(report.getErrorCounts()).containsEntry("localPortBindFailure", 12L);
        assertThat(report.getNodeReports()).hasSize(1);
        assertThat(report.getNodeReports().get(0).getConnectFailure()).isEqualTo(4);
    }

    @Test
    void generateReport_qosDistributionUsesMetricName() {
        TaskMetricsSnapshot snapshot = buildSnapshot(120, 120);
        snapshot.setCounterMetrics(List.of(
            counter("bifro_task_metric_publish_completion_count", 120),
            counter("bifro_task_metric_message_received_count", 120),
            counter("bifro_task_metric_qos1_message_count", 120)
        ));

        when(taskMetricsSnapshotService.findByTaskId(TASK_ID)).thenReturn(Mono.just(snapshot));
        when(taskInfoMetadataRepository.findById(TASK_ID))
            .thenReturn(Mono.just(buildMetadata(1)));

        TaskReportResponse report = taskReportService.generateReport(TASK_ID).block();

        assertThat(report).isNotNull();
        assertThat(report.getQosDistribution().getQos0Count()).isZero();
        assertThat(report.getQosDistribution().getQos1Count()).isEqualTo(120);
        assertThat(report.getQosDistribution().getQos2Count()).isZero();
        assertThat(report.getQosDistribution().getQos1Percent()).isEqualTo(100.0);
    }

    @Test
    void generateReport_qosDistributionUsesQosTagForLegacyMetricName() {
        TaskMetricsSnapshot snapshot = buildSnapshot(2252, 2252);
        snapshot.setCounterMetrics(List.of(
            counter("bifro_task_metric_publish_completion_count", 2252),
            counter("bifro_task_metric_message_received_count", 2252),
            counter("bifro_task_metric_qos0_message_count", 2252, Map.of("qos", "1"))
        ));

        when(taskMetricsSnapshotService.findByTaskId(TASK_ID)).thenReturn(Mono.just(snapshot));
        when(taskInfoMetadataRepository.findById(TASK_ID))
            .thenReturn(Mono.just(buildMetadata(1)));

        TaskReportResponse report = taskReportService.generateReport(TASK_ID).block();

        assertThat(report).isNotNull();
        assertThat(report.getQosDistribution().getQos0Count()).isZero();
        assertThat(report.getQosDistribution().getQos1Count()).isEqualTo(2252);
        assertThat(report.getQosDistribution().getQos2Count()).isZero();
        assertThat(report.getQosDistribution().getQos1Percent()).isEqualTo(100.0);
    }

    @Test
    void generateReport_connectLatencyP95_fallbacksToMean_whenPercentileIsMissing() {
        TaskConfig config = new TaskConfig();
        config.setFanOut(1);
        config.setTotalClientCount(100);
        config.setConnectRate(100);

        TimerMetricData connectTimer = TimerMetricData.builder()
            .name("bifro_task_metric_connect_latency")
            .tags(Map.of())
            .count(100)
            .mean(34.35)
            .p50(0.0)
            .p95(0.0)
            .p99(0.0)
            .max(36.79)
            .totalTime(3435.0)
            .hasData(true)
            .build();

        TaskMetricsSnapshot snapshot = buildSnapshot(0, 0, 100L, 180_195L, 34L);
        snapshot.setTimerMetrics(List.of(connectTimer));
        snapshot.getNodeMetrics().values().forEach(node -> node.setTimerMetrics(List.of(connectTimer)));

        when(taskMetricsSnapshotService.findByTaskId(TASK_ID)).thenReturn(Mono.just(snapshot));
        when(taskInfoMetadataRepository.findById(TASK_ID))
            .thenReturn(Mono.just(TaskInfoMetadata.builder().taskId(TASK_ID).taskConfig(config).build()));

        TaskReportResponse report = taskReportService.generateReport(TASK_ID).block();
        assertThat(report).isNotNull();
        assertThat(report.getConnectLatencyP95()).isEqualTo(34.35);
        assertThat(report.getNodeReports()).isNotNull();
        assertThat(report.getNodeReports().get(0).getLatencyP95()).isEqualTo(34.35);
    }

    @Test
    void generateReport_pubsubQpsAndLatency_useMessagePhaseAndMessageLatency() {
        TaskConfig config = new TaskConfig();
        config.setTaskType(TaskConfig.TaskType.PUBSUB);
        config.setPayloadMode(PayloadMode.BIFRO);
        config.setFanOut(1);
        config.setTotalClientCount(300);
        config.setConnectRate(100);
        config.setStressDurationInSec(60);
        config.setDelayAfterStageInSec(30);

        TimerMetricData deliveryTimer = TimerMetricData.builder()
            .name("bifro_task_metric_message_delivery_latency")
            .tags(Map.of())
            .count(1350)
            .mean(37.3)
            .p50(0.0)
            .p95(0.0)
            .p99(0.0)
            .max(79.44)
            .totalTime(50_355.0)
            .hasData(true)
            .build();
        TimerMetricData connectTimer = TimerMetricData.builder()
            .name("bifro_task_metric_connect_latency")
            .tags(Map.of())
            .count(300)
            .mean(90.47)
            .p50(69.2)
            .p95(140.5)
            .p99(140.5)
            .max(160.0)
            .totalTime(27_141.0)
            .hasData(true)
            .build();

        TaskMetricsSnapshot snapshot = buildSnapshot(1350, 1350, 300L, 332_251L, 300L);
        snapshot.setTimerMetrics(List.of(deliveryTimer, connectTimer));
        snapshot.getNodeMetrics().values().forEach(node -> {
            node.setCounterMetrics(snapshot.getCounterMetrics());
            node.setTimerMetrics(List.of(deliveryTimer, connectTimer));
        });

        when(taskMetricsSnapshotService.findByTaskId(TASK_ID)).thenReturn(Mono.just(snapshot));
        when(taskInfoMetadataRepository.findById(TASK_ID))
            .thenReturn(Mono.just(TaskInfoMetadata.builder().taskId(TASK_ID).taskConfig(config).build()));

        TaskReportResponse report = taskReportService.generateReport(TASK_ID).block();
        assertThat(report).isNotNull();
        assertThat(report.getAvgPublishQps()).isEqualTo(15.0);
        assertThat(report.getAvgReceiveQps()).isEqualTo(15.0);
        assertThat(report.getAvgMessagesPerSecond()).isEqualTo(15.0);
        assertThat(report.getLatencyP95()).isEqualTo(37.3);
        assertThat(report.getConnectLatencyP95()).isEqualTo(140.5);
        assertThat(report.getNodeReports()).isNotNull();
        assertThat(report.getNodeReports().get(0).getAvgPublishQps()).isEqualTo(15.0);
        assertThat(report.getNodeReports().get(0).getLatencyP95()).isEqualTo(37.3);
    }

    @Test
    void generateReport_templatePayload_ignoresMessageLatencyTimer() {
        TaskConfig config = new TaskConfig();
        config.setTaskType(TaskConfig.TaskType.PUBSUB);
        config.setPayloadMode(PayloadMode.TEMPLATE);
        config.setFanOut(1);
        config.setTotalClientCount(300);
        config.setConnectRate(100);
        config.setStressDurationInSec(60);
        config.setDelayAfterStageInSec(30);

        TimerMetricData deliveryTimer = TimerMetricData.builder()
            .name("bifro_task_metric_message_delivery_latency")
            .tags(Map.of())
            .count(1350)
            .mean(37.3)
            .p95(37.3)
            .max(79.44)
            .hasData(true)
            .build();
        TimerMetricData connectTimer = TimerMetricData.builder()
            .name("bifro_task_metric_connect_latency")
            .tags(Map.of())
            .count(300)
            .mean(90.47)
            .p95(140.5)
            .max(160.0)
            .hasData(true)
            .build();

        TaskMetricsSnapshot snapshot = buildSnapshot(1350, 1350, 300L, 332_251L, 300L);
        snapshot.setTimerMetrics(List.of(deliveryTimer, connectTimer));
        snapshot.getNodeMetrics().values().forEach(node -> {
            node.setCounterMetrics(snapshot.getCounterMetrics());
            node.setTimerMetrics(List.of(deliveryTimer, connectTimer));
        });

        when(taskMetricsSnapshotService.findByTaskId(TASK_ID)).thenReturn(Mono.just(snapshot));
        when(taskInfoMetadataRepository.findById(TASK_ID))
            .thenReturn(Mono.just(TaskInfoMetadata.builder().taskId(TASK_ID).taskConfig(config).build()));

        TaskReportResponse report = taskReportService.generateReport(TASK_ID).block();
        assertThat(report).isNotNull();
        assertThat(report.getLatencyP50()).isNull();
        assertThat(report.getLatencyP95()).isNull();
        assertThat(report.getLatencyP99()).isNull();
        assertThat(report.getLatencyMax()).isNull();
        assertThat(report.getConnectLatencyP95()).isEqualTo(140.5);
        assertThat(report.getNodeReports()).isNotNull();
        assertThat(report.getNodeReports().get(0).getLatencyP95()).isEqualTo(140.5);
    }
}
