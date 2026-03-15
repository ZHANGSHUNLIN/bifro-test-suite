// VertxSharedMapClient.java
package com.baidu.iot.test.suite;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Vert.x实现的共享Map客户端
 */
@Slf4j
public record VertxSharedMapClient(Vertx vertx) implements SharedMapClient {

    @Override
    public <K, V> CompletableFuture<Void> put(String mapName, K key, V value) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        getMapInternal(mapName)
                .compose(map -> map.put(key, value))
                .onComplete(result -> {
                    if (result.succeeded()) {
                        log.debug("Map: {}, key: {}, value: {} added successfully", mapName, key, value);
                        future.complete(null);
                    } else {
                        log.error("Failed to put key: {} to map: {}", key, mapName, result.cause());
                        future.completeExceptionally(result.cause());
                    }
                });

        return future;
    }

    @Override
    public <K, V> CompletableFuture<V> get(String mapName, K key) {
        CompletableFuture<V> future = new CompletableFuture<>();

        this.<K, V>getMapInternal(mapName)
                .compose(map -> map.get(key))
                .onComplete(result -> {
                    if (result.succeeded()) {
                        V value = result.result();
                        log.trace("Map: {}, key: {} queried successfully, value: {}", mapName, key, value);
                        future.complete(value);
                    } else {
                        log.error("Failed to get key: {} from map: {}", key, mapName, result.cause());
                        future.completeExceptionally(result.cause());
                    }
                });

        return future;
    }

    @Override
    public <K, V> CompletableFuture<V> remove(String mapName, K key) {
        CompletableFuture<V> future = new CompletableFuture<>();

        this.<K, V>getMapInternal(mapName)
                .compose(map -> map.remove(key))
                .onComplete(result -> {
                    if (result.succeeded()) {
                        V removedValue = result.result();
                        log.debug("Map: {}, key: {} removed successfully", mapName, key);
                        future.complete(removedValue);
                    } else {
                        log.error("Failed to remove key: {} from map: {}", key, mapName, result.cause());
                        future.completeExceptionally(result.cause());
                    }
                });

        return future;
    }

    @Override
    public <K, V> CompletableFuture<AsyncMap<K, V>> getMap(String mapName) {
        CompletableFuture<AsyncMap<K, V>> future = new CompletableFuture<>();

        this.<K, V>getMapInternal(mapName)
                .onComplete(result -> {
                    if (result.succeeded()) {
                        log.trace("Map: {} queried successfully", mapName);
                        future.complete(result.result());
                    } else {
                        log.error("Failed to get map: {}", mapName, result.cause());
                        future.completeExceptionally(result.cause());
                    }
                });

        return future;
    }

    /**
     * 内部方法：获取AsyncMap，使用Vert.x的Future简化回调
     */
    private <K, V> Future<AsyncMap<K, V>> getMapInternal(String mapName) {
        return Future.future(promise ->
                vertx.sharedData().<K, V>getAsyncMap(mapName, result -> {
                    if (result.failed()) {
                        promise.fail(result.cause());
                    } else {
                        promise.complete(result.result());
                    }
                }));
    }
}