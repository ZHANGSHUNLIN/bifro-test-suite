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

import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.pipeline.PipelineStageSnapshot;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskCommand;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndex(name = "idx_task_node", def = "{'taskId': 1, 'nodeId': 1}", unique = true)
@Document(collection = "node_task")
public class NodeTask {

    @Id
    private String id;

    private String taskId;

    private String nodeId;

    private String nodeName;

    private TaskConfig taskConfig;

    private WorkerTaskCommand workerTaskCommand;

    private TaskStage currentStage;

    private Instant stageUpdatedAt;

    private Long plannedStartAtMs;

    private List<PipelineStageSnapshot> pipelineStages;
}
