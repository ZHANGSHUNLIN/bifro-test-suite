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

import com.google.common.util.concurrent.RateLimiter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GuavaRateLimiter implements IRateLimiter {

    private static final long MAX_PAUSE_WAIT_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final int MIN_RESUME_QPS = 1;

    private final RateLimiter rateLimiter;
    private volatile double currentPermitsPerSecond;

    private final ExecutorService executor;
    private final AtomicLong acquiredCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong totalWaitNanos = new AtomicLong(0);

    private volatile boolean paused = false;
    private volatile boolean disposed = false;

    public GuavaRateLimiter(int permitsPerSecond) {
        this((double) permitsPerSecond, null);
    }

    public GuavaRateLimiter(double permitsPerSecond) {
        this(permitsPerSecond, null);
    }

    public GuavaRateLimiter(int permitsPerSecond, ExecutorService executor) {
        this((double) permitsPerSecond, executor);
    }

    public GuavaRateLimiter(double permitsPerSecond, ExecutorService executor) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive: " + permitsPerSecond);
        }
        this.currentPermitsPerSecond = permitsPerSecond;
        this.rateLimiter = RateLimiter.create(permitsPerSecond);
        this.executor = executor != null ? executor
            : Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "guava-rate-limiter");
                t.setDaemon(true);
                return t;
            });
        log.debug("Created Guava rate limiter: {} QPS", permitsPerSecond);
    }

    @Override
    public int getPermitsPerSecond() {
        return (int) Math.round(currentPermitsPerSecond);
    }

    @Override
    public double getPermitsPerSecondValue() {
        return currentPermitsPerSecond;
    }

    @Override
    public long getIntervalNanos() {
        double qps = Math.max(Double.MIN_NORMAL, currentPermitsPerSecond);
        return Math.max(1L, Math.round(TimeUnit.SECONDS.toNanos(1) / qps));
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
        totalWaitNanos.set(0);
    }

    @Override
    public long getTotalWaitNanos() {
        return totalWaitNanos.get();
    }

    @Override
    public void setRate(int newPermitsPerSecond) {
        setRate((double) newPermitsPerSecond);
    }

    @Override
    public void setRate(double newPermitsPerSecond) {
        if (newPermitsPerSecond <= 0) {

            paused = true;
        } else {
            currentPermitsPerSecond = newPermitsPerSecond;
            rateLimiter.setRate(newPermitsPerSecond);
            paused = false;
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        paused = false;
        executor.shutdownNow();
    }

    @Override
    public CompletableFuture<Void> executeWithRateLimit(int total,
                                                        Function<Integer, CompletableFuture<Void>> action) {
        if (total <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                runLoop(total, action);
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        return result;
    }

    @Override
    public CompletableFuture<Void> executeContinuously(Function<Long, CompletableFuture<Void>> action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                runContinuousLoop(action);
                result.complete(null);
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        return result;
    }

    private void runLoop(int total, Function<Integer, CompletableFuture<Void>> action) {
        for (int i = 0; i < total; i++) {
            waitIfPaused(true);
            if (disposed || Thread.currentThread().isInterrupted()) {
                return;
            }

            long waitStart = System.nanoTime();
            try {
                rateLimiter.acquire();
            } finally {
                totalWaitNanos.addAndGet(Math.max(0L, System.nanoTime() - waitStart));
            }
            if (Thread.currentThread().isInterrupted() || disposed) {
                return;
            }

            final int index = i;
            try {
                CompletableFuture<Void> future = action.apply(index);
                future.whenComplete((v, ex) -> {
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
        }
        log.debug("Guava rate limit execution completed. Acquired: {}, Failed: {}",
            acquiredCount.get(), failedCount.get());
    }

    private void runContinuousLoop(Function<Long, CompletableFuture<Void>> action) {
        long index = 0L;
        while (!disposed && !Thread.currentThread().isInterrupted()) {
            waitIfPaused(false);
            if (disposed || Thread.currentThread().isInterrupted()) {
                return;
            }

            long waitStart = System.nanoTime();
            try {
                rateLimiter.acquire();
            } finally {
                totalWaitNanos.addAndGet(Math.max(0L, System.nanoTime() - waitStart));
            }
            if (Thread.currentThread().isInterrupted() || disposed) {
                return;
            }

            final long actionIndex = index++;
            try {
                CompletableFuture<Void> future = action.apply(actionIndex);
                future.whenComplete((v, ex) -> {
                    if (ex == null) {
                        acquiredCount.incrementAndGet();
                    } else {
                        failedCount.incrementAndGet();
                        log.debug("Action failed at index {}: {}", actionIndex, ex.getMessage());
                    }
                });
            } catch (Exception e) {
                failedCount.incrementAndGet();
                log.error("Action threw exception at index {}", actionIndex, e);
            }
        }
    }

    private void waitIfPaused(boolean autoResume) {
        long pauseStartNanos = -1L;
        while (paused && !disposed) {
            if (autoResume) {
                if (pauseStartNanos < 0) {
                    pauseStartNanos = System.nanoTime();
                } else {
                    long pausedNanos = System.nanoTime() - pauseStartNanos;
                    if (pausedNanos >= MAX_PAUSE_WAIT_NANOS) {
                        log.warn("Rate limiter paused too long ({} ms), auto-resume with {} QPS",
                            TimeUnit.NANOSECONDS.toMillis(pausedNanos), MIN_RESUME_QPS);
                        setRate(MIN_RESUME_QPS);
                        return;
                    }
                }
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
