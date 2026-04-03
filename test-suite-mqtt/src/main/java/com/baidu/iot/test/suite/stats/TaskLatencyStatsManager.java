

package com.baidu.iot.test.suite.stats;

import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;
import com.baidu.iot.test.suite.stats.recorder.LatencyRecorder;
import com.baidu.iot.test.suite.stats.recorder.MetricsRecorder;
import com.google.common.collect.Lists;
import io.micrometer.core.instrument.Tags;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 */
@Slf4j
public abstract class TaskLatencyStatsManager<T> {

    @Getter
    protected final String name;
    protected final ExecutorService executor;
    protected final LatencyRecorder totalRecorder;
    protected final LatencyRecorder periodRecorder;
    protected final List<StatsBasicResult> periodResults = new ArrayList<>();

    protected long startTs = -1;
    protected long lastTs = -1;
    protected long periodStartTs = -1;
    protected long periodLastTs = -1;

    private final int skipStatsPeriod;
    private int taggedPeriod = 0;

    public TaskLatencyStatsManager(String name, String event) {
        this(name, event, 0, null);
    }

    public TaskLatencyStatsManager(String name, String event, ExecutorService executor) {
        this(name, event, 0, executor);
    }

    public TaskLatencyStatsManager(String name, String event, int skipStatsPeriod) {
        this(name, event, skipStatsPeriod, null);
    }

    public TaskLatencyStatsManager(String name, String event, int skipStatsPeriod, ExecutorService executor) {
        this.skipStatsPeriod = skipStatsPeriod;
        this.name = name;
        this.executor = executor != null ? executor : ForkJoinPool.commonPool();
        this.totalRecorder = new MetricsRecorder(Tags.of("name", name, "event", event, "type", "total"));
        this.periodRecorder = new MetricsRecorder(Tags.of("name", name, "event", event, "type", "period"));
    }

    public void recordSuccess(long latency, TimeUnit timeUnit) {
        executor.execute(() -> {
            updateStatsTimestamp(latency);
            periodRecorder.updateSuccess(latency, timeUnit);
            if (taggedPeriod >= skipStatsPeriod) {
                totalRecorder.updateSuccess(latency, timeUnit);
            }
        });
    }

    public StatsBasicResult tagPeriodResult() {
        CompletableFuture<StatsBasicResult> result = new CompletableFuture<>();
        long start = System.currentTimeMillis();
        taggedPeriod++;
        StatsBasicResult statsBasicResult = periodRecorder.genResult(Duration.ofMillis(periodLastTs - periodStartTs));
        periodResults.add(statsBasicResult);
        periodRecorder.reset();
        periodStartTs = System.currentTimeMillis();
        periodLastTs = -1;
        log.info("tagPeriodResult actually costs {}ms", System.currentTimeMillis() - start);
        result.complete(statsBasicResult);
        return result.join();
    }

    public abstract T getTotalResult();

    public List<StatsBasicResult> getPeriodResults() {
        return Lists.newArrayList(periodResults);
    }

    protected void recordFail() {
        updateStatsTimestamp();
    }

    protected void reset() {
        totalRecorder.reset();
        periodRecorder.reset();
        periodResults.clear();
        startTs = -1;
        lastTs = -1;
        periodStartTs = -1;
        periodLastTs = -1;
    }

    private void updateStatsTimestamp() {
        if (startTs <= 0) {
            startTs = System.currentTimeMillis();
        }
        if (periodStartTs <= 0) {
            periodStartTs = startTs;
        }
        lastTs = System.currentTimeMillis();
        periodLastTs = lastTs;
    }

    private void updateStatsTimestamp(long latencyInNanoSeconds) {
        lastTs = System.currentTimeMillis();
        if (startTs <= 0) {
            startTs = lastTs - TimeUnit.NANOSECONDS.toMillis(latencyInNanoSeconds);
        }
        if (periodStartTs <= 0) {
            periodStartTs = startTs;
        }
        periodLastTs = lastTs;
    }

}
