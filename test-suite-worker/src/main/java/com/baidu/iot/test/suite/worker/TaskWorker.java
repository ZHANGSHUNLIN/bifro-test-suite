package com.baidu.iot.test.suite.worker;

import com.baidu.iot.test.suite.TaskStage;

import java.util.concurrent.CompletableFuture;

public interface TaskWorker {

    void startTask();

    CompletableFuture<Void> stopTask();

    TaskStage getTaskState();

}
