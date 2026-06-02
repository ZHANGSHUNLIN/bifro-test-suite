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

package org.apache.bifromq.testsuite.app.task.config;

import java.util.ArrayList;
import java.util.List;
import org.apache.bifromq.testsuite.qps.WaveQpsStrategy;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.type.PubSubClientCountPlanner;
import org.apache.bifromq.testsuite.worker.type.PubSubClientCountSpec;

public final class NodeTaskConfigMapper {

    private static final long WAVE_PROFILE_SAMPLE_INTERVAL_MS = 100L;

    private NodeTaskConfigMapper() {
    }

    public static TaskConfig toNodeTaskConfig(TaskConfig mainTaskConfig, NodeExecutionConfig executionConfig) {
        double ratio = executionConfig.mainTotalClientCount() > 0
            ? (double) executionConfig.nodeClientCount() / executionConfig.mainTotalClientCount()
            : 1.0;
        double publishRatio = publishRatio(mainTaskConfig, executionConfig);
        List<long[]> scaledConnectDataPoints = executionConfig.preScaledConnectDataPoints() != null
            ? executionConfig.preScaledConnectDataPoints()
            : scaleProfileDataPoints(mainTaskConfig.getConnectProfileDataPoints(), ratio);
        List<long[]> scaledDisconnectDataPoints = executionConfig.preScaledDisconnectDataPoints() != null
            ? executionConfig.preScaledDisconnectDataPoints()
            : scaleProfileDataPoints(mainTaskConfig.getDisconnectProfileDataPoints(), ratio);
        List<long[]> scaledSubscribeDataPoints = executionConfig.preScaledSubscribeDataPoints() != null
            ? executionConfig.preScaledSubscribeDataPoints()
            : scaleProfileDataPoints(mainTaskConfig.getSubscribeProfileDataPoints(), ratio);
        List<long[]> scaledPublishDataPoints = executionConfig.preScaledPublishDataPoints() != null
            ? executionConfig.preScaledPublishDataPoints()
            : scaleProfileDataPoints(publishProfileDataPoints(mainTaskConfig), publishRatio);

        return TaskConfig.builder()
            .taskWorkStage(mainTaskConfig.getTaskWorkStage())
            .template(mainTaskConfig.getTemplate())
            .taskId(mainTaskConfig.getTaskId())
            .nodeId(executionConfig.nodeId())
            .taskType(mainTaskConfig.getTaskType())
            .protocol(mainTaskConfig.getProtocol())
            .localAddresses(mainTaskConfig.getLocalAddresses())
            .brokers(mainTaskConfig.getBrokers())
            .username(mainTaskConfig.getUsername())
            .password(mainTaskConfig.getPassword())
            .thingIdStartAt(mainTaskConfig.getThingIdStartAt())
            .cleanSession(mainTaskConfig.isCleanSession())
            .keepAliveInSec(mainTaskConfig.getKeepAliveInSec())
            .ackTimeoutInSec(mainTaskConfig.getAckTimeoutInSec())
            .reconnectMaxAttempts(mainTaskConfig.getReconnectMaxAttempts())
            .reconnectIntervalInMs(mainTaskConfig.getReconnectIntervalInMs())
            .connectTimeoutInMs(mainTaskConfig.getConnectTimeoutInMs())
            .maxInflightQueue(mainTaskConfig.getMaxInflightQueue())
            .totalClientCount(executionConfig.nodeClientCount())
            .fanOut(mainTaskConfig.getFanOut())
            .fanIn(mainTaskConfig.getFanIn())
            .topicsPerClient(mainTaskConfig.getTopicsPerClient())
            .nodePubCount(executionConfig.nodePubCount())
            .nodeSubCount(executionConfig.nodeSubCount())
            .topic(mainTaskConfig.getTopic())
            .qos(mainTaskConfig.getQos())
            .fixedTopic(mainTaskConfig.isFixedTopic())
            .isWildcard(mainTaskConfig.isWildcard())
            .messageSize(mainTaskConfig.getMessageSize())
            .publishRate(scaledPublishRate(mainTaskConfig, publishRatio))
            .stressDurationInSec(mainTaskConfig.getStressDurationInSec())
            .stageTimeoutInSec(mainTaskConfig.getStageTimeoutInSec())
            .delayAfterStageInSec(mainTaskConfig.getDelayAfterStageInSec())
            .retain(mainTaskConfig.isRetain())
            .isMqtt5(mainTaskConfig.isMqtt5())
            .authType(mainTaskConfig.getAuthType())
            .isEmptyClientId(mainTaskConfig.isEmptyClientId())
            .expiryIntervalInSec(mainTaskConfig.getExpiryIntervalInSec())
            .connectRate((int) Math.max(1, Math.round(mainTaskConfig.getConnectRate() * ratio)))
            .disconnectRate((int) Math.max(1, Math.round(mainTaskConfig.getDisconnectRate() * ratio)))
            .enableAutoMultiAddress(mainTaskConfig.isEnableAutoMultiAddress())
            .localPortRangeConfig(mainTaskConfig.getLocalPortRangeConfig())
            .group(mainTaskConfig.getGroup())
            .willConfig(mainTaskConfig.getWillConfig())
            .chaosPolicy(mainTaskConfig.getChaosPolicy())
            .waveQpsSpec(mainTaskConfig.getWaveQpsSpec())
            .qpsMode(mainTaskConfig.getQpsMode())
            .profileConfig(mainTaskConfig.getProfileConfig())
            .publishProfileDataPoints(scaledPublishDataPoints)
            .connectProfileId(mainTaskConfig.getConnectProfileId())
            .connectProfileDataPoints(scaledConnectDataPoints)
            .disconnectProfileId(mainTaskConfig.getDisconnectProfileId())
            .disconnectProfileDataPoints(scaledDisconnectDataPoints)
            .subscribeQpsMode(mainTaskConfig.getSubscribeQpsMode())
            .subscribeRate((int) Math.max(0, Math.round(mainTaskConfig.getSubscribeRate() * ratio)))
            .subscribeProfileId(mainTaskConfig.getSubscribeProfileId())
            .subscribeProfileDataPoints(scaledSubscribeDataPoints)
            .payloadMode(mainTaskConfig.getPayloadMode())
            .payloadTemplate(mainTaskConfig.getPayloadTemplate())
            .clientImpl(mainTaskConfig.getClientImpl())
            .build();
    }

    public static List<long[]> publishProfileDataPoints(TaskConfig taskConfig) {
        if (taskConfig == null) {
            return null;
        }
        if (taskConfig.getQpsMode() == TaskConfig.QpsMode.DYNAMIC
            && taskConfig.getProfileConfig() != null
            && taskConfig.getProfileConfig().getDataPoints() != null
            && !taskConfig.getProfileConfig().getDataPoints().isEmpty()) {
            return taskConfig.getProfileConfig().getDataPoints();
        }
        if (taskConfig.getWaveQpsSpec() != null) {
            WaveQpsStrategy strategy = new WaveQpsStrategy(taskConfig.getWaveQpsSpec());
            long durationMs = taskConfig.getWaveQpsSpec().getTotalDurationMs();
            List<long[]> points = new ArrayList<>();
            for (long t = 0; t <= durationMs; t += WAVE_PROFILE_SAMPLE_INTERVAL_MS) {
                points.add(new long[] {t, strategy.currentQps(t)});
            }
            if (points.isEmpty() || points.get(points.size() - 1)[0] != durationMs) {
                points.add(new long[] {durationMs, strategy.currentQps(durationMs)});
            }
            return points;
        }
        return null;
    }

    private static List<long[]> scaleProfileDataPoints(List<long[]> dataPoints, double ratio) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return dataPoints;
        }
        List<long[]> scaled = new ArrayList<>(dataPoints.size());
        for (long[] pt : dataPoints) {
            scaled.add(new long[] {pt[0], Math.max(0, Math.round(pt[1] * ratio))});
        }
        return scaled;
    }

    private static double publishRatio(TaskConfig mainTaskConfig, NodeExecutionConfig executionConfig) {
        int globalPubCount = PubSubClientCountPlanner.expectedPubCount(
            PubSubClientCountSpec.fromTaskConfig(mainTaskConfig));
        if (globalPubCount <= 0) {
            return 0D;
        }
        return Math.min(1D, Math.max(0D, executionConfig.nodePubCount() / (double) globalPubCount));
    }

    private static double scaledPublishRate(TaskConfig mainTaskConfig, double publishRatio) {
        if (publishRatio <= 0D) {
            return mainTaskConfig.getPublishRate();
        }
        return mainTaskConfig.getPublishRate() * publishRatio;
    }
}
