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

package org.apache.bifromq.testsuite.app.task.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.junit.jupiter.api.Test;

class TaskRuntimeStatesTest {

    @Test
    void mainStageShouldPreferCurrentStageAndFallbackToTaskConfigStage() {
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .currentStage(TaskStage.ONGOING)
            .taskConfig(TaskConfig.builder().taskWorkStage(TaskStage.ASSIGNED).build())
            .build();

        assertThat(TaskRuntimeStates.mainStage(metadata)).isEqualTo(TaskStage.ONGOING);

        metadata.setCurrentStage(null);
        assertThat(TaskRuntimeStates.mainStage(metadata)).isEqualTo(TaskStage.ASSIGNED);
    }

    @Test
    void nodeStageShouldFallbackToInitWhenStateIsMissing() {
        assertThat(TaskRuntimeStates.nodeStage(NodeTask.builder().build())).isEqualTo(TaskStage.INIT);
    }

    @Test
    void applyNodeStageShouldSynchronizeRuntimeAndLegacyConfigStage() {
        Instant now = Instant.now();
        NodeTask nodeTask = NodeTask.builder()
            .taskConfig(TaskConfig.builder().taskWorkStage(TaskStage.INIT).build())
            .build();

        TaskRuntimeStates.applyNodeStage(nodeTask, TaskStage.FAILED, now);

        assertThat(nodeTask.getCurrentStage()).isEqualTo(TaskStage.FAILED);
        assertThat(nodeTask.getStageUpdatedAt()).isEqualTo(now);
        assertThat(nodeTask.getTaskConfig().getTaskWorkStage()).isEqualTo(TaskStage.FAILED);
    }

    @Test
    void applyMainStageShouldSynchronizeRuntimeAndLegacyConfigStage() {
        Instant now = Instant.now();
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskConfig(TaskConfig.builder().taskWorkStage(TaskStage.INIT).build())
            .build();

        TaskRuntimeStates.applyMainStage(metadata, TaskStage.ASSIGNED, now);

        assertThat(metadata.getCurrentStage()).isEqualTo(TaskStage.ASSIGNED);
        assertThat(metadata.getStageUpdatedAt()).isEqualTo(now);
        assertThat(metadata.getTaskConfig().getTaskWorkStage()).isEqualTo(TaskStage.ASSIGNED);
    }

    @Test
    void applyMainStageShouldNotOverwriteTerminalStage() {
        Instant terminalAt = Instant.parse("2026-05-29T10:00:00Z");
        Instant later = terminalAt.plusSeconds(30);
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .currentStage(TaskStage.FAILED)
            .stageUpdatedAt(terminalAt)
            .taskConfig(TaskConfig.builder().taskWorkStage(TaskStage.FAILED).build())
            .build();

        TaskRuntimeStates.applyMainStage(metadata, TaskStage.ONGOING, later);

        assertThat(metadata.getCurrentStage()).isEqualTo(TaskStage.FAILED);
        assertThat(metadata.getStageUpdatedAt()).isEqualTo(terminalAt);
        assertThat(metadata.getTaskConfig().getTaskWorkStage()).isEqualTo(TaskStage.FAILED);
    }

    @Test
    void applyNodeStageShouldNotOverwriteTerminalStageFromLegacyConfig() {
        Instant terminalAt = Instant.parse("2026-05-29T10:00:00Z");
        Instant later = terminalAt.plusSeconds(30);
        NodeTask nodeTask = NodeTask.builder()
            .stageUpdatedAt(terminalAt)
            .taskConfig(TaskConfig.builder().taskWorkStage(TaskStage.TIMEOUT).build())
            .build();

        TaskRuntimeStates.applyNodeStage(nodeTask, TaskStage.SHUTTING, later);

        assertThat(nodeTask.getCurrentStage()).isNull();
        assertThat(nodeTask.getStageUpdatedAt()).isEqualTo(terminalAt);
        assertThat(nodeTask.getTaskConfig().getTaskWorkStage()).isEqualTo(TaskStage.TIMEOUT);
        assertThat(TaskRuntimeStates.nodeStage(nodeTask)).isEqualTo(TaskStage.TIMEOUT);
    }

    @Test
    void plannedStartShouldReadAndWriteThroughRuntimeState() {
        long plannedStartAtMs = 123456L;
        TaskInfoMetadata metadata = TaskInfoMetadata.builder()
            .taskConfig(TaskConfig.builder().build())
            .build();
        NodeTask nodeTask = NodeTask.builder()
            .taskConfig(TaskConfig.builder().build())
            .build();

        TaskRuntimeStates.applyMainPlannedStart(metadata, plannedStartAtMs);
        TaskRuntimeStates.applyNodePlannedStart(nodeTask, plannedStartAtMs);

        assertThat(TaskRuntimeStates.mainPlannedStartAtMs(metadata)).isEqualTo(plannedStartAtMs);
        assertThat(TaskRuntimeStates.nodePlannedStartAtMs(nodeTask)).isEqualTo(plannedStartAtMs);
        assertThat(TaskRuntimeStates.mainState(metadata).plannedStartAtMs()).isEqualTo(plannedStartAtMs);
        assertThat(TaskRuntimeStates.nodeState(nodeTask).plannedStartAtMs()).isEqualTo(plannedStartAtMs);
    }

    @Test
    void isRunningShouldIncludeStoppingStage() {
        assertThat(TaskRuntimeStates.isRunning(TaskStage.STARTING)).isTrue();
        assertThat(TaskRuntimeStates.isRunning(TaskStage.ONGOING)).isTrue();
        assertThat(TaskRuntimeStates.isRunning(TaskStage.SHUTTING)).isTrue();
        assertThat(TaskRuntimeStates.isRunning(TaskStage.STOPPED)).isFalse();
    }
}
