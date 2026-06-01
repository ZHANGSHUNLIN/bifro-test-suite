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

import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskSpec;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionConfig;

public final class TaskExecutionConfigMapper {

    private TaskExecutionConfigMapper() {
    }

    public static TaskExecutionConfig fromTaskConfig(TaskConfig config) {
        boolean dynamicPublishQps = PublishQpsStrategyFactory.hasDynamicPublishQps(config);
        return new TaskExecutionConfig(
            config.getTaskId(),
            config.getNodeId(),
            config.getTaskType() == null ? "" : config.getTaskType().name(),
            config.getTemplate(),
            config.getBrokers(),
            config.getMessageSize(),
            config.getPublishRate(),
            config.isMqtt5(),
            config.isRetain(),
            config.getQos(),
            config.getClientImpl(),
            config.getPayloadMode(),
            config.getPayloadTemplate(),
            dynamicPublishQps ? null : config.getWaveQpsSpec(),
            dynamicPublishQps ? null : PublishQpsStrategyFactory.clientPublishProfileQpsSpec(config),
            config.getChaosPolicy(),
            config.getTemplate() == TaskTemplate.CONN_PUBLISH_ON_CONNECT,
            config.getTopic(),
            config.getTopicsPerClient() > 0 ? config.getTopicsPerClient() : 1,
            config.isWildcard(),
            config.getSubWorkerPoolSize(),
            null,
            config.getConnectProfileDataPoints(),
            config.getConnectWaveQpsSpec(),
            config.getDisconnectProfileDataPoints(),
            config.getDisconnectWaveQpsSpec(),
            config.getSubscribeProfileDataPoints()
        );
    }

    public static TaskExecutionConfig fromWorkerTaskSpec(WorkerTaskSpec spec) {
        boolean dynamicPublishQps = PublishQpsStrategyFactory.hasDynamicPublishQps(spec);
        return new TaskExecutionConfig(
            spec.getTaskId(),
            spec.getNodeId(),
            spec.getTaskType() == null ? "" : spec.getTaskType().name(),
            spec.getTemplate(),
            spec.getBrokers(),
            spec.getMessageSize(),
            spec.getPublishRate(),
            spec.isMqtt5(),
            spec.isRetain(),
            spec.getQos(),
            spec.getClientImpl(),
            spec.getPayloadMode(),
            spec.getPayloadTemplate(),
            dynamicPublishQps ? null : spec.getWaveQpsSpec(),
            dynamicPublishQps ? null : PublishQpsStrategyFactory.clientPublishProfileQpsSpec(spec),
            spec.getChaosPolicy(),
            spec.getTemplate() == TaskTemplate.CONN_PUBLISH_ON_CONNECT,
            spec.getTopic(),
            spec.getTopicsPerClient() > 0 ? spec.getTopicsPerClient() : 1,
            spec.isWildcard(),
            spec.getSubWorkerPoolSize(),
            spec.getPlannedStartAtMs(),
            spec.getConnectProfileDataPoints(),
            spec.getConnectWaveQpsSpec(),
            spec.getDisconnectProfileDataPoints(),
            spec.getDisconnectWaveQpsSpec(),
            spec.getSubscribeProfileDataPoints()
        );
    }
}
