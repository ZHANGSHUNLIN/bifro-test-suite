/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.stats.recorder;

import com.hivemq.client.internal.annotations.NotThreadSafe;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import lombok.Data;

import java.time.Duration;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;;
import java.util.stream.Collectors;


import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@NotThreadSafe
public class MetricsRecorder extends BucketLatencyRecorder {

    static {
        Metrics.addRegistry(new SimpleMeterRegistry());
    }

    private Timer latencyTimer;
    private Tags tags;
    private double sumOfStandardDeviationInNano;

    public MetricsRecorder(Tags tags) {
        this.tags = tags;
        buildMeters();
    }

    @Override
    public void updateSuccess(long latency, TimeUnit timeUnit) {
        //updateStandardDeviation(latency, timeUnit);
        latencyTimer.record(latency, timeUnit);
    }

    @Override
    public StatsBasicResult genResult(Duration duration) {
        if (latencyTimer.count() == 0) {
            return StatsBasicResult.builder()
                    .bucketCounts(getEmptyLatencyBucketCount())
                    .build();
        }
        double[] percentileLatencies = getPercentileLatencies(0, 0.5, 0.95, 0.99, 0.999);
        return StatsBasicResult.builder()
                .count(latencyTimer.count())
                .meanLatency(roundHalfUp(latencyTimer.mean(TimeUnit.MILLISECONDS), 2))
                .qps(roundHalfUp(((latencyTimer.count() * 1000.0) / duration.toMillis()), 2))
                .standardDeviation(
                        roundHalfUp(Math.sqrt(sumOfStandardDeviationInNano / latencyTimer.count()) / 1000000, 2))
                .minLatency(Math.max(roundHalfUp(percentileLatencies[0], 2), 0))
                .medianLatency(roundHalfUp(percentileLatencies[1], 2))
                .p95Latency(roundHalfUp(percentileLatencies[2], 2))
                .p99Latency(roundHalfUp(percentileLatencies[3], 2))
                .p999Latency(roundHalfUp(percentileLatencies[4], 2))
                .maxLatency(roundHalfUp(latencyTimer.max(TimeUnit.MILLISECONDS), 2))
                .bucketCounts(getLatencyBucketCount())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Override
    public void reset() {
        sumOfStandardDeviationInNano = 0;
        if (latencyTimer != null) {
            latencyTimer.close();
            Metrics.globalRegistry.remove(latencyTimer);
        }
//        buildMeters();
    }

    @Override
    protected double[] getPercentileLatencies(double... percents) {
        TreeMap<Double, Double> pvMap =
                new TreeMap<>(Arrays.asList(latencyTimer.takeSnapshot().percentileValues())
                        .stream()
                        .collect(Collectors.toMap(
                                pv -> pv.percentile(),
                                pv -> pv.value(TimeUnit.MILLISECONDS))
                        )
                );
        double[] results = new double[percents.length];
        for (int i = 0; i < percents.length; i++) {
            results[i] = Math.min(percents[i] <= 0 ? pvMap.firstEntry().getValue()
                    : pvMap.floorEntry(percents[i]).getValue(), latencyTimer.max(TimeUnit.MILLISECONDS));
        }
        return results;
    }

    @Override
    protected double[][] getLatencyBucketCount() {
        CountAtBucket[] countAtBuckets = latencyTimer.takeSnapshot().histogramCounts();
        double[][] bcs = new double[countAtBuckets.length][2];
        if (countAtBuckets.length < 1) {
            return bcs;
        }
        bcs[0] = new double[] {countAtBuckets[0].bucket(TimeUnit.MILLISECONDS), countAtBuckets[0].count()};
        for (int i = 1; i < countAtBuckets.length; i++) {
            bcs[i] = new double[] {countAtBuckets[i].bucket(TimeUnit.MILLISECONDS),
                    countAtBuckets[i].count() - countAtBuckets[i - 1].count()};
        }
        return bcs;
    }

    private double[][] getEmptyLatencyBucketCount() {
        CountAtBucket[] countAtBuckets = latencyTimer.takeSnapshot().histogramCounts();
        double[][] bcs = new double[countAtBuckets.length][2];
        bcs[0] = new double[] {countAtBuckets[0].bucket(TimeUnit.MILLISECONDS), countAtBuckets[0].count()};
        for (int i = 1; i < countAtBuckets.length; i++) {
            bcs[i] = new double[] {countAtBuckets[i].bucket(TimeUnit.MILLISECONDS), 0.0};
        }
        return bcs;
    }

    private void buildMeters() {
        this.latencyTimer = Timer.builder("test.suite.task.latency.timer")
                .tags(tags)
                .distributionStatisticBufferLength(1)
                .distributionStatisticExpiry(Duration.ofMillis(Long.MAX_VALUE))
                .publishPercentileHistogram()
                .publishPercentiles(percentiles)
                .serviceLevelObjectives(buckets)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofMillis(60000))
                .register(Metrics.globalRegistry);
    }


    private void updateStandardDeviation(long value, TimeUnit timeUnit) {
        double meanLatency = latencyTimer.mean(TimeUnit.NANOSECONDS);
        double deltaFromMean = timeUnit.toNanos(value) - meanLatency;
        meanLatency += deltaFromMean / (latencyTimer.count() + 1);
        double deltaFromMean2 = timeUnit.toNanos(value) - meanLatency;
        sumOfStandardDeviationInNano += deltaFromMean * deltaFromMean2;
    }

}
