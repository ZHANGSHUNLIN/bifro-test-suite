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

package org.apache.bifromq.testsuite.worker.ratelimit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;
import org.apache.bifromq.testsuite.qps.WaveQpsStrategy;

public final class FiniteDispatchPlan {

    private static final double MILLIS_PER_SECOND = 1000.0d;

    private final long[][] points;
    private final double[] cumulativeCounts;
    private final int totalCount;
    private final long durationMs;

    private FiniteDispatchPlan(long[][] points, double[] cumulativeCounts, int totalCount) {
        this.points = points;
        this.cumulativeCounts = cumulativeCounts;
        this.totalCount = totalCount;
        this.durationMs = points[points.length - 1][0];
    }

    public static FiniteDispatchPlan fromProfileDataPoints(List<long[]> dataPoints, int totalCount) {
        if (dataPoints == null || dataPoints.size() < 2 || totalCount <= 0) {
            return null;
        }
        long[][] sortedPoints = dataPoints.stream()
            .filter(point -> point != null && point.length >= 2)
            .map(point -> new long[] {Math.max(0L, point[0]), Math.max(0L, point[1])})
            .sorted(Comparator.comparingLong(point -> point[0]))
            .toArray(long[][]::new);
        if (sortedPoints.length < 2) {
            return null;
        }

        double[] cumulative = new double[sortedPoints.length];
        for (int i = 1; i < sortedPoints.length; i++) {
            long previousTimeMs = sortedPoints[i - 1][0];
            long currentTimeMs = sortedPoints[i][0];
            if (currentTimeMs <= previousTimeMs) {
                cumulative[i] = cumulative[i - 1];
                continue;
            }
            double averageQps = (sortedPoints[i - 1][1] + sortedPoints[i][1]) / 2.0d;
            cumulative[i] = cumulative[i - 1] + averageQps * (currentTimeMs - previousTimeMs)
                / MILLIS_PER_SECOND;
        }
        return new FiniteDispatchPlan(sortedPoints, cumulative, totalCount);
    }

    public static FiniteDispatchPlan fromWaveSpec(WaveQpsSpec spec, int totalCount) {
        if (spec == null || totalCount <= 0 || spec.getTotalDurationMs() <= 0) {
            return null;
        }
        WaveQpsStrategy strategy = new WaveQpsStrategy(spec);
        long durationMs = spec.getTotalDurationMs();
        long sampleIntervalMs = Math.max(20L, Math.min(100L, durationMs));
        List<long[]> points = new ArrayList<>();
        for (long elapsedMs = 0; elapsedMs <= durationMs; elapsedMs += sampleIntervalMs) {
            points.add(new long[] {elapsedMs, strategy.currentQps(elapsedMs)});
        }
        if (points.isEmpty() || points.get(points.size() - 1)[0] != durationMs) {
            points.add(new long[] {durationMs, strategy.currentQps(durationMs)});
        }
        return fromProfileDataPoints(points, totalCount);
    }

    public int expectedCountAt(long elapsedMs) {
        if (elapsedMs <= points[0][0]) {
            return 0;
        }
        if (elapsedMs >= durationMs) {
            return cappedCount(cumulativeCounts[cumulativeCounts.length - 1]);
        }

        int lower = 0;
        int upper = points.length - 1;
        while (upper - lower > 1) {
            int mid = (lower + upper) >>> 1;
            if (points[mid][0] <= elapsedMs) {
                lower = mid;
            } else {
                upper = mid;
            }
        }

        long segmentStartMs = points[lower][0];
        long segmentEndMs = points[upper][0];
        if (segmentEndMs <= segmentStartMs) {
            return cappedCount(cumulativeCounts[lower]);
        }

        double elapsedInSegmentMs = elapsedMs - segmentStartMs;
        double segmentDurationMs = segmentEndMs - segmentStartMs;
        double startQps = points[lower][1];
        double endQps = points[upper][1];
        double qpsAtElapsed = startQps + (endQps - startQps) * elapsedInSegmentMs / segmentDurationMs;
        double averageQps = (startQps + qpsAtElapsed) / 2.0d;
        double count = cumulativeCounts[lower] + averageQps * elapsedInSegmentMs / MILLIS_PER_SECOND;
        return cappedCount(count);
    }

    public boolean isEnded(long elapsedMs) {
        return elapsedMs >= durationMs;
    }

    public long durationMs() {
        return durationMs;
    }

    public int plannedTotalCount() {
        return cappedCount(cumulativeCounts[cumulativeCounts.length - 1]);
    }

    private int cappedCount(double count) {
        return Math.min(totalCount, Math.max(0, (int) Math.floor(count + 1.0e-9d)));
    }
}
