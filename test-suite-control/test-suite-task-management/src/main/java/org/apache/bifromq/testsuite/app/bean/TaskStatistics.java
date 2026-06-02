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

package org.apache.bifromq.testsuite.app.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskStatistics {
    
    private Integer totalNodes;
    private Integer totalAssignedClients;
    private Integer minClientsPerNode;
    private Integer maxClientsPerNode;
    private Integer averageClientsPerNode;
    private Double distributionBalance; 

    
    
    private Long actualDurationMs;

    
    
    private Long totalConnectSuccess;

    
    private Long totalConnectException;

    
    private Long totalReconnect;

    
    private Long totalClientCreated;

    
    private Long totalClientFailure;

    
    
    private Long totalMessageReceived;

    
    private Long totalMessageDuplicate;

    
    private Long totalPublishCompletion;

    
    
    private Double connectLatencyP50;

    
    private Double avgConnectLatencyP95;

    
    private Double connectLatencyP99;

    
    private Double connectLatencyMax;

    
    
    private Double endToEndLatencyP50;

    
    private Double endToEndLatencyP95;

    
    private Double endToEndLatencyP99;

    
    
    private Double pubackLatencyP95;
}
