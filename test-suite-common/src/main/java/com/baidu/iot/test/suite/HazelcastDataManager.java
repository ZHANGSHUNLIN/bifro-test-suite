package com.baidu.iot.test.suite;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 基于 Hazelcast IMap 的分布式数据管理器，替代 Vert.x AsyncMap
 */
@Slf4j
public class HazelcastDataManager {

    private final Map<ShareDataAddr, IMapWrapper<?, ?>> mapWrappers = new ConcurrentHashMap<>();

    private final HazelcastInstance hazelcastInstance;

    public HazelcastDataManager(Vertx vertx) {
        Set<HazelcastInstance> instances = Hazelcast.getAllHazelcastInstances();
        if (instances.isEmpty()) {
            throw new IllegalStateException("No Hazelcast instances found");
        }
        this.hazelcastInstance = instances.iterator().next();
        log.info("HazelcastDataManager initialized with instance: {}", hazelcastInstance.getName());
    }

    @SuppressWarnings("unchecked")
    public <K, V> IMapWrapper<K, V> map(ShareDataAddr addr) {
        return (IMapWrapper<K, V>) mapWrappers.computeIfAbsent(addr,
                a -> new IMapWrapper<>(hazelcastInstance, a.getAddr()));
    }

    /**
     * IMap 包装器，提供异步 API
     */
    public static class IMapWrapper<K, V> {

        private final IMap<K, V> imap;

        IMapWrapper(HazelcastInstance hazelcast, String name) {
            this.imap = hazelcast.getMap(name);
        }

        public KeyRef<K, V> key(K key) {
            return new KeyRef<>(key, this);
        }

        public CompletableFuture<Map<K, V>> entries() {
            return CompletableFuture.supplyAsync(() -> new HashMap<>(imap));
        }

        public CompletableFuture<Set<K>> keys() {
            return CompletableFuture.supplyAsync(() -> new HashSet<>(imap.keySet()));
        }

        public CompletableFuture<List<V>> values() {
            return CompletableFuture.supplyAsync(() -> new ArrayList<>(imap.values()));
        }

        IMap<K, V> getIMap() {
            return imap;
        }
    }

    /**
     * KeyRef 类，提供 DSL 风格的操作
     */
    public static class KeyRef<K, V> {

        private final K key;
        private final IMapWrapper<K, V> wrapper;
        private CompletableFuture<V> stage;

        KeyRef(K key, IMapWrapper<K, V> wrapper) {
            this.key = key;
            this.wrapper = wrapper;
            this.stage = CompletableFuture.supplyAsync(() -> wrapper.getIMap().get(key));
        }

        public KeyRef<K, V> putIfAbsent(V value) {
            this.stage = CompletableFuture.supplyAsync(() -> wrapper.getIMap().putIfAbsent(key, value));
            return this;
        }

        public KeyRef<K, V> putIfAbsent(Supplier<V> value) {
            this.stage = CompletableFuture.supplyAsync(() ->
                    wrapper.getIMap().putIfAbsent(key, value.get()));
            return this;
        }

        public KeyRef<K, V> replace(V value) {
            this.stage = CompletableFuture.supplyAsync(() -> wrapper.getIMap().replace(key, value));
            return this;
        }

        public KeyRef<K, V> remove() {
            this.stage = CompletableFuture.supplyAsync(() -> wrapper.getIMap().remove(key));
            return this;
        }

        public KeyRef<K, V> compute(Function<V, V> fn) {
            this.stage = stage.thenCompose(v -> {
                V newVal = fn.apply(v);
                return CompletableFuture.supplyAsync(() -> wrapper.getIMap().replace(key, newVal));
            });
            return this;
        }

        public KeyRef<K, V> computeIfAbsent(Supplier<V> supplier) {
            this.stage = stage.thenCompose(v -> {
                if (v != null) {
                    return CompletableFuture.completedFuture(v);
                }
                V newVal = supplier.get();
                return CompletableFuture.supplyAsync(() -> wrapper.getIMap().putIfAbsent(key, newVal));
            });
            return this;
        }

        public KeyRef<K, V> thenApply(Function<V, V> fn) {
            this.stage = stage.thenApply(fn);
            return this;
        }

        public KeyRef<K, V> thenCompose(Function<V, CompletableFuture<V>> fn) {
            this.stage = stage.thenCompose(fn);
            return this;
        }

        public KeyRef<K, V> thenAccept(Consumer<V> consumer) {
            this.stage = stage.thenCompose(v -> {
                consumer.accept(v);
                return CompletableFuture.completedFuture(v);
            });
            return this;
        }

        public KeyRef<K, V> onError(Consumer<Throwable> consumer) {
            this.stage.exceptionally(e -> {
                consumer.accept(e);
                return null;
            });
            return this;
        }

        public CompletableFuture<V> future() {
            return stage;
        }

        public K key() {
            return key;
        }
    }

    /**
     * ShareDataAddr 枚举，迁移自 test-suite-common
     */
    @Getter
    public enum ShareDataAddr {
        CLUSTER_TASK_CONFIGS("cluster-task-configs"),
        NODE_TASK_CONFIGS("node-task-configs"),
        FINISH_NODE_TASKS("finish-node-tasks"),
        CLUSTER_NODE_INFO("cluster-node-info"),
        BROKER_MAP_NAME("broker-map"),
        TASK_METADATA("task-metadata"),
        BROKER_TASK_MAPPING("broker-task-mapping");

        private final String addr;

        ShareDataAddr(String addr) {
            this.addr = addr;
        }

    }
}
