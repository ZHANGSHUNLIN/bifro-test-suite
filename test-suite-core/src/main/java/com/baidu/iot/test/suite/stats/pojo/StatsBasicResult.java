/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.stats.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by mafei01 in 3/12/21 3:39 PM
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsBasicResult {
    private long count;
    private double qps;
    private double meanLatency;
    private double standardDeviation;
    private double medianLatency;
    private double p95Latency;
    private double p99Latency;
    private double p999Latency;
    private double maxLatency;
    private double minLatency;
    private double[][] bucketCounts;
    private long timestamp;

}
