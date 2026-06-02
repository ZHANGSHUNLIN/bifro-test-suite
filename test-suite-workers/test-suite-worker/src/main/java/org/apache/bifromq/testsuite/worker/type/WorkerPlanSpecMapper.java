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

package org.apache.bifromq.testsuite.worker.type;

import org.apache.bifromq.testsuite.client.AuthStrategy;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskSpec;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionConfig;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionContext;
import org.jspecify.annotations.NonNull;

public final class WorkerPlanSpecMapper {

    private WorkerPlanSpecMapper() {
    }

    public static WorkerPlanSpec fromTaskConfig(TaskConfig config) {
        PubSubClientCountSpec clientCountSpec = PubSubClientCountSpec.fromTaskConfig(config);
        int expectedPubCount = config.getTaskType() == TaskConfig.TaskType.PUBSUB
            ? PubSubClientCountPlanner.expectedPubCount(clientCountSpec) : 0;
        int expectedSubCount = config.getTaskType() == TaskConfig.TaskType.PUBSUB
            ? PubSubClientCountPlanner.expectedSubCount(clientCountSpec) : 0;
        return new WorkerPlanSpec(
            buildExecutionContext(config, expectedPubCount, expectedSubCount),
            expectedPubCount,
            expectedSubCount,
            config.getStageTimeoutInSec()
        );
    }

    public static WorkerPlanSpec fromWorkerTaskSpec(WorkerTaskSpec spec) {
        PubSubClientCountSpec clientCountSpec = PubSubClientCountSpec.fromWorkerTaskSpec(spec);
        int expectedPubCount = spec.getTaskType() == TaskConfig.TaskType.PUBSUB
            ? PubSubClientCountPlanner.expectedPubCount(clientCountSpec) : 0;
        int expectedSubCount = spec.getTaskType() == TaskConfig.TaskType.PUBSUB
            ? PubSubClientCountPlanner.expectedSubCount(clientCountSpec) : 0;
        return new WorkerPlanSpec(
            buildExecutionContext(spec, expectedPubCount, expectedSubCount),
            expectedPubCount,
            expectedSubCount,
            spec.getStageTimeoutInSec()
        );
    }

    public static TaskExecutionContext buildExecutionContext(
        TaskConfig config,
        int expectedPubCount,
        int expectedSubCount) {
        return TaskExecutionContextMapper.fromTaskConfig(config, expectedPubCount, expectedSubCount);
    }

    public static TaskExecutionContext buildExecutionContext(
        WorkerTaskSpec spec,
        int expectedPubCount,
        int expectedSubCount) {
        return TaskExecutionContextMapper.fromWorkerTaskSpec(spec, expectedPubCount, expectedSubCount);
    }

    public static TaskExecutionConfig buildExecutionConfig(TaskConfig config) {
        return TaskExecutionConfigMapper.fromTaskConfig(config);
    }

    public static TaskExecutionConfig buildExecutionConfig(WorkerTaskSpec spec) {
        return TaskExecutionConfigMapper.fromWorkerTaskSpec(spec);
    }

    public static QpsStrategy publishQpsStrategy(TaskConfig config) {
        return PublishQpsStrategyFactory.create(config);
    }

    public static boolean hasDynamicPublishQps(TaskConfig config) {
        return PublishQpsStrategyFactory.hasDynamicPublishQps(config);
    }

    @NonNull
    public static AuthStrategy authStrategy(TaskConfig config) {
        return AuthStrategyMapper.fromTaskConfig(config);
    }
}
