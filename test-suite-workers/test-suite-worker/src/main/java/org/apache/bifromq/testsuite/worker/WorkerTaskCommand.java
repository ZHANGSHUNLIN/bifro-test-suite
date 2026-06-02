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

import java.io.Serial;
import java.io.Serializable;
import lombok.Builder;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpec;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpecMapper;

@Builder
public record WorkerTaskCommand(
    String taskId,
    String nodeId,
    TaskConfig.TaskType taskType,
    TaskTemplate template,
    int totalClientCount,
    WorkerTaskSpec workerTaskSpec
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static WorkerTaskCommand fromTaskConfig(TaskConfig taskConfig) {
        return fromTaskConfig(taskConfig, null);
    }

    public static WorkerTaskCommand fromTaskConfig(TaskConfig taskConfig, Long plannedStartAtMs) {
        return WorkerTaskCommand.builder()
            .taskId(taskConfig.getTaskId())
            .nodeId(taskConfig.getNodeId())
            .taskType(taskConfig.getTaskType())
            .template(taskConfig.getTemplate())
            .totalClientCount(taskConfig.getTotalClientCount())
            .workerTaskSpec(WorkerTaskSpec.fromTaskConfig(taskConfig).toBuilder()
                .plannedStartAtMs(plannedStartAtMs)
                .build())
            .build();
    }

    public WorkerPlanSpec createWorkerPlanSpec() {
        return WorkerPlanSpecMapper.fromWorkerTaskSpec(workerTaskSpec);
    }
}
