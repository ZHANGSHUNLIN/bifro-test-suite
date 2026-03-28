/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite.stats.pojo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StatsBasicResult.
 */
class StatsBasicResultTest {

    @Test
    void testBuilder_shouldCreateInstance() {
        // when
        var result = StatsBasicResult.builder()
                .count(100)
                .qps(10.0)
                .meanLatency(50.5)
                .standardDeviation(5.2)
                .medianLatency(48.0)
                .p95Latency(55.0)
                .p99Latency(58.0)
                .p999Latency(59.5)
                .maxLatency(60.0)
                .minLatency(40.0)
                .bucketCounts(new double[10][2])
                .timestamp(1234567890L)
                .build();

        // then
        assertThat(result.getCount()).isEqualTo(100);
        assertThat(result.getQps()).isEqualTo(10.0);
        assertThat(result.getMeanLatency()).isEqualTo(50.5);
        assertThat(result.getStandardDeviation()).isEqualTo(5.2);
        assertThat(result.getMedianLatency()).isEqualTo(48.0);
        assertThat(result.getP95Latency()).isEqualTo(55.0);
        assertThat(result.getP99Latency()).isEqualTo(58.0);
        assertThat(result.getP999Latency()).isEqualTo(59.5);
        assertThat(result.getMaxLatency()).isEqualTo(60.0);
        assertThat(result.getMinLatency()).isEqualTo(40.0);
        assertThat(result.getTimestamp()).isEqualTo(1234567890L);
    }

    @Test
    void testNoArgsConstructor_shouldCreateEmptyInstance() {
        // when
        var result = new StatsBasicResult();

        // then - default values for primitives
        assertThat(result.getCount()).isZero();
        assertThat(result.getQps()).isZero();
        assertThat(result.getMeanLatency()).isZero();
        assertThat(result.getStandardDeviation()).isZero();
        assertThat(result.getMedianLatency()).isZero();
        assertThat(result.getP95Latency()).isZero();
        assertThat(result.getP99Latency()).isZero();
        assertThat(result.getP999Latency()).isZero();
        assertThat(result.getMaxLatency()).isZero();
        assertThat(result.getMinLatency()).isZero();
        assertThat(result.getBucketCounts()).isNull();
        assertThat(result.getTimestamp()).isZero();
    }

    @Test
    void testAllArgsConstructor_shouldCreateInstance() {
        // given
        long count = 200;
        double qps = 20.0;
        double meanLatency = 45.5;
        double standardDeviation = 3.8;
        double medianLatency = 44.0;
        double p95Latency = 50.0;
        double p99Latency = 52.0;
        double p999Latency = 53.0;
        double maxLatency = 55.0;
        double minLatency = 35.0;
        double[][] bucketCounts = new double[10][2];
        long timestamp = 9876543210L;

        // when
        var result = new StatsBasicResult(count, qps, meanLatency, standardDeviation,
                medianLatency, p95Latency, p99Latency, p999Latency,
                maxLatency, minLatency, bucketCounts, timestamp);

        // then
        assertThat(result.getCount()).isEqualTo(count);
        assertThat(result.getQps()).isEqualTo(qps);
        assertThat(result.getMeanLatency()).isEqualTo(meanLatency);
        assertThat(result.getStandardDeviation()).isEqualTo(standardDeviation);
        assertThat(result.getMedianLatency()).isEqualTo(medianLatency);
        assertThat(result.getP95Latency()).isEqualTo(p95Latency);
        assertThat(result.getP99Latency()).isEqualTo(p99Latency);
        assertThat(result.getP999Latency()).isEqualTo(p999Latency);
        assertThat(result.getMaxLatency()).isEqualTo(maxLatency);
        assertThat(result.getMinLatency()).isEqualTo(minLatency);
        assertThat(result.getBucketCounts()).isSameAs(bucketCounts);
        assertThat(result.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void testSetters_shouldUpdateFields() {
        // given
        var result = StatsBasicResult.builder().build();

        // when
        result.setCount(500);
        result.setQps(50.0);
        result.setMeanLatency(30.5);
        result.setStandardDeviation(2.5);

        // then
        assertThat(result.getCount()).isEqualTo(500);
        assertThat(result.getQps()).isEqualTo(50.0);
        assertThat(result.getMeanLatency()).isEqualTo(30.5);
        assertThat(result.getStandardDeviation()).isEqualTo(2.5);
    }

    @Test
    void testEquality_sameValues_shouldBeEqual() {
        // given
        var result1 = StatsBasicResult.builder()
                .count(100)
                .meanLatency(50.0)
                .build();
        var result2 = StatsBasicResult.builder()
                .count(100)
                .meanLatency(50.0)
                .build();

        // then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    void testEquality_differentValues_shouldNotBeEqual() {
        // given
        var result1 = StatsBasicResult.builder().count(100).build();
        var result2 = StatsBasicResult.builder().count(200).build();

        // then
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void testBuilder_partialFields_shouldCreateInstanceWithDefaults() {
        // when
        var result = StatsBasicResult.builder()
                .count(100)
                .build();

        // then
        assertThat(result.getCount()).isEqualTo(100);
        assertThat(result.getQps()).isZero();  // default value
        assertThat(result.getMeanLatency()).isZero();  // default value
    }

    @Test
    void testBucketCountsField_shouldStoreArray() {
        // given
        double[][] expectedBucketCounts = {
                {20.0, 10.0},
                {50.0, 5.0},
                {100.0, 2.0}
        };

        // when
        var result = StatsBasicResult.builder()
                .bucketCounts(expectedBucketCounts)
                .build();

        // then
        assertThat(result.getBucketCounts()).isEqualTo(expectedBucketCounts);
        assertThat(result.getBucketCounts()[0][0]).isEqualTo(20.0);
        assertThat(result.getBucketCounts()[0][1]).isEqualTo(10.0);
    }

    @Test
    void testTimestampField_shouldStoreLongValue() {
        // given
        long currentTime = System.currentTimeMillis();

        // when
        var result = StatsBasicResult.builder()
                .timestamp(currentTime)
                .build();

        // then
        assertThat(result.getTimestamp()).isEqualTo(currentTime);
    }
}
