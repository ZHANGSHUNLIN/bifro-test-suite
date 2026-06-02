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

package org.apache.bifromq.testsuite.qps;

public interface QpsStrategy {
    static QpsStrategy fixed(int qps) {
        return new FixedQpsStrategy(qps);
    }

    static QpsStrategy fixed(double qps) {
        return new FixedQpsStrategy(qps);
    }

    static QpsStrategy fromWaveSpec(WaveQpsSpec spec, int fallbackQps) {
        if (spec == null || spec.getComponents() == null || spec.getComponents().isEmpty()) {
            return fixed(Math.max(1, fallbackQps));
        }
        return new WaveQpsStrategy(spec);
    }

    static QpsStrategy fromProfileDataPoints(java.util.List<long[]> dataPoints, int fallbackQps) {
        return fromProfileDataPoints(dataPoints, fallbackQps, ProfileQpsSpec.EndBehavior.HOLD);
    }

    static QpsStrategy fromProfileDataPoints(java.util.List<long[]> dataPoints, int fallbackQps,
                                             ProfileQpsSpec.EndBehavior endBehavior) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return fixed(Math.max(1, fallbackQps));
        }
        ProfileQpsSpec.EndBehavior effectiveEndBehavior =
            endBehavior != null ? endBehavior : ProfileQpsSpec.EndBehavior.HOLD;
        return new DataDrivenQpsStrategy(ProfileQpsSpec.builder()
            .dataPoints(dataPoints)
            .totalDurationMs(dataPoints.get(dataPoints.size() - 1)[0])
            .endBehavior(effectiveEndBehavior)
            .build(), false);
    }

    int currentQps(long elapsedMs);

    default double currentQpsValue(long elapsedMs) {
        return currentQps(elapsedMs);
    }

    boolean isDynamic();
}
