package com.baidu.iot.test.suite;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.shareddata.AsyncMap;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

@Slf4j
public class ShareDataManager {

    private final Map<ShareDataAddr, ShareMap<?, ?>> maps =
            new EnumMap<>(ShareDataAddr.class);

    public ShareDataManager(Vertx vertx) {

        for (ShareDataAddr addr : ShareDataAddr.values()) {
            maps.put(addr, new ShareMapImpl<>(vertx, addr.getAddr()));
        }
    }

    @SuppressWarnings("unchecked")
    public <K, V> ShareMap<K, V> map(ShareDataAddr addr) {
        return (ShareMap<K, V>) maps.get(addr);
    }

    // =================================================
    // ShareMap
    // =================================================

    public interface ShareMap<K, V> {

        KeyRef<K, V> key(K key);

        CompletableFuture<Map<K, V>> entries();

        CompletableFuture<Set<K>> keys();

        CompletableFuture<List<V>> values();
    }

    // =================================================
    // ShareMapImpl
    // =================================================

    static class ShareMapImpl<K, V> implements ShareMap<K, V> {

        private final CompletableFuture<AsyncMap<K, V>> mapFuture;

        ShareMapImpl(Vertx vertx, String name) {

            Future<AsyncMap<K, V>> f = vertx.sharedData().getClusterWideMap(name);
            this.mapFuture = f.toCompletionStage().toCompletableFuture();

            mapFuture.whenComplete((m, e) -> {
                if (e != null) {
                    log.error("AsyncMap init failed: {}", name, e);
                }
            });
        }

        CompletableFuture<AsyncMap<K, V>> map() {
            return mapFuture;
        }

        @Override
        public KeyRef<K, V> key(K key) {
            return new KeyRef<>(key, this);
        }

        @Override
        public CompletableFuture<Map<K, V>> entries() {

            return map().thenCompose(m ->
                    m.entries().toCompletionStage().toCompletableFuture());
        }

        @Override
        public CompletableFuture<Set<K>> keys() {

            return map().thenCompose(m ->
                    m.keys().toCompletionStage().toCompletableFuture());
        }

        @Override
        public CompletableFuture<List<V>> values() {

            return map().thenCompose(m ->
                    m.values().toCompletionStage().toCompletableFuture());
        }
    }

    // =================================================
    // KeyRef  (核心 DSL)
    // =================================================

    public static class KeyRef<K, V> {

        private final K key;
        private final ShareMapImpl<K, V> map;

        private CompletableFuture<V> stage;

        KeyRef(K key, ShareMapImpl<K, V> map) {
            this.key = key;
            this.map = map;
            stage = map.map()
                    .thenCompose(m ->
                            m.get(key)
                                    .toCompletionStage()
                                    .toCompletableFuture());
        }

        // -------------------------
        // 基础操作
        // -------------------------


        public KeyRef<K, V> putIfAbsent(V value) {

            stage = map.map()
                    .thenCompose(m ->
                            m.putIfAbsent(key, value)
                                    .toCompletionStage()
                                    .toCompletableFuture());

            return this;
        }

        public KeyRef<K, V> putIfAbsent(Supplier<V> value) {

            stage = map.map()
                    .thenCompose(m ->
                            m.putIfAbsent(key, value.get())
                                    .toCompletionStage()
                                    .toCompletableFuture());

            return this;
        }

        public KeyRef<K, V> replace(V value) {

            stage = map.map()
                    .thenCompose(m ->
                            m.replace(key, value)
                                    .toCompletionStage()
                                    .toCompletableFuture());

            return this;
        }

        public KeyRef<K, V> remove() {

            stage = map.map()
                    .thenCompose(m ->
                            m.remove(key)
                                    .toCompletionStage()
                                    .toCompletableFuture());

            return this;
        }

        // -------------------------
        // compute DSL
        // -------------------------

        public KeyRef<K, V> compute(Function<V, V> fn) {

            stage = stage.thenCompose(v -> {

                V newVal = fn.apply(v);

                return map.map()
                        .thenCompose(m ->
                                m.replace(key, newVal)
                                        .toCompletionStage()
                                        .toCompletableFuture());
            });

            return this;
        }

        public KeyRef<K, V> computeIfAbsent(Supplier<V> supplier) {

            stage = stage.thenCompose(v -> {

                if (v != null) {
                    return CompletableFuture.completedFuture(v);
                }

                V newVal = supplier.get();

                return map.map()
                        .thenCompose(m ->
                                m.putIfAbsent(key, newVal)
                                        .toCompletionStage()
                                        .toCompletableFuture());
            });

            return this;
        }

        // -------------------------
        // Future DSL
        // -------------------------

        public KeyRef<K, V> thenApply(Function<V, V> fn) {

            stage = stage.thenApply(fn);
            return this;
        }

        public KeyRef<K, V> thenCompose(Function<V, CompletableFuture<V>> fn) {

            stage = stage.thenCompose(fn);
            return this;
        }

        public KeyRef<K, V> thenAccept(Consumer<V> consumer) {

            stage.thenAccept(consumer);
            return this;
        }

        public KeyRef<K, V> onError(Consumer<Throwable> consumer) {

            stage.exceptionally(e -> {
                consumer.accept(e);
                return null;
            });

            return this;
        }

        // -------------------------
        // terminal
        // -------------------------

        public CompletableFuture<V> future() {
            return stage;
        }

        public K key() {
            return key;
        }
    }

    public static DeliveryOptions getLocalDeliveryOptions() {
        DeliveryOptions deliveryOptions = new DeliveryOptions();
        deliveryOptions.setLocalOnly(true);
        return deliveryOptions;
    }
}