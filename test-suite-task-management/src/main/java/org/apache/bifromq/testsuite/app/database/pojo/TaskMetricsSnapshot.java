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

package org.apache.bifromq.testsuite.app.database.pojo;

import org.apache.bifromq.testsuite.metric.CounterMetricData;
import org.apache.bifromq.testsuite.metric.TimerMetricData;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "task_metrics_snapshot")
public class TaskMetricsSnapshot implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    private String taskId;
    
    private String taskName;

    private String taskWorkStage;

    private String nodeId;

    private String nodeName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private List<CounterMetricData> counterMetrics;

    private List<TimerMetricData> timerMetrics;

    private Map<String, NodeMetricsSnapshot> nodeMetrics;

    private LocalDateTime createTime;
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NodeMetricsSnapshot implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String nodeId;
        private String nodeName;
        private List<CounterMetricData> counterMetrics;
        private List<TimerMetricData> timerMetrics;
    }
}
