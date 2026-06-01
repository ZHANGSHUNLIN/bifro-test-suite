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

import java.time.Instant;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.app.database.pojo.NodeTask;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.worker.TaskConfig;

public final class TaskRuntimeStates {

    private TaskRuntimeStates() {
    }

    public static TaskRuntimeState mainState(TaskInfoMetadata metadata) {
        if (metadata == null) {
            return TaskRuntimeState.builder().stage(TaskStage.INIT).build();
        }
        return TaskRuntimeState.builder()
            .stage(resolveStage(metadata.getCurrentStage(), metadata.getTaskConfig()))
            .stageUpdatedAt(metadata.getStageUpdatedAt())
            .plannedStartAtMs(metadata.getPlannedStartAtMs())
            .build();
    }

    public static TaskRuntimeState nodeState(NodeTask nodeTask) {
        if (nodeTask == null) {
            return TaskRuntimeState.builder().stage(TaskStage.INIT).build();
        }
        return TaskRuntimeState.builder()
            .stage(resolveStage(nodeTask.getCurrentStage(), nodeTask.getTaskConfig()))
            .stageUpdatedAt(nodeTask.getStageUpdatedAt())
            .plannedStartAtMs(nodeTask.getPlannedStartAtMs())
            .build();
    }

    public static TaskStage mainStage(TaskInfoMetadata metadata) {
        return mainState(metadata).stage();
    }

    public static TaskStage nodeStage(NodeTask nodeTask) {
        return nodeState(nodeTask).stage();
    }

    public static Long mainPlannedStartAtMs(TaskInfoMetadata metadata) {
        return mainState(metadata).plannedStartAtMs();
    }

    public static Long nodePlannedStartAtMs(NodeTask nodeTask) {
        return nodeState(nodeTask).plannedStartAtMs();
    }

    public static boolean isTerminal(TaskStage stage) {
        return stage == TaskStage.SHUTDOWN
            || stage == TaskStage.STOPPED
            || stage == TaskStage.FAILED
            || stage == TaskStage.TIMEOUT;
    }

    public static boolean isRunning(TaskStage stage) {
        return stage == TaskStage.ONGOING
            || stage == TaskStage.STARTING
            || stage == TaskStage.SHUTTING;
    }

    public static void applyMainStage(TaskInfoMetadata metadata, TaskStage stage, Instant updatedAt) {
        if (metadata == null || stage == null) {
            return;
        }
        TaskStage currentStage = resolveStage(metadata.getCurrentStage(), metadata.getTaskConfig());
        if (isLockedTerminalStage(currentStage, stage)) {
            return;
        }
        metadata.setCurrentStage(stage);
        metadata.setStageUpdatedAt(updatedAt);
        if (metadata.getTaskConfig() != null) {
            metadata.getTaskConfig().setTaskWorkStage(stage);
        }
    }

    public static void applyNodeStage(NodeTask nodeTask, TaskStage stage, Instant updatedAt) {
        if (nodeTask == null || stage == null) {
            return;
        }
        TaskStage currentStage = resolveStage(nodeTask.getCurrentStage(), nodeTask.getTaskConfig());
        if (isLockedTerminalStage(currentStage, stage)) {
            return;
        }
        nodeTask.setCurrentStage(stage);
        nodeTask.setStageUpdatedAt(updatedAt);
        if (nodeTask.getTaskConfig() != null) {
            nodeTask.getTaskConfig().setTaskWorkStage(stage);
        }
    }

    public static void applyMainPlannedStart(TaskInfoMetadata metadata, long plannedStartAtMs) {
        if (metadata == null) {
            return;
        }
        metadata.setPlannedStartAtMs(plannedStartAtMs);
    }

    public static void applyNodePlannedStart(NodeTask nodeTask, long plannedStartAtMs) {
        if (nodeTask == null) {
            return;
        }
        nodeTask.setPlannedStartAtMs(plannedStartAtMs);
    }

    private static TaskStage resolveStage(TaskStage currentStage, TaskConfig taskConfig) {
        if (currentStage != null) {
            return currentStage;
        }
        if (taskConfig != null && taskConfig.getTaskWorkStage() != null) {
            return taskConfig.getTaskWorkStage();
        }
        return TaskStage.INIT;
    }

    private static boolean isLockedTerminalStage(TaskStage currentStage, TaskStage nextStage) {
        return isTerminal(currentStage) && currentStage != nextStage;
    }

}
