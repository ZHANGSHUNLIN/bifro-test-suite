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

package org.apache.bifromq.testsuite.worker.context;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.client.MqttClientConfigFactory;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.qps.ProfileQpsSpec;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;
import org.apache.bifromq.testsuite.worker.chaos.ChaosPolicy;
import org.apache.bifromq.testsuite.worker.ratelimit.DynamicQpsClock;
import org.apache.bifromq.testsuite.worker.ratelimit.FiniteDispatchPlan;
import org.apache.bifromq.testsuite.worker.ratelimit.IRateLimiter;
import org.apache.bifromq.testsuite.worker.topic.DefaultTopicDistributionPlanner;
import org.apache.bifromq.testsuite.worker.topic.TopicDistributionPlanner;

public record TaskExecutionContext(
    String taskId,
    TaskExecutionConfig executionConfig,
    int totalClientCount,
    int stressDurationInSec,
    int stageTimeoutInSec,
    int delayAfterStageInSec,
    int thingIdStartAt,
    int fanOut,
    int fanIn,
    IRateLimiter connectRateLimiter,
    IRateLimiter disconnectRateLimiter,
    QpsStrategy connectQpsStrategy,
    QpsStrategy disconnectQpsStrategy,
    IRateLimiter subscribeRateLimiter,
    QpsStrategy subscribeQpsStrategy,
    QpsStrategy publishQpsStrategy,
    MqttClientConfigFactory mqttClientConfigFactory,
    int nodePubCount,
    int nodeSubCount,
    int expectedPubCount,
    int expectedSubCount,
    ConcurrentHashMap<String, MqttClientTask> connClients,
    ConcurrentHashMap<String, MqttClientTask> pubClients,
    ConcurrentHashMap<String, MqttClientTask> subClients,
    Optional<ChaosPolicy> chaosPolicy
) {
    public String nodeId() {
        return executionConfig == null ? "" : executionConfig.nodeId();
    }

    public String taskTypeName() {
        return executionConfig == null || executionConfig.taskTypeName() == null ? "" : executionConfig.taskTypeName();
    }

    public String validateClientInitialization() {
        if (executionConfig == null) {
            return "Task execution config is missing";
        }
        if (totalClientCount <= 0) {
            return Messages.get("error.worker.clientCountInvalid", totalClientCount);
        }
        if (executionConfig.brokers() == null || executionConfig.brokers().isEmpty()) {
            return Messages.get("error.worker.brokerListEmpty");
        }
        for (int i = 0; i < executionConfig.brokers().size(); i++) {
            var broker = executionConfig.brokers().get(i);
            if (broker.getHost() == null || broker.getHost().isBlank()) {
                return Messages.get("error.worker.brokerAddressEmpty", i);
            }
            if (broker.getPort() <= 0 || broker.getPort() > 65535) {
                return String.format("Broker[%d] port invalid: %d, must be in range 1-65535", i, broker.getPort());
            }
        }
        return null;
    }

    public ClientTaskConfig newClientTaskConfig() {
        return mqttClientConfigFactory.clientTaskConfigBuilder(
                executionConfig.messageSize(),
                executionConfig.publishRate(),
                stressDurationInSec,
                executionConfig.mqtt5(),
                executionConfig.retain(),
                executionConfig.qos()
            ).clientImpl(executionConfig.clientImpl())
            .payloadMode(executionConfig.payloadMode())
            .payloadTemplate(executionConfig.payloadTemplate())
            .waveQpsSpec(executionConfig.clientPublishWaveQpsSpec())
            .profileQpsSpec(executionConfig.clientPublishProfileQpsSpec())
            .chaosPolicy(executionConfig.chaosPolicy())
            .publishOnConnect(executionConfig.publishOnConnect())
            .build();
    }

    public int subWorkerPoolSize() {
        return executionConfig.subWorkerPoolSize();
    }

    public TopicDistributionPlanner topicDistributionPlanner() {
        return new DefaultTopicDistributionPlanner(taskId, executionConfig, thingIdStartAt, fanOut, fanIn);
    }

    public long dynamicQpsTimeOriginMs(long localStageStartMs, String stageName) {
        return DynamicQpsClock.resolveTimeOriginMs(
            executionConfig.plannedStartAtMs(), taskId, nodeId(), localStageStartMs, stageName);
    }

    public FiniteDispatchPlan connectDispatchPlan(int totalClients) {
        if (executionConfig == null) {
            return null;
        }
        if (executionConfig.connectProfileDataPoints() != null
            && !executionConfig.connectProfileDataPoints().isEmpty()) {
            return FiniteDispatchPlan.fromProfileDataPoints(executionConfig.connectProfileDataPoints(), totalClients);
        }
        return FiniteDispatchPlan.fromWaveSpec(executionConfig.connectWaveQpsSpec(), totalClients);
    }

    public FiniteDispatchPlan disconnectDispatchPlan(int totalClients) {
        if (executionConfig == null) {
            return null;
        }
        if (executionConfig.disconnectProfileDataPoints() != null
            && !executionConfig.disconnectProfileDataPoints().isEmpty()) {
            return FiniteDispatchPlan.fromProfileDataPoints(
                executionConfig.disconnectProfileDataPoints(), totalClients);
        }
        return FiniteDispatchPlan.fromWaveSpec(executionConfig.disconnectWaveQpsSpec(), totalClients);
    }

    public WaveQpsSpec clientPublishWaveQpsSpec() {
        return executionConfig == null ? null : executionConfig.clientPublishWaveQpsSpec();
    }

    public ProfileQpsSpec clientPublishProfileQpsSpec() {
        return executionConfig == null ? null : executionConfig.clientPublishProfileQpsSpec();
    }

    public boolean hasDynamicPublishQps() {
        return publishQpsStrategy() != null && publishQpsStrategy().isDynamic();
    }

    public QpsStrategy publishQpsStrategy() {
        return publishQpsStrategy;
    }
}
