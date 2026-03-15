package com.baidu.iot.test.suite.worker;

import java.util.concurrent.CompletableFuture;

public interface TaskWorker {

    void startTask();

    CompletableFuture<Void> stopTask();

    TaskStage getTaskState();

}
