
package com.baidu.iot.test.suite.worker;

import com.baidu.iot.test.suite.TaskEvent;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.statemachine.StateMachine;
import com.baidu.iot.test.suite.statemachine.StateChangeListener;
import com.baidu.iot.test.suite.statemachine.StateTransitionContext;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.TaskPipeline;
import com.baidu.iot.test.suite.pipeline.stages.StateTransitionStage;
import com.baidu.iot.test.suite.statemachine.TaskStateMachineConfig;
import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;
import com.baidu.iot.test.suite.stats.pojo.StatsConnResult;
import com.baidu.iot.test.suite.stats.pojo.StatsPubResult;
import com.baidu.iot.test.suite.stats.pojo.StatsSubResult;
import com.baidu.iot.test.suite.worker.pojo.EventReport;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base task worker with state machine and pipeline support.
 * All task workers must implement buildPipeline() to define their execution stages.
 */
@Slf4j
public abstract class BaseTaskWorker implements TaskWorker {

    private final AtomicBoolean interrupt = new AtomicBoolean(false);
    protected final Vertx vertx;
    protected final TaskConfig taskConfig;
    protected final StateMachine<TaskStage, TaskEvent> stateMachine;
    protected TaskPipeline<PipelineContext> pipeline;
    protected final AtomicReference<TaskStage> taskStage = new AtomicReference<>(TaskStage.INIT);
    private final Subject<EventReport> reportEventSubject = PublishSubject.<EventReport>create()
            .toSerialized();
    private final PipelineContext context;

    protected BaseTaskWorker(Vertx vertx, TaskConfig taskConfig) {
        this.vertx = vertx;
        this.taskConfig = taskConfig;
        this.stateMachine = getStateMachine();
        this.stateMachine.addListener(new StateChangeLogger());
        this.context = createPipelineContext();
        // buildPipeline() will be called by subclasses to initialize pipeline
        // after their fields are initialized
    }

    abstract StateMachine<TaskStage, TaskEvent> getStateMachine();

    /**
     * Initialize the pipeline.
     * Should be called by subclasses in their constructor after field initialization.
     */
    protected final void initPipeline() {
        this.pipeline = buildPipeline();
    }

    /**
     * Build the execution pipeline for this task worker.
     * All subclasses must implement this method to define their stages.
     *
     * @return the pipeline with all execution stages
     */
    protected abstract TaskPipeline<PipelineContext> buildPipeline();

    /**
     * Create pipeline context.
     */
    protected PipelineContext createPipelineContext() {
        Map<String, Object> config = new HashMap<>();
        config.put("taskConfig", taskConfig);
        config.put("taskId", taskConfig.getTaskId());
        config.put("totalClientCount", taskConfig.getTotalClientCount());
        config.put("connectRate", taskConfig.getConnectRate());
        config.put("disconnectRate", taskConfig.getDisconnectRate());
        config.put("connectRateLimiter", taskConfig.getConnectRateLimiter());
        config.put("disConnectRateLimiter", taskConfig.getDisConnectRateLimiter());
        config.put("stressDurationInSec", taskConfig.getStressDurationInSec());
        config.put("stageTimeoutInSec", taskConfig.getStageTimeoutInSec());
        config.put("tagPeriodIntervalInSec", taskConfig.getTagPeriodIntervalInSec());
        config.put("delayAfterReadyInSec", taskConfig.getDelayAfterReadyInSec());
        config.put("thingIdStartAt", taskConfig.getThingIdStartAt());
        config.put("fanOut", taskConfig.getFanOut());
        config.put("fanIn", taskConfig.getFanIn());
        config.put("pubOnly", taskConfig.isPubOnly());
        config.put("subOnly", taskConfig.isSubOnly());
        config.put("vertx", vertx);
        int expectPubCount = calculateExpectPubCount();
        int expectSubCount = calculateExpectSubCount();

        return PipelineContext.of(vertx, stateMachine, config, expectPubCount, expectSubCount);
    }

    /**
     * Calculate expected pub client count.
     */
    protected int calculateExpectPubCount() {
        if (taskConfig.isPubOnly()) {
            return taskConfig.getTotalClientCount();
        } else if (taskConfig.isSubOnly()) {
            return 0;
        } else if (taskConfig.getFanIn() > 1) {
            return taskConfig.getFanIn() * (taskConfig.getTotalClientCount() / (taskConfig.getFanIn() + 1));
        } else {
            return taskConfig.getTotalClientCount() / (taskConfig.getFanOut() + 1);
        }
    }

    /**
     * Calculate expected sub client count.
     */
    protected int calculateExpectSubCount() {
        if (taskConfig.isSubOnly()) {
            return taskConfig.getTotalClientCount();
        } else if (taskConfig.isPubOnly()) {
            return 0;
        } else if (taskConfig.getFanIn() > 1) {
            return taskConfig.getTotalClientCount() / (taskConfig.getFanIn() + 1);
        } else {
            return taskConfig.getFanOut() * (taskConfig.getTotalClientCount() / (taskConfig.getFanOut() + 1));
        }
    }

    @Override
    public TaskStage getTaskState() {
        return taskStage.get();
    }

    /**
     * Start the task.
     */
    @Override
    public void startTask() {
        log.info("Starting task: {}", taskConfig.getTaskId());


        stateMachine.transition(TaskEvent.START_TASK, Map.of("taskId", taskConfig.getTaskId()))
            .thenCompose(success -> {
                if (!success) {
                    throw new IllegalStateException("Cannot start task - state transition failed");
                }
                return pipeline.execute(context);
            })
            .exceptionally(ex -> {
                log.error("Task execution failed", ex);
                stateMachine.transition(TaskEvent.FAILURE, Map.of("error", ex.getMessage()));
                return null;
            });
    }

    /**
     * Stop the task.
     */
    @Override
    public CompletableFuture<Void> stopTask() {
        log.info("Stopping task: {}", taskConfig.getTaskId());

        return stateMachine.transition(TaskEvent.STOP, Map.of())
            .thenCompose(success -> {
                if (success) {
                    return pipeline.cancel(context);
                }
                return CompletableFuture.completedFuture(null);
            });
    }

    /**
     * Interrupt the task.
     */
    public void interrupt() {
        log.info("Interrupting task: {}", taskConfig.getTaskId());
        interrupt.set(true);
        stateMachine.transition(TaskEvent.INTERRUPT, Map.of());
    }

    protected boolean canceled() {
        if (interrupt.get()) {
            taskStage.set(TaskStage.STOPPED);
            eventReport();
            log.info("interrupt task");
            return true;
        }
        return false;
    }

    protected void eventReport(StatsBasicResult statsBasicResult) {
        this.reportEventSubject.onNext(EventReport.builder()
                .statsBasicResult(statsBasicResult)
                .taskStage(taskStage.get())
                .build());
    }

    protected void eventReport(StatsConnResult statsConnResult) {
        this.reportEventSubject.onNext(EventReport.builder()
                .statsConnResult(statsConnResult)
                .taskStage(taskStage.get())
                .build());
    }

    protected void eventReport(StatsSubResult statsSubResult) {
        this.reportEventSubject.onNext(EventReport.builder()
                .statsSubResult(statsSubResult)
                .taskStage(taskStage.get())
                .build());
    }

    protected void eventReport(StatsPubResult statsPubResult, StatsSubResult statsSubResult) {
        this.reportEventSubject.onNext(EventReport.builder()
                .statsSubResult(statsSubResult)
                .statsPubResult(statsPubResult)
                .taskStage(taskStage.get())
                .build());
    }

    protected void eventReport(StatsPubResult statsPubResult) {
        this.reportEventSubject.onNext(EventReport.builder()
                .statsPubResult(statsPubResult)
                .taskStage(taskStage.get())
                .build());
    }

    protected void eventReport(EventReport eventReport) {
        this.reportEventSubject.onNext(eventReport);
    }

    protected void eventReport() {
        this.reportEventSubject.onNext(EventReport.builder()
                .taskStage(taskStage.get())
                .build());
    }

    public Subject<EventReport> reportEventSubject() {
        return this.reportEventSubject;
    }

    /**
     * State change listener for logging.
     */
    private class StateChangeLogger implements StateChangeListener<TaskStage> {
        @Override
        public void onStateChange(TaskStage from, TaskStage to, StateTransitionContext<TaskStage> context) {
            taskStage.set(to);
            log.info("State transition: {} -> {}, taskId={}", from, to, taskConfig.getTaskId());
        }

        @Override
        public void onStateEntered(TaskStage state, StateTransitionContext<TaskStage> context) {
            log.debug("Entered state: {}, taskId={}", state, taskConfig.getTaskId());
        }

        @Override
        public void onStateExited(TaskStage state, StateTransitionContext<TaskStage> context) {
            log.debug("Exited state: {}, taskId={}", state, taskConfig.getTaskId());
        }
    }
}
