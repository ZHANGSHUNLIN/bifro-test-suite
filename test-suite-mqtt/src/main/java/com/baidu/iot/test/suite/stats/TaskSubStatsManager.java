
package com.baidu.iot.test.suite.stats;

import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import com.baidu.iot.test.suite.stats.pojo.StatsSubResult;

/**
 */
@Slf4j
public class TaskSubStatsManager extends TaskLatencyStatsManager<StatsSubResult>{

    private final AtomicLong subDuplicatedCount = new AtomicLong();

    public TaskSubStatsManager(String taskId) {
        super(taskId ,"SUB");
    }

    public TaskSubStatsManager(String taskId, ExecutorService executor) {
        super(taskId , "SUB", executor);
    }

    public TaskSubStatsManager(String taskId, int skipStatsPeriod) {
        super(taskId , "SUB", skipStatsPeriod);
    }

    public TaskSubStatsManager(String taskId, int skipStatsPeriod, ExecutorService executor) {
        super(taskId , "SUB", skipStatsPeriod, executor);
    }

    public synchronized void recordSubDuplicate() {
        super.recordFail();
        subDuplicatedCount.incrementAndGet();
    }

    public StatsSubResult getTotalResult() {
        return StatsSubResult.builder()
                .actualResult(totalRecorder.genResult(Duration.ofMillis(lastTs - startTs)))
                .duplicateSubMsgCount(subDuplicatedCount.get())
                .build();
    }

    public void reset() {
        super.reset();
        subDuplicatedCount.set(0);
    }

}
