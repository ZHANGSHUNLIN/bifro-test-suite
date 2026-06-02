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

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.bean.vo.TaskReportResponse;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot;
import org.apache.bifromq.testsuite.app.database.pojo.TaskMetricsSnapshot.NodeMetricsSnapshot;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.constants.PayloadMode;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.CounterMetricData;
import org.apache.bifromq.testsuite.metric.TimerMetricData;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@ConditionalOnControlPlane
public class TaskReportService {

    private static final String METRIC_CONNECT_SUCCESS = "bifro_task_metric_connect_success_count";
    private static final String METRIC_CONNECT_EXCEPTION = "bifro_task_metric_connect_exception_count";
    private static final String METRIC_RECONNECT_LIMIT_EXCEEDED = "bifro_task_metric_reconnect_limit_exceeded";
    private static final String METRIC_LOCAL_PORT_BIND_FAILURE = "bifro_task_metric_local_port_bind_failure_count";
    private static final String METRIC_MESSAGE_RECEIVED = "bifro_task_metric_message_received_count";
    private static final String METRIC_MESSAGE_DUPLICATE = "bifro_task_metric_message_duplicate_count";
    private static final String METRIC_PUBLISH_COMPLETION = "bifro_task_metric_publish_completion_count";
    private static final String METRIC_RECONNECT = "bifro_task_metric_reconnect_count";
    private static final String METRIC_QOS0 = "bifro_task_metric_qos0_message_count";
    private static final String METRIC_QOS1 = "bifro_task_metric_qos1_message_count";
    private static final String METRIC_QOS2 = "bifro_task_metric_qos2_message_count";
    private static final String METRIC_THROUGHPUT_MSG = "bifro_task_metric_throughput_messages";
    private static final String METRIC_THROUGHPUT_BYTES = "bifro_task_metric_throughput_bytes";
    private static final String METRIC_CLIENT_FAILURE = "bifro_task_metric_client_failure_count";
    private static final String METRIC_CLIENT_CREATED = "bifro_task_metric_client_created_count";
    private static final String TIMER_END_TO_END = "bifro_task_metric_end_to_end_latency";
    private static final String TIMER_MESSAGE_DELIVERY = "bifro_task_metric_message_delivery_latency";
    private static final String TIMER_CONNECT = "bifro_task_metric_connect_latency";
    private static final String TIMER_PUBACK = "bifro_task_metric_puback_latency";
    @Resource
    private TaskMetricsSnapshotService taskMetricsSnapshotService;
    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    public Mono<TaskReportResponse> generateReport(String taskId) {
        Mono<TaskMetricsSnapshot> snapshotMono = taskMetricsSnapshotService.findByTaskId(taskId);
        Mono<TaskInfoMetadata> metadataMono =
            taskInfoMetadataRepository.findById(taskId).defaultIfEmpty(new TaskInfoMetadata());

        return Mono.zip(snapshotMono, metadataMono)
            .map(tuple -> buildReport(tuple.getT1(), tuple.getT2().getTaskConfig()))
            .doOnSuccess(report -> log.info("Report generated for task: {}", taskId))
            .doOnError(e -> log.error("Failed to generate report for task: {}", taskId, e));
    }

    private TaskReportResponse buildReport(TaskMetricsSnapshot snapshot, TaskConfig taskConfig) {
        int fanOut = 1;
        String template = "";
        String taskType = null;
        if (taskConfig != null) {
            fanOut = Math.max(1, taskConfig.getFanOut());
            taskType = taskConfig.getTaskType() != null ? taskConfig.getTaskType().name() : null;
            if (taskConfig.getTaskType() == TaskConfig.TaskType.CHAOS) {
                template = "CHAOS";
            } else if (taskConfig.getTemplate() != null) {
                template = taskConfig.getTemplate().name();
            }
        }
        TaskReportResponse report = new TaskReportResponse();
        report.setTaskId(snapshot.getTaskId());
        report.setTaskName(snapshot.getTaskName());
        report.setTaskType(taskType);
        report.setStartTime(snapshot.getStartTime() != null ?
            snapshot.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null);
        report.setEndTime(snapshot.getEndTime() != null ?
            snapshot.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null);
        report.setDurationMs(snapshot.getDurationMs());

        Map<String, Double> counterMap = extractCounters(snapshot.getCounterMetrics());

        report.setTotalMessagesSent(counterMap.getOrDefault(METRIC_PUBLISH_COMPLETION, 0.0).longValue());
        report.setTotalMessagesReceived(counterMap.getOrDefault(METRIC_MESSAGE_RECEIVED, 0.0).longValue());
        report.setTotalBytesTransmitted(counterMap.getOrDefault(METRIC_THROUGHPUT_BYTES, 0.0).longValue());

        long connectSuccess = counterMap.getOrDefault(METRIC_CONNECT_SUCCESS, 0.0).longValue();
        long connectFailure = counterMap.getOrDefault(METRIC_CONNECT_EXCEPTION, 0.0).longValue();
        report.setTotalConnectSuccess(connectSuccess);
        report.setTotalConnectFailure(connectFailure);

        long totalConnects = connectSuccess + connectFailure;
        if (totalConnects > 0) {
            report.setConnectSuccessRate(Math.round(connectSuccess * 10000.0 / totalConnects) / 100.0);
        }

        report.setTotalReconnectCount(counterMap.getOrDefault(METRIC_RECONNECT, 0.0).longValue());

        long connectPhaseDurationMs = resolveConnectPhaseDurationMs(taskConfig, snapshot.getDurationMs());
        long messagePhaseDurationMs = resolveMessagePhaseDurationMs(taskConfig, snapshot.getDurationMs());

        if (snapshot.getDurationMs() != null && snapshot.getDurationMs() > 0) {
            double durationSec = snapshot.getDurationMs() / 1000.0;
            report.setAvgMessagesPerSecond(report.getTotalMessagesReceived() / durationSec);
            report.setAvgBytesPerSecond(report.getTotalBytesTransmitted() / durationSec);
        }
        if (messagePhaseDurationMs > 0) {
            double messageDurationSec = messagePhaseDurationMs / 1000.0;
            report.setAvgMessagesPerSecond(report.getTotalMessagesReceived() / messageDurationSec);
            report.setAvgBytesPerSecond(report.getTotalBytesTransmitted() / messageDurationSec);
            report.setAvgPublishQps(Math.round(report.getTotalMessagesSent() * 100.0 / messageDurationSec) / 100.0);
            report.setAvgReceiveQps(Math.round(report.getTotalMessagesReceived() * 100.0 / messageDurationSec) / 100.0);
        }
        if (connectPhaseDurationMs > 0) {
            double connectDurationSec = connectPhaseDurationMs / 1000.0;
            report.setAvgConnectQps(Math.round(connectSuccess * 100.0 / connectDurationSec) / 100.0);
        }

        long duplicateMsg = counterMap.getOrDefault(METRIC_MESSAGE_DUPLICATE, 0.0).longValue();
        report.setTotalDuplicateMessages(duplicateMsg);

        long totalReceived = report.getTotalMessagesReceived();
        if (totalReceived > 0) {
            report.setDuplicateRate(Math.round(duplicateMsg * 10000.0 / totalReceived) / 100.0);
        }

        boolean hasSubscribers = !"PUBSUB_PUB_ONLY".equals(template) && !"CONN_STANDARD".equals(template)
            && !"CONN_IMMEDIATE_DISCONNECT".equals(template) && !"CHAOS_STANDARD".equals(template)
            && !"CHAOS".equals(template);
        long totalSent = report.getTotalMessagesSent();
        long expectedReceived = totalSent * fanOut;
        if (hasSubscribers && expectedReceived > totalReceived) {
            long messageLoss = expectedReceived - totalReceived;
            report.setEstimatedMessageLoss(messageLoss);
            report.setMessageLossRate(Math.round(messageLoss * 10000.0 / expectedReceived) / 100.0);
        }

        report.setQosDistribution(buildQosDistribution(snapshot.getCounterMetrics()));

        report.setTotalClients(resolveTotalClients(counterMap, taskConfig));
        report.setFailedClients(resolveFailedClients(counterMap, connectFailure));

        Map<String, TimerMetricData> timerMap = extractTimers(snapshot.getTimerMetrics());

        TimerMetricData latencyTimer = supportsMessageLatency(taskConfig)
            ? resolveMessageLatencyTimer(timerMap)
            : null;
        if (latencyTimer != null && latencyTimer.isHasData()) {
            report.setLatencyP50(resolvePercentile(latencyTimer, latencyTimer.getP50()));
            report.setLatencyP95(resolvePercentile(latencyTimer, latencyTimer.getP95()));
            report.setLatencyP99(resolvePercentile(latencyTimer, latencyTimer.getP99()));
            report.setLatencyMax(latencyTimer.getMax());
        }

        TimerMetricData connectTimer = timerMap.get(TIMER_CONNECT);
        if (connectTimer != null && connectTimer.isHasData()) {
            report.setConnectLatencyP95(resolveP95(connectTimer));
        }

        TimerMetricData pubackTimer = timerMap.get(TIMER_PUBACK);
        if (pubackTimer != null && pubackTimer.isHasData()) {
            report.setPubackLatencyP95(pubackTimer.getP95());
        }

        if (snapshot.getNodeMetrics() != null) {
            report.setTotalNodes(snapshot.getNodeMetrics().size());
            report.setOnlineNodes(snapshot.getNodeMetrics().size());
            report.setNodeReports(buildNodeReports(snapshot.getNodeMetrics(), taskConfig, snapshot.getDurationMs(),
                connectPhaseDurationMs, messagePhaseDurationMs));
        }

        report.setErrorCounts(buildErrorCounts(counterMap));

        if ("CHAOS".equals(template)) {
            report.setChaosResults(buildChaosResults(snapshot.getCounterMetrics()));
        }

        return report;
    }


    private Map<String, Map<String, Long>> buildChaosResults(List<CounterMetricData> counters) {
        Map<String, Map<String, Long>> result = new java.util.LinkedHashMap<>();
        if (counters == null) {
            return result;
        }
        String metricName = BifroTaskMetric.CHAOS_BEHAVIOR_COUNT.getName();
        for (CounterMetricData c : counters) {
            if (!metricName.equals(c.getName())) {
                continue;
            }
            Map<String, String> tags = c.getTags();
            if (tags == null) {
                continue;
            }
            String behavior = tags.getOrDefault("behavior", "UNKNOWN");
            String reaction = tags.getOrDefault("brokerReaction", "UNKNOWN");
            result.computeIfAbsent(behavior, k -> new java.util.LinkedHashMap<>())
                .merge(reaction, (long) c.getCount(), Long::sum);
        }
        return result;
    }


    private Map<String, Double> extractCounters(List<CounterMetricData> counters) {
        Map<String, Double> map = new HashMap<>();
        if (counters == null) {
            return map;
        }

        for (CounterMetricData counter : counters) {
            map.merge(counter.getName(), counter.getCount(), Double::sum);
        }
        return map;
    }


    private Map<String, TimerMetricData> extractTimers(List<TimerMetricData> timers) {
        Map<String, TimerMetricData> map = new HashMap<>();
        if (timers == null) {
            return map;
        }

        for (TimerMetricData timer : timers) {
            String name = timer.getName();
            if (!map.containsKey(name)) {
                map.put(name, timer);
            } else {

                TimerMetricData existing = map.get(name);
                long totalCount = existing.getCount() + timer.getCount();
                double weightedMean = totalCount > 0
                    ? (existing.getMean() * existing.getCount() + timer.getMean() * timer.getCount()) / totalCount
                    : 0.0;
                map.put(name, TimerMetricData.builder()
                    .name(name)
                    .tags(existing.getTags())
                    .count(totalCount)
                    .mean(weightedMean)
                    .p50(Math.max(existing.getP50(), timer.getP50()))
                    .p95(Math.max(existing.getP95(), timer.getP95()))
                    .p99(Math.max(existing.getP99(), timer.getP99()))
                    .max(Math.max(existing.getMax(), timer.getMax()))
                    .totalTime(existing.getTotalTime() + timer.getTotalTime())
                    .hasData(existing.isHasData() || timer.isHasData())
                    .build());
            }
        }
        return map;
    }


    private TaskReportResponse.QosDistribution buildQosDistribution(List<CounterMetricData> counters) {
        TaskReportResponse.QosDistribution dist = new TaskReportResponse.QosDistribution();

        long qos0 = 0;
        long qos1 = 0;
        long qos2 = 0;
        if (counters != null) {
            for (CounterMetricData counter : counters) {
                String name = counter.getName();
                if (!METRIC_QOS0.equals(name) && !METRIC_QOS1.equals(name) && !METRIC_QOS2.equals(name)) {
                    continue;
                }
                int qos = resolveQos(counter);
                long count = (long) counter.getCount();
                if (qos == 1) {
                    qos1 += count;
                } else if (qos == 2) {
                    qos2 += count;
                } else {
                    qos0 += count;
                }
            }
        }
        long total = qos0 + qos1 + qos2;

        dist.setQos0Count(qos0);
        dist.setQos1Count(qos1);
        dist.setQos2Count(qos2);

        if (total > 0) {
            dist.setQos0Percent(Math.round(qos0 * 10000.0 / total) / 100.0);
            dist.setQos1Percent(Math.round(qos1 * 10000.0 / total) / 100.0);
            dist.setQos2Percent(Math.round(qos2 * 10000.0 / total) / 100.0);
        }

        return dist;
    }

    private int resolveQos(CounterMetricData counter) {
        Map<String, String> tags = counter.getTags();
        if (tags != null && tags.containsKey("qos")) {
            try {
                int qos = Integer.parseInt(tags.get("qos"));
                if (qos >= 0 && qos <= 2) {
                    return qos;
                }
            } catch (NumberFormatException expected) {
            }
        }
        if (METRIC_QOS1.equals(counter.getName())) {
            return 1;
        }
        if (METRIC_QOS2.equals(counter.getName())) {
            return 2;
        }
        return 0;
    }


    private List<TaskReportResponse.NodeReport> buildNodeReports(Map<String, NodeMetricsSnapshot> nodeMetrics,
                                                                 TaskConfig taskConfig,
                                                                 Long durationMs,
                                                                 Long connectPhaseDurationMs,
                                                                 Long messagePhaseDurationMs) {
        List<TaskReportResponse.NodeReport> reports = new ArrayList<>();
        double durationSec = (durationMs != null && durationMs > 0) ? durationMs / 1000.0 : 0;
        double connectDurationSec = (connectPhaseDurationMs != null && connectPhaseDurationMs > 0)
            ? connectPhaseDurationMs / 1000.0
            : 0;
        double messageDurationSec = (messagePhaseDurationMs != null && messagePhaseDurationMs > 0)
            ? messagePhaseDurationMs / 1000.0
            : 0;

        for (NodeMetricsSnapshot node : nodeMetrics.values()) {
            TaskReportResponse.NodeReport nodeReport = new TaskReportResponse.NodeReport();
            nodeReport.setNodeId(node.getNodeId());
            nodeReport.setNodeName(node.getNodeName());

            Map<String, Double> nodeCounters = extractCounters(node.getCounterMetrics());
            long nodeSent = nodeCounters.getOrDefault(METRIC_PUBLISH_COMPLETION, 0.0).longValue();
            long nodeReceived = nodeCounters.getOrDefault(METRIC_MESSAGE_RECEIVED, 0.0).longValue();
            long nodeConnectSuccess = nodeCounters.getOrDefault(METRIC_CONNECT_SUCCESS, 0.0).longValue();
            long nodeConnectFailure = nodeCounters.getOrDefault(METRIC_CONNECT_EXCEPTION, 0.0).longValue();
            nodeReport.setMessagesSent(nodeSent);
            nodeReport.setMessagesReceived(nodeReceived);
            nodeReport.setAssignedClients(
                resolveNodeAssignedClients(nodeCounters, nodeConnectSuccess, nodeConnectFailure));
            nodeReport.setConnectSuccess(nodeConnectSuccess);
            nodeReport.setConnectFailure(nodeConnectFailure);

            if (connectDurationSec > 0) {
                nodeReport.setAvgConnectQps(Math.round(nodeConnectSuccess * 100.0 / connectDurationSec) / 100.0);
            }
            if (messageDurationSec > 0) {
                nodeReport.setAvgPublishQps(Math.round(nodeSent * 100.0 / messageDurationSec) / 100.0);
                nodeReport.setAvgReceiveQps(Math.round(nodeReceived * 100.0 / messageDurationSec) / 100.0);
            }

            Map<String, TimerMetricData> nodeTimers = extractTimers(node.getTimerMetrics());
            TimerMetricData latencyTimer = supportsMessageLatency(taskConfig)
                ? resolveMessageLatencyTimer(nodeTimers)
                : null;
            if (latencyTimer != null && latencyTimer.isHasData()) {
                nodeReport.setLatencyP95(resolvePercentile(latencyTimer, latencyTimer.getP95()));
            } else {
                TimerMetricData connectTimer = nodeTimers.get(TIMER_CONNECT);
                if (connectTimer != null && connectTimer.isHasData()) {
                    nodeReport.setLatencyP95(resolveP95(connectTimer));
                }
            }

            reports.add(nodeReport);
        }

        return reports;
    }

    private boolean supportsMessageLatency(TaskConfig taskConfig) {
        return taskConfig == null || taskConfig.getPayloadMode() == null
            || taskConfig.getPayloadMode() == PayloadMode.BIFRO;
    }

    private TimerMetricData resolveMessageLatencyTimer(Map<String, TimerMetricData> timerMap) {
        TimerMetricData e2eTimer = timerMap.get(TIMER_END_TO_END);
        TimerMetricData deliveryTimer = timerMap.get(TIMER_MESSAGE_DELIVERY);
        return e2eTimer != null ? e2eTimer : deliveryTimer;
    }


    private Map<String, Long> buildErrorCounts(Map<String, Double> counterMap) {
        Map<String, Long> errors = new HashMap<>();

        if (counterMap.getOrDefault(METRIC_CONNECT_EXCEPTION, 0.0) > 0) {
            errors.put("connectException", counterMap.get(METRIC_CONNECT_EXCEPTION).longValue());
        }
        if (counterMap.getOrDefault(METRIC_CLIENT_FAILURE, 0.0) > 0) {
            errors.put("clientFailure", counterMap.get(METRIC_CLIENT_FAILURE).longValue());
        }
        if (counterMap.getOrDefault(METRIC_RECONNECT, 0.0) > 0) {
            errors.put("reconnect", counterMap.get(METRIC_RECONNECT).longValue());
        }
        if (counterMap.getOrDefault(METRIC_RECONNECT_LIMIT_EXCEEDED, 0.0) > 0) {
            errors.put("reconnectLimitExceeded", counterMap.get(METRIC_RECONNECT_LIMIT_EXCEEDED).longValue());
        }
        if (counterMap.getOrDefault(METRIC_LOCAL_PORT_BIND_FAILURE, 0.0) > 0) {
            errors.put("localPortBindFailure", counterMap.get(METRIC_LOCAL_PORT_BIND_FAILURE).longValue());
        }
        if (counterMap.getOrDefault(METRIC_MESSAGE_DUPLICATE, 0.0) > 0) {
            errors.put("duplicateMessage", counterMap.get(METRIC_MESSAGE_DUPLICATE).longValue());
        }

        return errors;
    }

    private long resolveConnectPhaseDurationMs(TaskConfig taskConfig, Long durationMs) {
        long fallbackDuration = (durationMs != null && durationMs > 0) ? durationMs : 0;
        if (taskConfig == null) {
            return fallbackDuration;
        }
        if (taskConfig.getConnectProfileDataPoints() == null || taskConfig.getConnectProfileDataPoints().isEmpty()) {
            return resolveFixedConnectDurationMs(taskConfig, fallbackDuration);
        }
        List<long[]> points = taskConfig.getConnectProfileDataPoints();
        for (int i = points.size() - 1; i >= 0; i--) {
            long[] point = points.get(i);
            if (point != null && point.length > 0 && point[0] > 0) {
                return point[0];
            }
        }
        return fallbackDuration;
    }

    private long resolveMessagePhaseDurationMs(TaskConfig taskConfig, Long durationMs) {
        long fallbackDuration = (durationMs != null && durationMs > 0) ? durationMs : 0;
        if (taskConfig == null || taskConfig.getTaskType() == TaskConfig.TaskType.CONN) {
            return fallbackDuration;
        }
        long stressDurationMs = taskConfig.getStressDurationInSec() > 0
            ? taskConfig.getStressDurationInSec() * 1000L
            : 0;
        if (stressDurationMs <= 0) {
            return fallbackDuration;
        }
        long preStressPublishMs = Math.max(0, taskConfig.getDelayAfterStageInSec()) * 1000L;
        return stressDurationMs + preStressPublishMs;
    }

    private int resolveTotalClients(Map<String, Double> counterMap, TaskConfig taskConfig) {
        int metricTotal = counterMap.getOrDefault(METRIC_CLIENT_CREATED, 0.0).intValue();
        if (metricTotal > 0 || taskConfig == null) {
            return metricTotal;
        }
        return Math.max(0, taskConfig.getTotalClientCount());
    }

    private int resolveFailedClients(Map<String, Double> counterMap, long connectFailure) {
        int clientFailure = counterMap.getOrDefault(METRIC_CLIENT_FAILURE, 0.0).intValue();
        return Math.toIntExact(Math.max(clientFailure, connectFailure));
    }

    private int resolveNodeAssignedClients(Map<String, Double> nodeCounters, long connectSuccess, long connectFailure) {
        int metricTotal = nodeCounters.getOrDefault(METRIC_CLIENT_CREATED, 0.0).intValue();
        if (metricTotal > 0) {
            return metricTotal;
        }
        return Math.toIntExact(Math.max(0L, connectSuccess + connectFailure));
    }

    private double resolveP95(TimerMetricData timer) {
        return resolvePercentile(timer, timer.getP95());
    }

    private double resolvePercentile(TimerMetricData timer, double percentile) {
        if (percentile > 0.0) {
            return percentile;
        }
        return timer.getMean() > 0.0 ? timer.getMean() : 0.0;
    }

    private long resolveFixedConnectDurationMs(TaskConfig taskConfig, long fallbackDuration) {
        int totalClientCount = taskConfig.getTotalClientCount();
        int connectRate = taskConfig.getConnectRate();
        if (totalClientCount <= 0 || connectRate <= 0) {
            return fallbackDuration;
        }
        return Math.max(1L, (long) Math.ceil(totalClientCount * 1000.0 / connectRate));
    }
}
