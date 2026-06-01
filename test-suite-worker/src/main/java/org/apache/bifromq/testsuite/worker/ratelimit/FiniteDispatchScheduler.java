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

package org.apache.bifromq.testsuite.worker.ratelimit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class FiniteDispatchScheduler {

    private static final long DEFAULT_TICK_MS = 20L;

    private final String stageName;
    private final String itemName;
    private final int totalCount;
    private final FiniteDispatchPlan dispatchPlan;
    private final IntFunction<CompletableFuture<Void>> action;
    private final Runnable completionAction;
    private final AtomicInteger dispatchedCount = new AtomicInteger(0);
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicBoolean stageCompleted = new AtomicBoolean(false);
    private final AtomicBoolean endCompensated = new AtomicBoolean(false);
    private final long tickMs;
    private final long startMs;
    private ScheduledExecutorService executor;

    public FiniteDispatchScheduler(String stageName, String itemName, int totalCount,
                                   FiniteDispatchPlan dispatchPlan,
                                   IntFunction<CompletableFuture<Void>> action,
                                   Runnable completionAction) {
        this(stageName, itemName, totalCount, dispatchPlan, action, completionAction, DEFAULT_TICK_MS);
    }

    FiniteDispatchScheduler(String stageName, String itemName, int totalCount,
                            FiniteDispatchPlan dispatchPlan,
                            IntFunction<CompletableFuture<Void>> action,
                            Runnable completionAction,
                            long tickMs) {
        if (totalCount <= 0) {
            throw new IllegalArgumentException("totalCount must be positive: " + totalCount);
        }
        if (dispatchPlan == null) {
            throw new IllegalArgumentException("dispatchPlan must not be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (completionAction == null) {
            throw new IllegalArgumentException("completionAction must not be null");
        }
        this.stageName = stageName;
        this.itemName = itemName;
        this.totalCount = totalCount;
        this.dispatchPlan = dispatchPlan;
        this.action = action;
        this.completionAction = completionAction;
        this.tickMs = tickMs;
        this.startMs = System.currentTimeMillis();
    }

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, stageName + "-finite-dispatch");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::onTick, 0, tickMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    int dispatchedCount() {
        return dispatchedCount.get();
    }

    int completedCount() {
        return completedCount.get();
    }

    void onTick() {
        if (stageCompleted.get()) {
            return;
        }
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - startMs);
        int targetCount = dispatchPlan.expectedCountAt(elapsedMs);
        if (dispatchPlan.isEnded(elapsedMs) && targetCount < totalCount) {
            targetCount = totalCount;
            if (endCompensated.compareAndSet(false, true)) {
                log.warn("{} profile ended before all {} were dispatched. "
                        + "Compensating remaining work immediately: planned={}, total={}, elapsed={}ms",
                    stageName, itemName, dispatchPlan.plannedTotalCount(), totalCount, elapsedMs);
            }
        }

        int budget = Math.max(0, targetCount - dispatchedCount.get());
        for (int i = 0; i < budget; i++) {
            int index = dispatchedCount.getAndIncrement();
            if (index >= totalCount) {
                break;
            }
            try {
                CompletableFuture<Void> future = action.apply(index);
                future.whenComplete((ignored, error) -> {
                    completedCount.incrementAndGet();
                    completeIfDone();
                });
            } catch (Exception e) {
                completedCount.incrementAndGet();
                log.warn("{} finite dispatch action failed before returning a future, index={}",
                    stageName, index, e);
                completeIfDone();
            }
        }
        completeIfDone();
    }

    private void completeIfDone() {
        if (dispatchedCount.get() >= totalCount && completedCount.get() >= totalCount
            && stageCompleted.compareAndSet(false, true)) {
            stop();
            completionAction.run();
        }
    }
}
