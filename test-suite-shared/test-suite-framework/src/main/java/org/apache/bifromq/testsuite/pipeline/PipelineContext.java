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

package org.apache.bifromq.testsuite.pipeline;

import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import org.apache.bifromq.testsuite.statemachine.StateMachine;

@Getter
public class PipelineContext<S, E> {

    private static final int MAX_PENDING_SAMPLES = 10;

    private final Vertx vertx;
    private final StateMachine<S, E> stateMachine;
    private final Map<String, Object> stageData;
    private final Map<String, StageExecutionScope> stageScopes;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final CompletableFuture<Void> completionFuture;

    
    @Getter
    private final List<PipelineStageSnapshot> pipelineProgress = new ArrayList<>();

    
    @Setter
    private Consumer<List<PipelineStageSnapshot>> onProgressUpdate;

    
    public PipelineContext(Vertx vertx, StateMachine<S, E> stateMachine) {
        this.vertx = vertx;
        this.stateMachine = stateMachine;
        this.stageData = new ConcurrentHashMap<>();
        this.stageScopes = new ConcurrentHashMap<>();
        this.completionFuture = new CompletableFuture<>();
    }

    public StageExecutionScope newStageScope(String stageName) {
        StageExecutionScope scope = new DefaultStageExecutionScope(stageName);
        stageScopes.put(stageName, scope);
        return scope;
    }

    public StageExecutionScope stageScope(String stageName) {
        return stageScopes.get(stageName);
    }

    public StageExecutionScope stageScopeOrCreate(String stageName) {
        return stageScopes.computeIfAbsent(stageName, DefaultStageExecutionScope::new);
    }

    public void updateStageSnapshot(int index, PipelineStageSnapshot snapshot) {
        if (index >= 0 && index < pipelineProgress.size()) {
            pipelineProgress.set(index, snapshot);
        }
        if (onProgressUpdate != null) {
            onProgressUpdate.accept(new ArrayList<>(pipelineProgress));
        }
    }

    public PipelineStageSnapshot refreshStageDiagnostics(String stageName) {
        if (stageName == null) {
            return null;
        }
        for (int i = 0; i < pipelineProgress.size(); i++) {
            PipelineStageSnapshot snapshot = pipelineProgress.get(i);
            if (stageName.equals(snapshot.getKey())) {
                PipelineStageSnapshot updated = enrichStageSnapshot(snapshot, stageScope(stageName));
                updateStageSnapshot(i, updated);
                return updated;
            }
        }
        return null;
    }

    public void onStageEvent(PipelineStageSnapshot snapshot) {
    }

    public ContextScope enterDiagnosticContext(String stageName) {
        return () -> {
        };
    }

    public Runnable wrapDiagnosticContext(String stageName, Runnable runnable) {
        return runnable;
    }

    public void cancel() {
        cancelled.set(true);
    }

    
    public CompletableFuture<Boolean> triggerTransition(Object event) {
        return stateMachine.transitionGeneric(event);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    
    public int getDelayAfterStageInSec() {
        return 0;
    }

    protected PipelineStageSnapshot enrichStageSnapshot(PipelineStageSnapshot snapshot, StageExecutionScope scope) {
        if (snapshot == null || scope == null) {
            return snapshot;
        }
        StageCancelSnapshot scopeSnapshot = scope.snapshot();
        Long durationMs = calculateDurationMs(snapshot.getStartedAt(), snapshot.getEndedAt());
        return PipelineStageSnapshot.builder()
            .key(snapshot.getKey())
            .label(snapshot.getLabel())
            .visible(snapshot.isVisible())
            .status(snapshot.getStatus())
            .startedAt(snapshot.getStartedAt())
            .endedAt(snapshot.getEndedAt())
            .failureReason(snapshot.getFailureReason())
            .durationMs(durationMs)
            .started(scopeSnapshot.started())
            .completed(scopeSnapshot.completed())
            .cancelled(scopeSnapshot.cancelled())
            .failed(scopeSnapshot.failed())
            .pending(scopeSnapshot.pending())
            .pendingSamples(limitPendingSamples(scopeSnapshot.pendingNames()))
            .failureReasons(scope.failureReasons())
            .build();
    }

    private Long calculateDurationMs(Long startedAt, Long endedAt) {
        if (startedAt == null) {
            return null;
        }
        long end = endedAt != null ? endedAt : System.currentTimeMillis();
        return Math.max(0L, end - startedAt);
    }

    private List<String> limitPendingSamples(List<String> pendingNames) {
        if (pendingNames == null || pendingNames.isEmpty()) {
            return List.of();
        }
        return pendingNames.stream()
            .sorted()
            .limit(MAX_PENDING_SAMPLES)
            .toList();
    }

    public interface ContextScope extends AutoCloseable {
        @Override
        void close();
    }
}
