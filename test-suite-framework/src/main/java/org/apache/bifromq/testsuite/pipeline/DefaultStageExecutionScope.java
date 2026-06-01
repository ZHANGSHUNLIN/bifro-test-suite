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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class DefaultStageExecutionScope implements StageExecutionScope {

    private final String stageName;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final CompletableFuture<StageResult> result = new CompletableFuture<>();
    private final ConcurrentMap<String, CompletableFuture<?>> futures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CancellableHandle> handles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> failureReasons = new ConcurrentHashMap<>();
    private final AtomicInteger started = new AtomicInteger(0);
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger cancelledHandles = new AtomicInteger(0);

    public DefaultStageExecutionScope(String stageName) {
        this.stageName = stageName;
    }

    @Override
    public String stageName() {
        return stageName;
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        for (Map.Entry<String, CancellableHandle> entry : handles.entrySet()) {
            try {
                entry.getValue().cancel();
                cancelledHandles.incrementAndGet();
            } catch (Exception e) {
                log.warn("Stage cancel handle failed, stage={}, handle={}", stageName, entry.getKey(), e);
            }
        }
        result.complete(StageResult.success("Stage cancelled"));
    }

    @Override
    public <T> CompletableFuture<T> track(String name, CompletableFuture<T> future) {
        started.incrementAndGet();
        futures.put(name, future);
        future.whenComplete((value, error) -> {
            completed.incrementAndGet();
            futures.remove(name);
            if (error != null) {
                failed.incrementAndGet();
            }
        });
        return future;
    }

    @Override
    public void register(String name, CancellableHandle handle) {
        handles.put(name, handle);
    }

    @Override
    public void complete(StageResult stageResult) {
        result.complete(stageResult);
    }

    @Override
    public void completeExceptionally(Throwable error) {
        result.completeExceptionally(error);
    }

    @Override
    public CompletableFuture<StageResult> result() {
        return result;
    }

    @Override
    public void recordFailureReason(String reasonType) {
        String key = reasonType == null || reasonType.isBlank() ? "unknown" : reasonType;
        failureReasons.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
    }

    @Override
    public Map<String, Integer> failureReasons() {
        Map<String, Integer> summary = new LinkedHashMap<>();
        failureReasons.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> summary.put(entry.getKey(), entry.getValue().get()));
        return summary;
    }

    @Override
    public StageCancelSnapshot snapshot() {
        List<String> pendingNames = new ArrayList<>(futures.keySet());
        int reasonFailures = failureReasons.values().stream()
            .mapToInt(AtomicInteger::get)
            .sum();
        return new StageCancelSnapshot(
            stageName,
            started.get(),
            completed.get(),
            cancelledHandles.get(),
            Math.max(failed.get(), reasonFailures),
            pendingNames.size(),
            pendingNames);
    }

    @Override
    public void close() {
        cancel();
    }
}
