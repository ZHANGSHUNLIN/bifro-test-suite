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

import java.util.List;
import org.apache.bifromq.testsuite.qps.ProfileQpsSpec;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskSpec;

public final class TaskRateStrategyFactory {

    private TaskRateStrategyFactory() {
    }

    public static QpsStrategy connect(TaskConfig config) {
        return profileOrWave(config.getConnectProfileDataPoints(), config.getConnectWaveQpsSpec(),
            config.getConnectRate());
    }

    public static QpsStrategy connect(WorkerTaskSpec spec) {
        return profileOrWave(spec.getConnectProfileDataPoints(), spec.getConnectWaveQpsSpec(),
            spec.getConnectRate());
    }

    public static QpsStrategy disconnect(TaskConfig config) {
        return profileOrWave(config.getDisconnectProfileDataPoints(), config.getDisconnectWaveQpsSpec(),
            config.getDisconnectRate());
    }

    public static QpsStrategy disconnect(WorkerTaskSpec spec) {
        return profileOrWave(spec.getDisconnectProfileDataPoints(), spec.getDisconnectWaveQpsSpec(),
            spec.getDisconnectRate());
    }

    public static QpsStrategy subscribe(TaskConfig config) {
        int fallbackRate = roundedSubscribeClientRate(
            config.getSubscribeRate(), config.getConnectRate(), config.getTopicsPerClient());
        return profileOrFixed(config.getSubscribeProfileDataPoints(), fallbackRate, config.getTopicsPerClient());
    }

    public static QpsStrategy subscribe(WorkerTaskSpec spec) {
        int fallbackRate = roundedSubscribeClientRate(
            spec.getSubscribeRate(), spec.getConnectRate(), spec.getTopicsPerClient());
        return profileOrFixed(spec.getSubscribeProfileDataPoints(), fallbackRate, spec.getTopicsPerClient());
    }

    public static QpsStrategy publish(TaskConfig config) {
        return publish(config, PubSubClientCountPlanner.expectedPubCount(PubSubClientCountSpec.fromTaskConfig(config)));
    }

    public static QpsStrategy publish(TaskConfig config, int publisherClientCount) {
        if (hasPoints(config.getPublishProfileDataPoints())) {
            return QpsStrategy.fromProfileDataPoints(config.getPublishProfileDataPoints(), 1,
                publishProfileEndBehavior(config));
        }
        if (config.getQpsMode() == TaskConfig.QpsMode.DYNAMIC
            && config.getProfileConfig() != null
            && hasPoints(config.getProfileConfig().getDataPoints())) {
            return QpsStrategy.fromProfileDataPoints(config.getProfileConfig().getDataPoints(), 1,
                publishProfileEndBehavior(config));
        }
        if (config.getWaveQpsSpec() != null) {
            return QpsStrategy.fromWaveSpec(config.getWaveQpsSpec(), 1);
        }
        return QpsStrategy.fixed(fixedPublishQps(config.getPublishRate(), publisherClientCount));
    }

    public static QpsStrategy publish(WorkerTaskSpec spec) {
        return publish(spec, PubSubClientCountPlanner.expectedPubCount(PubSubClientCountSpec.fromWorkerTaskSpec(spec)));
    }

    public static QpsStrategy publish(WorkerTaskSpec spec, int publisherClientCount) {
        if (hasPoints(spec.getPublishProfileDataPoints())) {
            return QpsStrategy.fromProfileDataPoints(spec.getPublishProfileDataPoints(), 1,
                publishProfileEndBehavior(spec));
        }
        if (spec.getQpsMode() == TaskConfig.QpsMode.DYNAMIC
            && spec.getProfileConfig() != null
            && hasPoints(spec.getProfileConfig().getDataPoints())) {
            return QpsStrategy.fromProfileDataPoints(spec.getProfileConfig().getDataPoints(), 1,
                publishProfileEndBehavior(spec));
        }
        if (spec.getWaveQpsSpec() != null) {
            return QpsStrategy.fromWaveSpec(spec.getWaveQpsSpec(), 1);
        }
        return QpsStrategy.fixed(fixedPublishQps(spec.getPublishRate(), publisherClientCount));
    }

    public static int effectiveSubscribeRate(int subscribeRate, int connectRate) {
        return subscribeRate > 0 ? subscribeRate : connectRate;
    }

    public static double effectiveSubscribeClientRate(int subscribeRate, int connectRate, int topicsPerClient) {
        int topicFilterRate = effectiveSubscribeRate(subscribeRate, connectRate);
        return topicFilterRate / (double) effectiveTopicsPerClient(topicsPerClient);
    }

    public static int roundedSubscribeClientRate(int subscribeRate, int connectRate, int topicsPerClient) {
        return Math.max(1, (int) Math.round(
            effectiveSubscribeClientRate(subscribeRate, connectRate, topicsPerClient)));
    }

    private static QpsStrategy profileOrWave(List<long[]> dataPoints,
                                             org.apache.bifromq.testsuite.qps.WaveQpsSpec waveQpsSpec,
                                             int fallbackRate) {
        if (hasPoints(dataPoints)) {
            return QpsStrategy.fromProfileDataPoints(dataPoints, fallbackRate);
        }
        return QpsStrategy.fromWaveSpec(waveQpsSpec, fallbackRate);
    }

    private static QpsStrategy profileOrFixed(List<long[]> dataPoints, int fallbackRate) {
        if (hasPoints(dataPoints)) {
            return QpsStrategy.fromProfileDataPoints(dataPoints, fallbackRate);
        }
        return QpsStrategy.fixed(fallbackRate);
    }

    private static QpsStrategy profileOrFixed(List<long[]> dataPoints, int fallbackRate, int topicsPerClient) {
        if (hasPoints(dataPoints)) {
            return new org.apache.bifromq.testsuite.qps.DataDrivenQpsStrategy(
                ProfileQpsSpec.builder()
                    .dataPoints(dataPoints)
                    .totalDurationMs(dataPoints.get(dataPoints.size() - 1)[0])
                    .endBehavior(ProfileQpsSpec.EndBehavior.HOLD)
                    .build(),
                false,
                effectiveTopicsPerClient(topicsPerClient));
        }
        return QpsStrategy.fixed(fallbackRate);
    }

    private static boolean hasPoints(List<long[]> dataPoints) {
        return dataPoints != null && !dataPoints.isEmpty();
    }

    private static int effectiveTopicsPerClient(int topicsPerClient) {
        return Math.max(1, topicsPerClient);
    }

    private static double fixedPublishQps(double publishRate, int publisherClientCount) {
        if (!Double.isFinite(publishRate) || publishRate <= 0) {
            throw new IllegalArgumentException("publishRate must be positive: " + publishRate);
        }
        return publishRate;
    }

    private static ProfileQpsSpec.EndBehavior publishProfileEndBehavior(TaskConfig config) {
        return config.getProfileConfig() != null ? config.getProfileConfig().getEndBehavior() : null;
    }

    private static ProfileQpsSpec.EndBehavior publishProfileEndBehavior(WorkerTaskSpec spec) {
        return spec.getProfileConfig() != null ? spec.getProfileConfig().getEndBehavior() : null;
    }
}
