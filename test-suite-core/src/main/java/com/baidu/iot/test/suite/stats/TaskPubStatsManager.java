/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.stats;

import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import com.baidu.iot.test.suite.stats.pojo.StatsPubResult;

/**
 * Created by mafei01 in 3/11/21 6:52 PM
 */
@Slf4j
public class TaskPubStatsManager extends TaskLatencyStatsManager<StatsPubResult>{

    private final AtomicLong pubFailCount = new AtomicLong();
    private final AtomicLong expectPubCount = new AtomicLong();

    public TaskPubStatsManager(String taskId) {
        super(taskId , "PUB");
    }

    public TaskPubStatsManager(String taskId, ExecutorService executor) {
        super(taskId , "PUB", executor);
    }

    public TaskPubStatsManager(String taskId, int skipStatsPeriod) {
        super(taskId , "PUB", skipStatsPeriod);
    }

    public TaskPubStatsManager(String taskId, int skipStatsPeriod, ExecutorService executor) {
        super(taskId , "PUB", skipStatsPeriod, executor);
    }

    public void recordPubFail() {
        super.recordFail();
        pubFailCount.incrementAndGet();
    }

    public void recordExpect() {
        expectPubCount.incrementAndGet();
    }

    public StatsPubResult getTotalResult() {
        return StatsPubResult.builder()
                .expectPubMsgCount(expectPubCount.get())
                .actualResult(totalRecorder.genResult(Duration.ofMillis(lastTs - startTs)))
                .pubFailCount(pubFailCount.get())
                .build();
    }

    public void reset() {
        super.reset();
        pubFailCount.set(0);
    }

}
