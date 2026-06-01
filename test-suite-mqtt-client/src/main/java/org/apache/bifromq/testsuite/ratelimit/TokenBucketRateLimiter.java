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

package org.apache.bifromq.testsuite.ratelimit;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TokenBucketRateLimiter implements IRateLimiter {

    /**
     * Use a shared bounded scheduler for timer ticks.
     * Avoid creating massive RxCachedThreadScheduler threads under large client counts.
     */
    private static final Scheduler SHARED_TIMER_SCHEDULER = Schedulers.computation();

    private final AtomicReference<Disposable> disposableRef = new AtomicReference<>();
    private final AtomicLong acquiredCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong emitIndex = new AtomicLong(0);

    private volatile long intervalNanos;
    private volatile double permitsPerSecond;

    private volatile Function<Long, CompletableFuture<Void>> continuousAction;

    public TokenBucketRateLimiter(int permitsPerSecond) {
        this((double) permitsPerSecond);
    }

    public TokenBucketRateLimiter(double permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive: " + permitsPerSecond);
        }
        this.permitsPerSecond = permitsPerSecond;
        this.intervalNanos = toIntervalNanos(permitsPerSecond);
        log.debug("Created rate limiter: {} QPS (intervalNanos={})", permitsPerSecond, intervalNanos);
    }

    private static long toIntervalNanos(double permitsPerSecond) {
        if (!Double.isFinite(permitsPerSecond) || permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive: " + permitsPerSecond);
        }
        return Math.max(1L, Math.round(1_000_000_000D / permitsPerSecond));
    }

    @Override
    public int getPermitsPerSecond() {
        return (int) Math.round(permitsPerSecond);
    }

    public double getPermitsPerSecondValue() {
        return permitsPerSecond;
    }

    @Override
    public long getIntervalNanos() {
        return intervalNanos;
    }

    @Override
    public void setRate(int newPermitsPerSecond) {
        setRate((double) newPermitsPerSecond);
    }

    public void setRate(double newPermitsPerSecond) {
        if (newPermitsPerSecond <= 0) {
            log.warn("setRate ignored: permitsPerSecond must be positive, got {}", newPermitsPerSecond);
            return;
        }
        if (!Double.isFinite(newPermitsPerSecond)) {
            log.warn("setRate ignored: permitsPerSecond must be finite, got {}", newPermitsPerSecond);
            return;
        }
        if (Double.compare(newPermitsPerSecond, this.permitsPerSecond) == 0) {
            return;
        }
        this.permitsPerSecond = newPermitsPerSecond;
        this.intervalNanos = toIntervalNanos(newPermitsPerSecond);
        log.debug("Rate updated to {} QPS (intervalNanos={})", newPermitsPerSecond, intervalNanos);
        if (continuousAction != null) {
            rebuildContinuousSubscription(continuousAction);
        }
    }

    public void startContinuous(Function<Long, CompletableFuture<Void>> action) {
        this.continuousAction = action;
        this.emitIndex.set(0);
        rebuildContinuousSubscription(action);
    }

    private void rebuildContinuousSubscription(Function<Long, CompletableFuture<Void>> action) {
        long currentIntervalNanos = this.intervalNanos;
        Disposable newDisposable = Observable
            .interval(0, currentIntervalNanos, TimeUnit.NANOSECONDS, SHARED_TIMER_SCHEDULER)
            .subscribe(
                tick -> {
                    long idx = emitIndex.getAndIncrement();
                    try {
                        action.apply(idx).whenComplete((v, ex) -> {
                            if (ex == null) {
                                acquiredCount.incrementAndGet();
                            } else {
                                failedCount.incrementAndGet();
                                log.debug("Continuous action failed at index {}: {}", idx, ex.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        failedCount.incrementAndGet();
                        log.error("Continuous action threw at index {}", idx, e);
                    }
                },
                error -> log.error("Continuous rate limiter stream error", error)
            );

        Disposable old = disposableRef.getAndSet(newDisposable);
        if (old != null && !old.isDisposed()) {
            old.dispose();
        }
    }

    @Override
    public CompletableFuture<Void> executeWithRateLimit(int total,
                                                        Function<Integer, CompletableFuture<Void>> action) {
        if (total <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        long currentIntervalNanos = this.intervalNanos;

        Disposable d = Observable.interval(currentIntervalNanos, TimeUnit.NANOSECONDS, SHARED_TIMER_SCHEDULER)
            .take(total)
            .map(Long::intValue)
            .subscribe(
                index -> {
                    try {
                        action.apply(index).whenComplete((v, ex) -> {
                            if (ex == null) {
                                acquiredCount.incrementAndGet();
                            } else {
                                failedCount.incrementAndGet();
                                log.debug("Action failed at index {}: {}", index, ex.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        failedCount.incrementAndGet();
                        log.error("Action threw exception at index {}", index, e);
                    }
                },
                error -> {
                    log.error("Rate limit execution failed", error);
                    result.completeExceptionally(error);
                },
                () -> {
                    result.complete(null);
                    log.debug("Batch execution completed. acquired={}, failed={}",
                        acquiredCount.get(), failedCount.get());
                }
            );
        disposableRef.set(d);
        return result;
    }

    @Override
    public long getAcquiredCount() {
        return acquiredCount.get();
    }

    @Override
    public long getFailedCount() {
        return failedCount.get();
    }

    @Override
    public void resetMetrics() {
        acquiredCount.set(0);
        failedCount.set(0);
    }

    @Override
    public long getTotalWaitNanos() {
        return 0;
    }

    @Override
    public void dispose() {
        continuousAction = null;
        Disposable d = disposableRef.getAndSet(null);
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
    }
}
