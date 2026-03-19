package com.baidu.iot.test.suite.worker;

import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;
import com.baidu.iot.test.suite.stats.pojo.StatsConnResult;
import com.baidu.iot.test.suite.stats.pojo.StatsPubResult;
import com.baidu.iot.test.suite.stats.pojo.StatsSubResult;
import com.baidu.iot.test.suite.worker.pojo.EventReport;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public abstract class BaseTaskWorker implements TaskWorker {

    private final AtomicBoolean interrupt = new AtomicBoolean(false);
    protected final Vertx vertx;
    protected final TaskConfig taskConfig;
    protected final AtomicReference<TaskStage> taskStage = new AtomicReference<>(TaskStage.INIT);
    private final Subject<EventReport> reportEventSubject = PublishSubject.<EventReport>create()
            .toSerialized();

    protected BaseTaskWorker(Vertx vertx, TaskConfig taskConfig) {
        this.vertx = vertx;
        this.taskConfig = taskConfig;
    }

    @Override
    public TaskStage getTaskState() {
        return taskStage.get();
    }

    protected boolean canceled() {
        if (interrupt.get()) {
            taskStage.set(TaskStage.BREAKING);
            reportResult();
            log.info("interrupt task");
            return true;
        }
        return false;
    }

    protected void reportResult(StatsBasicResult statsBasicResult) {
        this.reportEventSubject.onNext(EventReport.builder()
                .statsBasicResult(statsBasicResult)
                .taskStage(taskStage.get())
                .build());
    }

    protected void reportResult(StatsConnResult statsConnResult) {
        this.reportEventSubject.onNext(EventReport.builder()
                .statsConnResult(statsConnResult)
                .taskStage(taskStage.get())
                .build());
    }

    protected void reportResult(StatsSubResult statsSubResult) {
        this.reportEventSubject.onNext(EventReport.builder()
                .statsSubResult(statsSubResult)
                .taskStage(taskStage.get())
                .build());
    }

    protected void reportResult(StatsPubResult statsPubResult, StatsSubResult statsSubResult) {
        this.reportEventSubject.onNext(EventReport.builder()
                .statsSubResult(statsSubResult)
                .statsPubResult(statsPubResult)
                .taskStage(taskStage.get())
                .build());
    }


    protected void reportResult(StatsPubResult statsPubResult) {
        this.reportEventSubject.onNext(EventReport.builder()
                .statsPubResult(statsPubResult)
                .taskStage(taskStage.get())
                .build());
    }


    protected void reportResult(EventReport eventReport) {
        this.reportEventSubject.onNext(eventReport);
    }

    protected void reportResult() {
        this.reportEventSubject.onNext(EventReport.builder()
                .taskStage(taskStage.get())
                .build());
    }

    public Subject<EventReport> reportEventSubject() {
        return this.reportEventSubject;
    }


    @Override
    public CompletableFuture<Void> stopTask() {
        return CompletableFuture.completedFuture(null);
    }
}
