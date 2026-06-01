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

package org.apache.bifromq.testsuite.worker.type.impl;

import io.vertx.core.Vertx;
import java.util.List;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.statemachine.TaskStateMachineConfig;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;
import org.apache.bifromq.testsuite.worker.pipeline.stages.ErrorHandlingStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.ExecuteChaosStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.InitChaosClientsStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.TaskFinishEventStage;
import org.apache.bifromq.testsuite.worker.type.ExecutionPlan;
import org.apache.bifromq.testsuite.worker.type.TaskType;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpec;

public class ChaosStandardTaskType implements TaskType {

    @Override
    public String typeId() {
        return "mqtt.chaos.standard";
    }

    @Override
    public ExecutionPlan buildPlan(WorkerPlanSpec spec, Vertx vertx) {
        List<PipelineStage<TaskPipelineContext>> stages = List.of(
            new InitChaosClientsStage(),
            new ExecuteChaosStage(),
            new TaskFinishEventStage(),
            new ErrorHandlingStage()
        );

        return new ExecutionPlan(stages, TaskStateMachineConfig.create(TaskStage.ASSIGNED), spec.executionContext());
    }
}
