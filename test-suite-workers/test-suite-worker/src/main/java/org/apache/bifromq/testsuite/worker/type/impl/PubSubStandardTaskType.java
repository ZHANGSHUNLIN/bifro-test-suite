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
import java.util.ArrayList;
import java.util.List;
import org.apache.bifromq.testsuite.Constants;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.statemachine.TaskStateMachineConfig;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;
import org.apache.bifromq.testsuite.worker.pipeline.stages.CleanupConnStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.ErrorHandlingStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.InitPubClientsStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.InitSubClientsStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.StartConnClientsStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.StartPubSubClientsStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.StartSubscribingStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.StressStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.TaskFinishEventStage;
import org.apache.bifromq.testsuite.worker.pipeline.stages.WaitForStageTimeoutStage;
import org.apache.bifromq.testsuite.worker.type.ExecutionPlan;
import org.apache.bifromq.testsuite.worker.type.TaskType;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpec;

public class PubSubStandardTaskType implements TaskType {

    @Override
    public String typeId() {
        return "mqtt.pubsub.standard";
    }

    @Override
    public ExecutionPlan buildPlan(WorkerPlanSpec spec, Vertx vertx) {
        int expectedPubCount = spec.expectedPubCount();
        int expectedSubCount = spec.expectedSubCount();
        boolean hasPubClients = expectedPubCount > 0;
        boolean hasSubClients = expectedSubCount > 0;

        List<PipelineStage<TaskPipelineContext>> stages = new ArrayList<>();

        if (hasPubClients) {
            stages.add(new InitPubClientsStage());
        }

        if (hasSubClients) {
            stages.add(new InitSubClientsStage());
        }

        if (hasPubClients && hasSubClients) {
            stages.add(new StartConnClientsStage(Constants.CONN_CLIENT_TAG));
            stages.add(new StartSubscribingStage());
        } else if (hasPubClients) {
            stages.add(new StartConnClientsStage(Constants.PUB_CLIENT_TAG));
        } else if (hasSubClients) {
            stages.add(new StartConnClientsStage(Constants.SUB_CLIENT_TAG));
            stages.add(new StartSubscribingStage());
        }

        stages.add(new WaitForStageTimeoutStage(spec.stageTimeoutInSec()));

        stages.add(new StartPubSubClientsStage(vertx));
        stages.add(new StressStage(vertx));

        if (hasPubClients) {
            stages.add(new CleanupConnStage(Constants.PUB_CLIENT_TAG));
        }

        if (hasSubClients) {
            stages.add(new CleanupConnStage(Constants.SUB_CLIENT_TAG));
        }

        stages.add(new TaskFinishEventStage());
        stages.add(new ErrorHandlingStage());

        return new ExecutionPlan(stages, TaskStateMachineConfig.create(TaskStage.ASSIGNED), spec.executionContext());
    }
}
