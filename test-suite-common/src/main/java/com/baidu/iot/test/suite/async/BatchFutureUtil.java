
package com.baidu.iot.test.suite.async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

/**
 * Utilities for batch operations with CompletableFuture.
 */
@Slf4j
public class BatchFutureUtil {

    private BatchFutureUtil() {
    }

    /**
     * Wait for a specified number of successful results.
     *
     * @param futures list of futures to wait for
     * @param requiredSuccess required number of successful results
     * @param <T> result type
     * @return CompletableFuture that completes when required successes are reached or all fail
     */
    public static <T> CompletableFuture<Void> waitForSuccess(
            List<CompletableFuture<T>> futures,
            int requiredSuccess) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger completed = new AtomicInteger(0);

        for (CompletableFuture<T> future : futures) {
            future.whenComplete((v, e) -> {
                if (e == null) {
                    successCount.incrementAndGet();
                }

                int completedCount = completed.incrementAndGet();

                if (successCount.get() >= requiredSuccess) {
                    log.info("Required {} successes achieved (total: {})",
                            requiredSuccess, successCount.get());
                    result.complete(null);
                } else if (completedCount == futures.size()) {
                    result.completeExceptionally(new RuntimeException(
                                    "Only " + successCount.get() + " successes, required " + requiredSuccess));
                }
            });
        }

        return result;
    }

    /**
     * Wait for all futures with a timeout on each.
     *
     * @param futures list of futures
     * @param timeoutMs timeout in milliseconds
     * @param <T> result type
     * @return CompletableFuture that completes when all are done or timeout occurs
     */
    public static <T> CompletableFuture<List<T>> allOfWithTimeout(
            List<CompletableFuture<T>> futures,
            long timeoutMs) {
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        CompletableFuture<Void> timeoutFuture = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        CompletableFuture<Void> allDone = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        CompletableFuture.anyOf(allDone, timeoutFuture)
                .thenAccept(v -> timeoutFuture.cancel(true));

        return allDone.thenApply(v -> {
            List<T> results = new ArrayList<>();
            for (CompletableFuture<T> future : futures) {
                if (future.isDone() && !future.isCompletedExceptionally()) {
                    results.add(future.join());
                }
            }
            return results;
        });
    }

    /**
     * Complete a future when a condition is met.
     *
     * @param condition condition to check
     * @param checkIntervalMs check interval in milliseconds
     * @param timeoutMs timeout in milliseconds
     * @return CompletableFuture that completes when condition is met or timeout occurs
     */
    public static CompletableFuture<Void> waitForCondition(
            Predicate<Void> condition,
            long checkIntervalMs,
            long timeoutMs) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        long startTime = System.currentTimeMillis();

        java.util.concurrent.ScheduledExecutorService scheduler =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

        Runnable checker = new Runnable() {
            @Override
            public void run() {
                if (result.isDone()) {
                    scheduler.shutdown();
                    return;
                }

                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    scheduler.shutdown();
                    result.completeExceptionally(
                            new java.util.concurrent.TimeoutException("Timeout waiting for condition"));
                    return;
                }

                try {
                    if (condition.test(null)) {
                        scheduler.shutdown();
                        result.complete(null);
                    }
                } catch (Exception e) {
                    scheduler.shutdown();
                    result.completeExceptionally(e);
                }
            }
        };

        scheduler.scheduleAtFixedRate(checker, 0, checkIntervalMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        return result;
    }
}
