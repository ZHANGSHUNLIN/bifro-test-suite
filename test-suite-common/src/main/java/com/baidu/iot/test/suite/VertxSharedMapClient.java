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
public class VertxSharedMapClient implements SharedMapClient {

    private final Vertx vertx;

    public VertxSharedMapClient(Vertx vertx) {
        this.vertx = vertx;
    }

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
        // Vert.x 5 API: getAsyncMap 只接受一个参数，返回 Future
        return vertx.sharedData().getAsyncMap(mapName);
    }
}
