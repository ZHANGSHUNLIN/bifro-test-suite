/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.stats.recoder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Created by mafei01 in 5/21/21 1:37 PM
 */
public abstract class BucketLatencyRecorder implements LatencyRecorder {

    protected static final double[] percentiles;
    protected static final Duration[] buckets;

    static {
        percentiles = new double[1001];
        for (int i = 0; i <= 1000; i++) {
            percentiles[i] = i / 1000.0;
        }
        buckets = new Duration[] {
                Duration.ofMillis(20),
                Duration.ofMillis(50),
                Duration.ofMillis(100),
                Duration.ofMillis(200),
                Duration.ofMillis(500),
                Duration.ofMillis(1000),
                Duration.ofMillis(2000),
                Duration.ofMillis(5000),
                Duration.ofMillis(10000),
                Duration.ofMillis(60000),
                Duration.ofMillis(Integer.MAX_VALUE),
        };
    }

    static double roundHalfUp(double origin, int scale) {
        return new BigDecimal(origin).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Get latencies in the specific percents
     * @param percents
     * @return
     */
    protected abstract double[] getPercentileLatencies(double... percents);

    /**
     * Get [latencyBucket, count] array in current recorder.
     * @return
     */
    protected abstract double[][] getLatencyBucketCount();

}
