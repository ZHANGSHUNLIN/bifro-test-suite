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

import java.util.List;

public class DataDrivenQpsStrategy implements QpsStrategy {

    private final long[][] dataPoints;
    private final double[] qpsValues;
    private final long totalDurationMs;
    private final ProfileQpsSpec.EndBehavior endBehavior;
    private final boolean clampToOne;

    public DataDrivenQpsStrategy(ProfileQpsSpec spec) {
        this(spec, true, 1.0d);
    }

    public DataDrivenQpsStrategy(ProfileQpsSpec spec, boolean clampToOne) {
        this(spec, clampToOne, 1.0d);
    }

    public DataDrivenQpsStrategy(ProfileQpsSpec spec, boolean clampToOne, double rateDivisor) {
        if (spec == null) {
            throw new IllegalArgumentException("ProfileQpsSpec must not be null");
        }
        List<long[]> pts = spec.getDataPoints();
        if (pts == null || pts.isEmpty()) {
            throw new IllegalArgumentException("ProfileQpsSpec.dataPoints must not be empty");
        }
        if (spec.getTotalDurationMs() <= 0) {
            throw new IllegalArgumentException(
                "totalDurationMs must be positive: " + spec.getTotalDurationMs());
        }
        this.dataPoints = pts.toArray(new long[0][]);
        this.qpsValues = new double[dataPoints.length];
        double effectiveRateDivisor = Math.max(Double.MIN_NORMAL, rateDivisor);
        for (int i = 0; i < dataPoints.length; i++) {
            qpsValues[i] = dataPoints[i][1] / effectiveRateDivisor;
        }
        this.totalDurationMs = spec.getTotalDurationMs();
        this.endBehavior = spec.getEndBehavior() != null
            ? spec.getEndBehavior()
            : ProfileQpsSpec.EndBehavior.LOOP;
        this.clampToOne = clampToOne;
    }

    @Override
    public int currentQps(long elapsedMs) {
        double qps = currentQpsValue(elapsedMs);
        return (int) Math.round(qps);
    }

    @Override
    public double currentQpsValue(long elapsedMs) {
        long position;
        if (endBehavior == ProfileQpsSpec.EndBehavior.LOOP) {
            position = elapsedMs % totalDurationMs;
        } else {

            position = Math.min(elapsedMs, dataPoints[dataPoints.length - 1][0]);
        }
        double qps = linearInterpolate(position);
        return clampToOne ? Math.max(1, qps) : Math.max(0, qps);
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    private double linearInterpolate(long posMs) {
        int lo = 0;
        int hi = dataPoints.length - 1;

        if (posMs <= dataPoints[lo][0]) {
            return qpsValues[lo];
        }
        if (posMs >= dataPoints[hi][0]) {
            return qpsValues[hi];
        }

        while (lo + 1 < hi) {
            int mid = (lo + hi) >>> 1;
            if (dataPoints[mid][0] <= posMs) {
                lo = mid;
            } else {
                hi = mid;
            }
        }

        long t0 = dataPoints[lo][0];
        long t1 = dataPoints[hi][0];
        double q0 = qpsValues[lo];
        double q1 = qpsValues[hi];

        if (t1 == t0) {
            return q0;
        }

        double ratio = (double) (posMs - t0) / (t1 - t0);
        return q0 + (q1 - q0) * ratio;
    }
}
