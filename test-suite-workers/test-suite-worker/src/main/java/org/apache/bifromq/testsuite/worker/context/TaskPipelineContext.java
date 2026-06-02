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

package org.apache.bifromq.testsuite.worker.context;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.Getter;
import org.apache.bifromq.testsuite.diagnostics.AsyncDiagnosticContext;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.pipeline.PipelineContext;
import org.apache.bifromq.testsuite.pipeline.PipelineContext.ContextScope;
import org.apache.bifromq.testsuite.pipeline.PipelineStageSnapshot;
import org.apache.bifromq.testsuite.statemachine.StateMachine;
import org.apache.bifromq.testsuite.worker.log.TaskLogger;
import org.apache.bifromq.testsuite.worker.pipeline.publish.NodePublishScheduler;

@Getter
public class TaskPipelineContext extends PipelineContext<TaskStage, TaskEvent> {

    private final TaskExecutionContext executionContext;

    private final AtomicReference<NodePublishScheduler> nodePublishScheduler = new AtomicReference<>();

    private final AtomicReference<WorkerExecutor> subWorkerExecutor = new AtomicReference<>();

    private final Consumer<String> onTaskComplete;

    public TaskPipelineContext(Vertx vertx,
                               StateMachine<TaskStage, TaskEvent> stateMachine,
                               TaskExecutionContext executionContext,
                               Consumer<String> onTaskComplete) {
        super(vertx, stateMachine);
        this.executionContext = executionContext;
        this.onTaskComplete = onTaskComplete;
    }

    @Override
    public int getDelayAfterStageInSec() {
        return executionContext.delayAfterStageInSec();
    }

    @Override
    public void onStageEvent(PipelineStageSnapshot snapshot) {
        TaskLogger.logStageEvent(
            executionContext.taskId(),
            executionContext.nodeId(),
            snapshot,
            executionContext.totalClientCount(),
            clientTag(snapshot.getKey()));
    }

    public void setNodePublishScheduler(NodePublishScheduler scheduler) {
        NodePublishScheduler previous = nodePublishScheduler.getAndSet(scheduler);
        if (previous != null && previous != scheduler) {
            previous.stop();
        }
    }

    public void stopNodePublishScheduler() {
        NodePublishScheduler scheduler = nodePublishScheduler.getAndSet(null);
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    public WorkerExecutor subWorkerExecutor(int workerPoolSize) {
        WorkerExecutor current = subWorkerExecutor.get();
        if (current != null) {
            return current;
        }
        int poolSize = workerPoolSize > 0 ? workerPoolSize : Runtime.getRuntime().availableProcessors() * 2;
        WorkerExecutor created = getVertx().createSharedWorkerExecutor("client-worker", poolSize);
        if (subWorkerExecutor.compareAndSet(null, created)) {
            return created;
        }
        created.close();
        return subWorkerExecutor.get();
    }

    @Override
    public ContextScope enterDiagnosticContext(String stageName) {
        AsyncDiagnosticContext.Scope scope = enterStage(stageName);
        return scope::close;
    }

    @Override
    public Runnable wrapDiagnosticContext(String stageName, Runnable runnable) {
        return wrapStage(stageName, runnable);
    }

    public AsyncDiagnosticContext.Scope enterStage(String stage) {
        return AsyncDiagnosticContext.with(executionContext.taskId(), executionContext.nodeId(), stage, "");
    }

    public AsyncDiagnosticContext.Snapshot stageSnapshot(String stage) {
        return new AsyncDiagnosticContext.Snapshot(executionContext.taskId(), executionContext.nodeId(), stage, "");
    }

    public Runnable wrapStage(String stage, Runnable runnable) {
        return AsyncDiagnosticContext.wrap(stageSnapshot(stage), runnable);
    }

    public void closeSubWorkerExecutor() {
        WorkerExecutor executor = subWorkerExecutor.getAndSet(null);
        if (executor != null) {
            executor.close();
        }
    }

    private String clientTag(String stageName) {
        if (stageName == null) {
            return "";
        }
        int delimiter = stageName.lastIndexOf('-');
        return delimiter >= 0 && delimiter < stageName.length() - 1 ? stageName.substring(delimiter + 1) : "";
    }
}
