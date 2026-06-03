/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.worker;

import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import io.vertx.core.Vertx;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.pipeline.PipelineProgressEvent;
import org.apache.bifromq.testsuite.pipeline.PipelineStage;
import org.apache.bifromq.testsuite.pipeline.TaskPipeline;
import org.apache.bifromq.testsuite.statemachine.StateChangeListener;
import org.apache.bifromq.testsuite.statemachine.StateMachine;
import org.apache.bifromq.testsuite.statemachine.StateTransitionContext;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionContext;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;
import org.apache.bifromq.testsuite.worker.eventbus.TaskRuntimeEventBus;
import org.apache.bifromq.testsuite.worker.eventbus.VertxTaskRuntimeEventBus;
import org.apache.bifromq.testsuite.worker.log.TaskLogger;
import org.apache.bifromq.testsuite.worker.pojo.EventReport;
import org.apache.bifromq.testsuite.worker.pojo.TaskStateChangeEvent;
import org.apache.bifromq.testsuite.worker.pojo.TaskStopContext;
import org.apache.bifromq.testsuite.worker.type.ExecutionPlan;

@Slf4j
public abstract class BaseTaskWorker implements TaskWorker {

    protected final Vertx vertx;
    protected final StateMachine<TaskStage, TaskEvent> stateMachine;
    protected final AtomicReference<TaskStage> taskStage = new AtomicReference<>(TaskStage.INIT);
    private final CompletableFuture<TaskStage> terminalFuture = new CompletableFuture<>();
    @Getter
    private final String taskId;
    @Getter
    private final String nodeId;
    private final String taskTypeName;
    private final int totalClientCount;
    private final AtomicBoolean interrupt = new AtomicBoolean(false);
    private final AtomicReference<TaskStopContext> stopContextRef = new AtomicReference<>();
    private final AtomicLong eventSeqGenerator = new AtomicLong(0);
    private final Subject<EventReport> reportEventSubject = PublishSubject.<EventReport>create()
        .toSerialized();
    private final TaskPipelineContext context;
    private final TaskRuntimeEventBus taskRuntimeEventBus;
    protected TaskPipeline<TaskPipelineContext> pipeline;
    private volatile io.micrometer.core.instrument.Timer.Sample taskDurationSample;

    protected BaseTaskWorker(Vertx vertx, ExecutionPlan plan) {
        this.vertx = vertx;
        TaskExecutionContext executionContext = plan.executionContext();
        this.taskId = executionContext.taskId();
        this.nodeId = executionContext.nodeId();
        this.taskTypeName = executionContext.taskTypeName();
        this.totalClientCount = executionContext.totalClientCount();
        this.taskRuntimeEventBus = new VertxTaskRuntimeEventBus(vertx.eventBus());
        this.stateMachine = plan.stateMachine();
        this.taskStage.set(this.stateMachine.getCurrentState());
        this.stateMachine.addListener(new StateChangeLogger());

        Consumer<String> onTaskComplete = this::stopTaskDurationTimer;
        this.context = new TaskPipelineContext(vertx, stateMachine, executionContext, onTaskComplete);

        TaskPipeline.Builder<TaskPipelineContext> builder = TaskPipeline.builder();
        for (PipelineStage<TaskPipelineContext> stage : plan.stages()) {
            builder.addStage(stage);
        }
        this.pipeline = builder.build();
        this.context.setOnProgressUpdate(stages -> {
            PipelineProgressEvent event = PipelineProgressEvent.builder()
                .taskId(taskId)
                .nodeId(nodeId)
                .stages(stages)
                .build();
            taskRuntimeEventBus.publishPipelineProgress(event);
        });
    }

    @Override
    public TaskStage getTaskState() {
        return taskStage.get();
    }

    @Override
    public void startTask() {
        log.info("Starting task: {}", taskId);

        TaskLogger.logTaskStart(
            taskId,
            taskTypeName,
            totalClientCount
        );

        MetricsHelper.counter(BifroTaskMetric.TASK_START_COUNT,
            io.micrometer.core.instrument.Tags.of(
                "taskId", taskId,
                "nodeId", nodeId,
                "taskType", taskTypeName));

        taskDurationSample = MetricsHelper.startTimer();

        stateMachine.transition(TaskEvent.START_TASK, Map.of("taskId", taskId))
            .thenCompose(success -> {
                if (!success) {
                    throw new IllegalStateException("Cannot start task - state transition failed");
                }
                pipeline.initProgress(context);
                return pipeline.execute(context);
            })
            .exceptionally(ex -> {
                log.error("Task execution failed", ex);
                stateMachine.transition(TaskEvent.FAILURE, Map.of("error", ex.getMessage()));

                MetricsHelper.counter(BifroTaskMetric.TASK_FAILURE_COUNT,
                    io.micrometer.core.instrument.Tags.of(
                        "taskId", taskId,
                        "nodeId", nodeId,
                        "taskType", taskTypeName,
                        "reason", "pipeline_error"));

                stopTaskDurationTimer("failed");
                return null;
            });
    }

    @Override
    public CompletableFuture<Void> stopTask() {
        return stopTask(TaskStopContext.userStop());
    }

    @Override
    public CompletableFuture<Void> stopTask(TaskStopContext stopContext) {
        TaskStopContext requestedStopContext = normalizeStopContext(stopContext);
        stopContextRef.compareAndSet(null, requestedStopContext);
        TaskStopContext effectiveStopContext = stopContextRef.get();
        Map<String, Object> stopMetadata = stopTransitionMetadata(effectiveStopContext);
        log.info("Stopping task: {}, reason={}", taskId, effectiveStopContext.getReason());

        TaskLogger.logTaskStop(taskId, taskStage.get());

        MetricsHelper.counter(BifroTaskMetric.TASK_STOP_COUNT,
            io.micrometer.core.instrument.Tags.of(
                "taskId", taskId,
                "nodeId", nodeId,
                "taskType", taskTypeName));

        stopTaskDurationTimer("stopped");

        return stateMachine.transition(TaskEvent.SHUTTING, stopMetadata)
            .thenCompose(success -> {
                if (success) {
                    log.debug("Task entered SHUTTING state: {}", taskId);
                }

                return pipeline.cancel(context);
            })
            .thenCompose(ignored -> stateMachine.transition(TaskEvent.STOP, stopMetadata))
            .thenAccept(success -> {
                if (success) {
                    log.debug("Task entered STOPPED state: {}", taskId);
                } else {

                    log.debug("STOP transition failed, falling back to INTERRUPT: {}", taskId);
                    stateMachine.transition(TaskEvent.INTERRUPT, stopMetadata);
                }
            });
    }

    public void interrupt() {
        log.info("Interrupting task: {}", taskId);
        interrupt.set(true);
        stateMachine.transition(TaskEvent.INTERRUPT, Map.of());
    }

    @Override
    public CompletableFuture<TaskStage> terminalFuture() {
        return terminalFuture;
    }

    private void stopTaskDurationTimer(String result) {
        if (taskDurationSample != null) {
            MetricsHelper.stopTimer(taskDurationSample, BifroTaskMetric.TASK_DURATION,
                "taskId", taskId,
                "nodeId", nodeId,
                "taskType", taskTypeName,
                "result", result);
            taskDurationSample = null;
        }
        MetricsHelper.freezeTimerSnapshots(taskId, nodeId);
    }

    protected boolean canceled() {
        if (interrupt.get()) {
            taskStage.set(TaskStage.STOPPED);
            eventReport();
            log.info("Task interrupted, taskId={}", taskId);
            return true;
        }
        return false;
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

    private void publishStateChangeEvent(TaskStage from, TaskStage to, StateTransitionContext<TaskStage> context) {
        TaskStateChangeEvent.TaskStateChangeEventBuilder builder = TaskStateChangeEvent.builder()
            .taskId(taskId)
            .fromStage(from)
            .toStage(to)
            .triggerEvent(context.getEvent() instanceof TaskEvent ? (TaskEvent) context.getEvent() : null)
            .timestamp(context.getTransitionTimestamp())
            .nodeId(nodeId)
            .eventSeq(eventSeqGenerator.incrementAndGet());
        TaskStopContext stopContext = stopContextFromTransition(context);
        if (stopContext != null && shouldAttachStopContext(context, to)) {
            builder.reason(stopContext.getReason() == null ? null : stopContext.getReason().name())
                .message(stopContext.getMessage())
                .metadata(stopEventMetadata(stopContext));
        }
        TaskStateChangeEvent event = builder.build();

        taskRuntimeEventBus.sendTaskStateChanged(event);
        log.debug("Published state change event: {}", event);
    }

    public Map<String, MqttClientTask> getClientTaskMap(String clientType) {
        TaskExecutionContext ec = context.getExecutionContext();
        return switch (clientType.toLowerCase()) {
            case "conn" -> ec.connClients();
            case "pub" -> ec.pubClients();
            case "sub" -> ec.subClients();
            default -> throw new IllegalArgumentException("Unknown client type: " + clientType);
        };
    }

    StateMachine<TaskStage, TaskEvent> getStateMachineForTest() {
        return stateMachine;
    }

    TaskPipelineContext getPipelineContextForTest() {
        return context;
    }

    private TaskStopContext normalizeStopContext(TaskStopContext context) {
        return context == null ? TaskStopContext.userStop() : context.normalized();
    }

    private Map<String, Object> stopTransitionMetadata(TaskStopContext stopContext) {
        return Map.of("stopContext", stopContext);
    }

    private TaskStopContext stopContextFromTransition(StateTransitionContext<TaskStage> context) {
        TaskStopContext stopContext = context.getMetadata("stopContext", TaskStopContext.class);
        return stopContext == null ? stopContextRef.get() : stopContext;
    }

    private boolean shouldAttachStopContext(StateTransitionContext<TaskStage> context, TaskStage to) {
        Object event = context.getEvent();
        return event == TaskEvent.SHUTTING || event == TaskEvent.STOP || to == TaskStage.STOPPED;
    }

    private Map<String, Object> stopEventMetadata(TaskStopContext stopContext) {
        java.util.LinkedHashMap<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (stopContext.getMetadata() != null) {
            metadata.putAll(stopContext.getMetadata());
        }
        if (stopContext.getReason() != null) {
            metadata.put("reason", stopContext.getReason().name());
        }
        if (stopContext.getInitiator() != null && !stopContext.getInitiator().isBlank()) {
            metadata.put("initiator", stopContext.getInitiator());
        }
        if (stopContext.getRequestedAt() != null) {
            metadata.put("requestedAt", stopContext.getRequestedAt().toString());
        }
        return Map.copyOf(metadata);
    }

    private class StateChangeLogger implements StateChangeListener<TaskStage> {
        @Override
        public void onStateChange(TaskStage from, TaskStage to, StateTransitionContext<TaskStage> context) {
            taskStage.set(to);
            if (isTerminal(to)) {
                terminalFuture.complete(to);
            }
            log.info("State transition: {} -> {}, taskId={}", from, to, taskId);
            TaskLogger.logStateTransition(
                taskId,
                nodeId,
                from,
                to,
                context.getEvent() instanceof TaskEvent ? (TaskEvent) context.getEvent() : null,
                context.getTransitionTimestamp()
            );

            publishStateChangeEvent(from, to, context);
        }

        private boolean isTerminal(TaskStage stage) {
            return stage == TaskStage.SHUTDOWN
                || stage == TaskStage.STOPPED
                || stage == TaskStage.FAILED
                || stage == TaskStage.TIMEOUT;
        }

        @Override
        public void onStateEntered(TaskStage state, StateTransitionContext<TaskStage> context) {
            log.debug("Entered state: {}, taskId={}", state, taskId);
        }

        @Override
        public void onStateExited(TaskStage state, StateTransitionContext<TaskStage> context) {
            log.debug("Exited state: {}, taskId={}", state, taskId);
        }
    }
}
