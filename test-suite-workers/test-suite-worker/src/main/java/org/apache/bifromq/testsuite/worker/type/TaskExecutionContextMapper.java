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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.bifromq.testsuite.worker.RateLimiterType;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskSpec;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionContext;
import org.apache.bifromq.testsuite.worker.ratelimit.IRateLimiter;

public final class TaskExecutionContextMapper {

    private TaskExecutionContextMapper() {
    }

    public static TaskExecutionContext fromTaskConfig(
        TaskConfig config,
        int expectedPubCount,
        int expectedSubCount) {
        double effectiveSubscribeClientRate = TaskRateStrategyFactory.effectiveSubscribeClientRate(
            config.getSubscribeRate(), config.getConnectRate(), config.getTopicsPerClient());

        return new TaskExecutionContext(
            config.getTaskId(),
            TaskExecutionConfigMapper.fromTaskConfig(config),
            config.getTotalClientCount(),
            config.getStressDurationInSec(),
            config.getStageTimeoutInSec(),
            config.getDelayAfterStageInSec(),
            config.getThingIdStartAt(),
            config.getFanOut(),
            config.getFanIn(),
            IRateLimiter.create(toRuntimeRateLimiterType(config.getRateLimiterType()), config.getConnectRate()),
            IRateLimiter.create(toRuntimeRateLimiterType(config.getRateLimiterType()), config.getDisconnectRate()),
            TaskRateStrategyFactory.connect(config),
            TaskRateStrategyFactory.disconnect(config),
            IRateLimiter.create(toRuntimeRateLimiterType(config.getRateLimiterType()), effectiveSubscribeClientRate),
            TaskRateStrategyFactory.subscribe(config),
            TaskRateStrategyFactory.publish(config, expectedPubCount),
            TaskMqttClientConfigFactoryMapper.fromTaskConfig(config),
            config.getNodePubCount(),
            config.getNodeSubCount(),
            expectedPubCount,
            expectedSubCount,
            new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>(),
            Optional.empty()
        );
    }

    public static TaskExecutionContext fromWorkerTaskSpec(
        WorkerTaskSpec spec,
        int expectedPubCount,
        int expectedSubCount) {
        double effectiveSubscribeClientRate = TaskRateStrategyFactory.effectiveSubscribeClientRate(
            spec.getSubscribeRate(), spec.getConnectRate(), spec.getTopicsPerClient());

        return new TaskExecutionContext(
            spec.getTaskId(),
            TaskExecutionConfigMapper.fromWorkerTaskSpec(spec),
            spec.getTotalClientCount(),
            spec.getStressDurationInSec(),
            spec.getStageTimeoutInSec(),
            spec.getDelayAfterStageInSec(),
            spec.getThingIdStartAt(),
            spec.getFanOut(),
            spec.getFanIn(),
            IRateLimiter.create(toRuntimeRateLimiterType(spec.getRateLimiterType()), spec.getConnectRate()),
            IRateLimiter.create(toRuntimeRateLimiterType(spec.getRateLimiterType()), spec.getDisconnectRate()),
            TaskRateStrategyFactory.connect(spec),
            TaskRateStrategyFactory.disconnect(spec),
            IRateLimiter.create(toRuntimeRateLimiterType(spec.getRateLimiterType()), effectiveSubscribeClientRate),
            TaskRateStrategyFactory.subscribe(spec),
            TaskRateStrategyFactory.publish(spec, expectedPubCount),
            TaskMqttClientConfigFactoryMapper.fromWorkerTaskSpec(spec),
            spec.getNodePubCount(),
            spec.getNodeSubCount(),
            expectedPubCount,
            expectedSubCount,
            new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>(),
            Optional.empty()
        );
    }

    private static IRateLimiter.Type toRuntimeRateLimiterType(RateLimiterType type) {
        return switch (type == null ? RateLimiterType.GUAVA : type) {
            case GUAVA -> IRateLimiter.Type.GUAVA;
        };
    }
}
