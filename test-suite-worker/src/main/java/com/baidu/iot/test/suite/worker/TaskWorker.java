package com.baidu.iot.test.suite.worker;

import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.client.MQTTClientWrapper;
import com.baidu.iot.test.suite.worker.pojo.EventReport;

import io.reactivex.subjects.Subject;

import java.util.concurrent.CompletableFuture;

public interface TaskWorker {

    void startTask();

    CompletableFuture<Void> stopTask();

    TaskStage getTaskState();

    Subject<EventReport> reportEventSubject();

}
