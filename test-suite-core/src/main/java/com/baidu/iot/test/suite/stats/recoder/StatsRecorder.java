/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.stats.recoder;

import com.hivemq.client.internal.annotations.NotThreadSafe;
import lombok.Data;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;


import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;
import lombok.EqualsAndHashCode;

/**
 * Created by mafei01 in 3/14/21 11:44 PM
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NotThreadSafe
public class StatsRecorder extends BucketLatencyRecorder {

    private long totalSuccessCount;
    private long totalLatency;
    private double meanLatency;
    private double sumOfStandardDeviation;
    private TreeMap<Long, Long> latencyFrequencyMap = new TreeMap<>();

    @Override
    // TODO should modify result with timeUnit
    public void updateSuccess(long latency, TimeUnit timeUnit) {
        totalSuccessCount++;
        totalLatency += latency;
        updateStandardDeviation(latency);
        latencyFrequencyMap.compute(latency, (k, v) -> v == null ? 1 : ++v);
    }

    @Override
    public StatsBasicResult genResult(Duration duration) {
        if (totalSuccessCount == 0) {
            return StatsBasicResult.builder().build();
        }
        return StatsBasicResult.builder()
                .count(this.getTotalSuccessCount())
                .meanLatency(roundHalfUp(this.getMeanLatency(), 2))
                .qps(roundHalfUp(((this.totalSuccessCount * 1000.0) / duration.toMillis()), 2))
                .standardDeviation(
                        roundHalfUp(Math.sqrt(this.getSumOfStandardDeviation() / this.getTotalSuccessCount()), 2))
                .medianLatency(roundHalfUp(this.getPercentileLatency(0.5), 2))
                .p95Latency(roundHalfUp(this.getPercentileLatency(0.95), 2))
                .p99Latency(roundHalfUp(this.getPercentileLatency(0.99), 2))
                .p999Latency(roundHalfUp(this.getPercentileLatency(0.999), 2))
                .maxLatency(this.getLatencyFrequencyMap().lastKey())
                .minLatency(this.getLatencyFrequencyMap().firstKey())
                .bucketCounts(getLatencyBucketCount())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Override
    public void reset() {
        totalSuccessCount = 0;
        totalLatency = 0;
        meanLatency = 0;
        sumOfStandardDeviation = 0;
        latencyFrequencyMap.clear();
    }

    @Override
    protected double[] getPercentileLatencies(double... percents) {
        double[] result = new double[percents.length];
        for (int i = 0; i < percents.length; i++) {
            result[i] = getPercentileLatency(percents[i]);
        }
        return result;
    }

    @Override
    protected double[][] getLatencyBucketCount() {
        double[][] bcs = new double[buckets.length][2];
        for (int i = 0; i < buckets.length; i++) {
            bcs[i][0] = buckets[i].toMillis();
        }
        int index = 0;
        Iterator<Map.Entry<Long, Long>> iterator = latencyFrequencyMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> current = iterator.next();
            while (buckets[index].toMillis() < current.getKey()) {
                index++;
            }
            bcs[index][1] += current.getValue();
        }
        return bcs;
    }

    private double getPercentileLatency(double percent) {
        if (percent <= 0) {
            return latencyFrequencyMap.firstKey();
        }
        if (percent >= 1) {
            return latencyFrequencyMap.lastKey();
        }
        double threshold = totalSuccessCount * percent;
        if (Math.ceil(threshold) == totalSuccessCount) {
            return latencyFrequencyMap.lastEntry().getValue();
        }
        long sum = 0;
        double result = 0;
        for (long latencyBucket : latencyFrequencyMap.keySet()) {
            long bucketValue = latencyFrequencyMap.get(latencyBucket);
            sum += bucketValue;
            if (sum >= threshold) {
                long leftLatency = latencyFrequencyMap.lowerKey(latencyBucket) == null
                        ? latencyBucket : latencyFrequencyMap.lowerKey(latencyBucket);
                long rightLatency = latencyBucket;
                long leftSum = sum - latencyFrequencyMap.get(latencyBucket);
                double bucketPercentPos = (threshold - leftSum) / bucketValue;
                result = leftLatency + (rightLatency - leftLatency) * bucketPercentPos;
                break;
            }
        }
        return result;
    }

    private void updateStandardDeviation(double value) {
        double deltaFromMean = value - meanLatency;
        meanLatency += deltaFromMean / totalSuccessCount;
        double deltaFromMean2 = value - meanLatency;
        sumOfStandardDeviation += deltaFromMean * deltaFromMean2;
    }

}
