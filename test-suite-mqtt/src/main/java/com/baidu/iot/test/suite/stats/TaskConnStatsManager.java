
package com.baidu.iot.test.suite.stats;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import com.baidu.iot.test.suite.stats.pojo.StatsConnResult;

/**
 */
public class TaskConnStatsManager extends TaskLatencyStatsManager<StatsConnResult>{

    private final AtomicLong connFailCount = new AtomicLong();

    public TaskConnStatsManager(String taskId) {
        super(taskId , "CONN", null);
    }

    public TaskConnStatsManager(String taskId, int skipStatsPeriod) {
        super(taskId , "CONN", skipStatsPeriod);
    }

    public TaskConnStatsManager(String taskId, int skipStatsPeriod, ExecutorService executor) {
        super(taskId , "CONN", skipStatsPeriod, executor);
    }

    public void recordConnFail() {
        super.recordFail();
        connFailCount.incrementAndGet();
    }

    public StatsConnResult getTotalResult() {
        return StatsConnResult.builder()
                .actualResult(totalRecorder.genResult(Duration.ofMillis(lastTs - startTs)))
                .connectFailCount(connFailCount.get())
                .build();
    }

    public void reset() {
        super.reset();
        connFailCount.set(0);
    }

}
