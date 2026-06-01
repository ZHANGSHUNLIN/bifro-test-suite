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

import org.apache.bifromq.testsuite.app.bean.vo.SubTaskDetail;
import org.apache.bifromq.testsuite.app.bean.vo.TaskConfigView;
import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.app.profile.TaskProfile;
import org.apache.bifromq.testsuite.statemachine.StateTransitionMeta;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDetailResponse {
    List<MqttBroker> brokers;
    private boolean success;
    private String message;
    private String taskId;
    private String taskName;
    private String group;
    private TaskConfigView mainTaskView;
    private TaskProfile publishProfile;
    private Map<String, TaskConfigView> subTasks;
    private Map<String, SubTaskDetail> subTaskDetails;
    private TaskStatistics statistics;
    private Long timestamp = System.currentTimeMillis();
    private Long createTime;

    
    private Long startTime;

    
    private Long endTime;

    
    private Boolean metricsFromSnapshot;

    
    private List<PipelineStageInfo> pipelineStages;

    
    private Integer currentStageIndex;

    
    private List<StateTransitionMeta> stateTransitions;

    public static TaskDetailResponse error(String message) {
        TaskDetailResponse response = new TaskDetailResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}
