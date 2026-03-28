/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.stats.recorder;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;

/**
 * Created by mafei01 in 5/21/21 1:36 PM
 */
public interface LatencyRecorder {

    /**
     * Record one event latency
     * @param latency
     */
    void updateSuccess(long latency, TimeUnit timeUnit);

    /**
     * General current period result of this recorder
     * @param duration Record duration for qps calculation
     * @return
     */
    StatsBasicResult genResult(Duration duration);

    /**
     * Reset this recorder to init state
     */
    void reset();
}
