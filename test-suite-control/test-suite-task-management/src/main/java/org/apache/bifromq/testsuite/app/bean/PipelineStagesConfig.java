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

import org.apache.bifromq.testsuite.i18n.Messages;

import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PipelineStagesConfig {

    private static final List<PipelineStageInfo> CONN_STAGES = List.of(
        new PipelineStageInfo("INIT_CLIENT", Messages.get("pipeline.stage.initClient")),
        new PipelineStageInfo("ONGOING", Messages.get("pipeline.stage.stress")),
        new PipelineStageInfo("SHUTTING", Messages.get("pipeline.stage.cleanup")),
        new PipelineStageInfo("SHUTDOWN", Messages.get("pipeline.stage.complete"))
    );

    private static final List<PipelineStageInfo> PUBSUB_STAGES = List.of(
        new PipelineStageInfo("INIT_PUB_CLIENT", Messages.get("pipeline.stage.initPub")),
        new PipelineStageInfo("INIT_SUB_CLIENT", Messages.get("pipeline.stage.initSub")),
        new PipelineStageInfo("PUB_CLIENT_CONN", Messages.get("pipeline.stage.buildPubConn")),
        new PipelineStageInfo("SUB_CLIENT_CONN", Messages.get("pipeline.stage.buildSubConn")),
        new PipelineStageInfo("SUBSCRIBE_CLIENT", Messages.get("pipeline.stage.startSubscribing")),
        new PipelineStageInfo("PUB_SUB_CLIENT_READY", Messages.get("pipeline.stage.waitReady")),
        new PipelineStageInfo("PUB_SUB_CLIENT_START", Messages.get("pipeline.stage.startPubSub")),
        new PipelineStageInfo("ONGOING", Messages.get("pipeline.stage.stress")),
        new PipelineStageInfo("SHUTTING", Messages.get("pipeline.stage.cleanup")),
        new PipelineStageInfo("SHUTDOWN", Messages.get("pipeline.stage.complete"))
    );

    private static final List<PipelineStageInfo> PUBSUB_PUB_ONLY_STAGES = List.of(
        new PipelineStageInfo("INIT_PUB_CLIENT", Messages.get("pipeline.stage.initPub")),
        new PipelineStageInfo("PUB_CLIENT_CONN", Messages.get("pipeline.stage.buildPubConn")),
        new PipelineStageInfo("PUB_SUB_CLIENT_READY", Messages.get("pipeline.stage.waitReady")),
        new PipelineStageInfo("PUB_SUB_CLIENT_START", Messages.get("pipeline.stage.startPubSub")),
        new PipelineStageInfo("ONGOING", Messages.get("pipeline.stage.stress")),
        new PipelineStageInfo("SHUTTING", Messages.get("pipeline.stage.cleanup")),
        new PipelineStageInfo("SHUTDOWN", Messages.get("pipeline.stage.complete"))
    );

    private static final List<PipelineStageInfo> PUBSUB_SUB_ONLY_STAGES = List.of(
        new PipelineStageInfo("INIT_SUB_CLIENT", Messages.get("pipeline.stage.initSub")),
        new PipelineStageInfo("SUB_CLIENT_CONN", Messages.get("pipeline.stage.buildSubConn")),
        new PipelineStageInfo("SUBSCRIBE_CLIENT", Messages.get("pipeline.stage.startSubscribing")),
        new PipelineStageInfo("PUB_SUB_CLIENT_READY", Messages.get("pipeline.stage.waitReady")),
        new PipelineStageInfo("ONGOING", Messages.get("pipeline.stage.stress")),
        new PipelineStageInfo("SHUTTING", Messages.get("pipeline.stage.cleanup")),
        new PipelineStageInfo("SHUTDOWN", Messages.get("pipeline.stage.complete"))
    );

    private static final Map<String, Integer> CONN_STAGE_INDEX = Map.ofEntries(
        Map.entry("INIT", 0),
        Map.entry("ASSIGNED", 0),
        Map.entry("START", 0),
        Map.entry("INIT_CLIENT", 0),
        Map.entry("ONGOING", 1),
        Map.entry("SHUTTING", 2),
        Map.entry("SHUTDOWN", 3),
        Map.entry("STOPPED", 3),
        Map.entry("FAILED", 3),
        Map.entry("TIMEOUT", 3)
    );

    private static final Map<String, Integer> PUBSUB_STAGE_INDEX = Map.ofEntries(
        Map.entry("INIT", 0),
        Map.entry("ASSIGNED", 0),
        Map.entry("START", 0),
        Map.entry("INIT_PUB_CLIENT", 0),
        Map.entry("INIT_SUB_CLIENT", 1),
        Map.entry("PUB_CLIENT_CONN", 2),
        Map.entry("SUB_CLIENT_CONN", 3),
        Map.entry("SUBSCRIBE_CLIENT", 4),
        Map.entry("PUB_SUB_CLIENT_READY", 5),
        Map.entry("PUB_SUB_CLIENT_START", 6),
        Map.entry("CONN_CLIENT", 3),
        Map.entry("ONGOING", 7),
        Map.entry("SHUTTING", 8),
        Map.entry("SHUTDOWN", 9),
        Map.entry("STOPPED", 9),
        Map.entry("FAILED", 9),
        Map.entry("TIMEOUT", 9)
    );

    private static final Map<String, Integer> PUBSUB_PUB_ONLY_STAGE_INDEX = Map.ofEntries(
        Map.entry("INIT", 0),
        Map.entry("ASSIGNED", 0),
        Map.entry("START", 0),
        Map.entry("INIT_PUB_CLIENT", 0),
        Map.entry("INIT_SUB_CLIENT", 0),
        Map.entry("PUB_CLIENT_CONN", 1),
        Map.entry("PUB_SUB_CLIENT_READY", 2),
        Map.entry("PUB_SUB_CLIENT_START", 3),
        Map.entry("CONN_CLIENT", 1),
        Map.entry("SUB_CLIENT_CONN", 1),
        Map.entry("ONGOING", 4),
        Map.entry("SHUTTING", 5),
        Map.entry("SHUTDOWN", 6),
        Map.entry("STOPPED", 6),
        Map.entry("FAILED", 6),
        Map.entry("TIMEOUT", 6)
    );

    private static final Map<String, Integer> PUBSUB_SUB_ONLY_STAGE_INDEX = Map.ofEntries(
        Map.entry("INIT", 0),
        Map.entry("ASSIGNED", 0),
        Map.entry("START", 0),
        Map.entry("INIT_SUB_CLIENT", 0),
        Map.entry("SUB_CLIENT_CONN", 1),
        Map.entry("SUBSCRIBE_CLIENT", 2),
        Map.entry("PUB_SUB_CLIENT_READY", 3),
        Map.entry("PUB_SUB_CLIENT_START", 3),
        Map.entry("CONN_CLIENT", 1),
        Map.entry("PUB_CLIENT_CONN", 1),
        Map.entry("ONGOING", 4),
        Map.entry("SHUTTING", 5),
        Map.entry("SHUTDOWN", 6),
        Map.entry("STOPPED", 6),
        Map.entry("FAILED", 6),
        Map.entry("TIMEOUT", 6)
    );

    public List<PipelineStageInfo> getStages(TaskConfig.TaskType taskType) {
        return taskType == TaskConfig.TaskType.CONN ? CONN_STAGES : PUBSUB_STAGES;
    }

    public List<PipelineStageInfo> getStages(String taskType) {
        return "CONN".equals(taskType) ? CONN_STAGES : PUBSUB_STAGES;
    }

    public List<PipelineStageInfo> getStages(TaskTemplate template) {
        if (template == TaskTemplate.PUBSUB_PUB_ONLY) {
            return PUBSUB_PUB_ONLY_STAGES;
        }
        if (template == TaskTemplate.PUBSUB_SUB_ONLY) {
            return PUBSUB_SUB_ONLY_STAGES;
        }
        if (template != null && template.name().startsWith("CONN")) {
            return CONN_STAGES;
        }
        return PUBSUB_STAGES;
    }

    public int getStageIndex(TaskConfig.TaskType taskType, TaskStage currentStage) {
        Map<String, Integer> indexMap = taskType == TaskConfig.TaskType.CONN
            ? CONN_STAGE_INDEX : PUBSUB_STAGE_INDEX;
        return indexMap.getOrDefault(currentStage.name(), 0);
    }

    public int getStageIndex(String taskType, String currentStage) {
        Map<String, Integer> indexMap = "CONN".equals(taskType)
            ? CONN_STAGE_INDEX : PUBSUB_STAGE_INDEX;
        return indexMap.getOrDefault(currentStage, 0);
    }

    public int getStageIndex(TaskTemplate template, String currentStage) {
        Map<String, Integer> indexMap;
        if (template == TaskTemplate.PUBSUB_PUB_ONLY) {
            indexMap = PUBSUB_PUB_ONLY_STAGE_INDEX;
        } else if (template == TaskTemplate.PUBSUB_SUB_ONLY) {
            indexMap = PUBSUB_SUB_ONLY_STAGE_INDEX;
        } else if (template != null && template.name().startsWith("CONN")) {
            indexMap = CONN_STAGE_INDEX;
        } else {
            indexMap = PUBSUB_STAGE_INDEX;
        }
        return indexMap.getOrDefault(currentStage, 0);
    }

    public boolean isTerminalStage(String stage) {
        return "SHUTDOWN".equals(stage) || "STOPPED".equals(stage)
            || "FAILED".equals(stage) || "TIMEOUT".equals(stage);
    }
}
