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

package org.apache.bifromq.testsuite.app.bean.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskReportResponse {
    private String taskId;
    private String taskName;
    private String taskType;

    
    
    private Long startTime;
    
    private Long endTime;
    
    private Long durationMs;

    
    
    private Long totalMessagesSent;
    
    private Long totalMessagesReceived;
    
    private Long totalBytesTransmitted;
    
    private Double avgMessagesPerSecond;
    
    private Double avgBytesPerSecond;
    
    private Double avgConnectQps;
    
    private Double avgPublishQps;
    
    private Double avgReceiveQps;

    
    
    private Double latencyP50;
    
    private Double latencyP95;
    
    private Double latencyP99;
    
    private Double latencyMax;
    
    private Double connectLatencyP95;
    
    private Double pubackLatencyP95;

    
    
    private Long totalConnectSuccess;
    
    private Long totalConnectFailure;
    
    private Double connectSuccessRate;
    
    private Long totalReconnectCount;

    
    
    private Long totalDuplicateMessages;
    
    private Double duplicateRate;
    
    private Long estimatedMessageLoss;
    
    private Double messageLossRate;

    
    private QosDistribution qosDistribution;

    
    
    private Integer totalNodes;
    
    private Integer onlineNodes;
    
    private List<NodeReport> nodeReports;

    
    
    private Integer totalClients;
    
    private Integer failedClients;

    
    private Map<String, Long> errorCounts;

    
    
    private Map<String, Map<String, Long>> chaosResults;

    
    @Data
    public static class QosDistribution {
        private Long qos0Count;
        private Long qos1Count;
        private Long qos2Count;
        private Double qos0Percent;
        private Double qos1Percent;
        private Double qos2Percent;
    }

    
    @Data
    public static class NodeReport {
        private String nodeId;
        private String nodeName;
        private Integer assignedClients;
        private Long messagesSent;
        private Long messagesReceived;
        private Double latencyP95;
        private Long connectSuccess;
        private Long connectFailure;
        
        private Double avgConnectQps;
        
        private Double avgPublishQps;
        
        private Double avgReceiveQps;
    }
}