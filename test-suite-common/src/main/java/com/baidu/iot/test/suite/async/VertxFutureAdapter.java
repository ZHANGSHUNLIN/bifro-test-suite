
package com.baidu.iot.test.suite.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongPredicate;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter for converting Vert.x async operations to CompletableFuture.
 */
@Slf4j
public class VertxFutureAdapter {

    private VertxFutureAdapter() {
    }

    /**
     * Convert Vert.x timer to CompletableFuture.
     *
     * @param vertx Vert.x instance
     * @param delay delay duration
     * @param unit time unit
     * @return CompletableFuture that completes when timer fires
     */
    public static CompletableFuture<Void> timerToFuture(Vertx vertx, long delay, TimeUnit unit) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        long timerId = vertx.setTimer(unit.toMillis(delay), id -> {
            future.complete(null);
        });
        future.whenComplete((v, e) -> {
            if (e != null) {
                vertx.cancelTimer(timerId);
            }
        });
        return future;
    }

    /**
     * Convert Vert.x periodic timer to CompletableFuture that completes based on stop condition.
     *
     * @param vertx Vert.x instance
     * @param period period duration
     * @param unit time unit
     * @param action action to execute periodically
     * @param stopCondition condition to stop and complete future
     * @return CompletableFuture that completes when stop condition is met
     */
    public static CompletableFuture<Void> periodicToFuture(Vertx vertx, long period,
                                                        TimeUnit unit, Consumer<Long> action,
                                                        LongPredicate stopCondition) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        long timerId = vertx.setPeriodic(unit.toMillis(period), id -> {
            action.accept(id);
            if (stopCondition.test(id)) {
                vertx.cancelTimer(id);
                future.complete(null);
            }
        });
        future.whenComplete((v, e) -> {
            vertx.cancelTimer(timerId);
        });
        return future;
    }

    /**
     * Convert Vert.x EventBus consumer to CompletableFuture.
     *
     * @param vertx Vert.x instance
     * @param address event bus address
     * @param messageType expected message type
     * @param <T> message type
     * @return MessageConsumer that can be used to unregister
     */
    public static <T> io.vertx.core.eventbus.MessageConsumer<T> eventToFuture(
            Vertx vertx, String address, Class<T> messageType,
            CompletableFuture<T> future) {
        io.vertx.core.eventbus.MessageConsumer<T> consumer = vertx.eventBus()
                .consumer(address, msg -> {
                    T body = msg.body();
                    if (body != null) {
                        future.complete(body);
                    }
                });
        return consumer;
    }

    /**
     * Convert Vert.x EventBus consumer to CompletableFuture with timeout.
     *
     * @param vertx Vert.x instance
     * @param address event bus address
     * @param messageType expected message type
     * @param timeout timeout duration
     * @param timeoutUnit timeout time unit
     * @param <T> message type
     * @return MessageConsumer that can be used to unregister
     */
    public static <T> io.vertx.core.eventbus.MessageConsumer<T> eventToFutureWithTimeout(
            Vertx vertx, String address, Class<T> messageType,
            long timeout, TimeUnit timeoutUnit,
            CompletableFuture<T> future) {
        io.vertx.core.eventbus.MessageConsumer<T> consumer = vertx.eventBus()
                .consumer(address, msg -> {
                    T body = msg.body();
                    if (body != null && !future.isDone()) {
                        future.complete(body);
                    }
                });

        // Set timeout
        timerToFuture(vertx, timeout, timeoutUnit)
                .thenAccept(v -> {
                    if (!future.isDone()) {
                        consumer.unregister();
                        future.completeExceptionally(
                                new java.util.concurrent.TimeoutException(
                                        "Timeout waiting for event on address: " + address));
                    }
                });

        return consumer;
    }
}
