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
import lombok.Builder;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.worker.TaskConfig;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskConfigView(
    String taskId,
    String nodeId,
    TaskConfig.TaskType taskType,
    TaskTemplate template,
    int totalClientCount,
    int stressDurationInSec,
    TaskStage taskWorkStage,
    Long plannedStartAtMs
) {
    public static TaskConfigView fromTaskConfig(TaskConfig config, TaskStage stage) {
        return fromTaskConfig(config, stage, null);
    }

    public static TaskConfigView fromTaskConfig(TaskConfig config, TaskStage stage, Long plannedStartAtMs) {
        if (config == null) {
            return null;
        }
        return TaskConfigView.builder()
            .taskId(config.getTaskId())
            .nodeId(config.getNodeId())
            .taskType(config.getTaskType())
            .template(config.getTemplate())
            .totalClientCount(config.getTotalClientCount())
            .stressDurationInSec(config.getStressDurationInSec())
            .taskWorkStage(stage != null ? stage : config.getTaskWorkStage())
            .plannedStartAtMs(plannedStartAtMs)
            .build();
    }
}
