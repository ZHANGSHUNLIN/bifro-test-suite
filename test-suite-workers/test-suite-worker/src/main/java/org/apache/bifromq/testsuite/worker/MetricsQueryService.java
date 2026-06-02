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

package org.apache.bifromq.testsuite.worker;

import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.metric.CounterMetricData;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.metric.NodeMetricsRequest;
import org.apache.bifromq.testsuite.metric.NodeMetricsResponse;
import org.apache.bifromq.testsuite.metric.TimerMetricData;

@Slf4j
public class MetricsQueryService {

    public NodeMetricsResponse query(NodeMetricsRequest request) {
        try {
            List<CounterMetricData> counters = MetricsHelper.readCounters(
                request.getTaskId(), request.getMetricNames());
            List<TimerMetricData> timers = MetricsHelper.readTimers(
                request.getTaskId(), request.getMetricNames());

            return NodeMetricsResponse.builder()
                .nodeId(request.getNodeId())
                .success(true)
                .timestamp(System.currentTimeMillis())
                .counterMetrics(counters)
                .timerMetrics(timers)
                .build();
        } catch (Exception e) {
            log.error("Failed to query metrics for node={}", request.getNodeId(), e);
            return NodeMetricsResponse.builder()
                .nodeId(request.getNodeId())
                .success(false)
                .errorMessage(e.getMessage())
                .timestamp(System.currentTimeMillis())
                .counterMetrics(Collections.emptyList())
                .timerMetrics(Collections.emptyList())
                .build();
        }
    }
}
