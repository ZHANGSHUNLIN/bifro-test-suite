
package com.baidu.iot.test.suite.stats.recorder;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;

/**
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
