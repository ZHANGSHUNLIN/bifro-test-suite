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

package org.apache.bifromq.testsuite.app.cluster.shared;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HazelcastDataManager {

    private final Map<ShareDataAddr, IMapWrapper<?, ?>> mapWrappers = new ConcurrentHashMap<>();

    private final HazelcastInstance hazelcastInstance;

    public HazelcastDataManager(HazelcastInstance hazelcastInstance) {
        if (hazelcastInstance == null) {
            throw new IllegalArgumentException("hazelcastInstance must not be null");
        }
        this.hazelcastInstance = hazelcastInstance;
        log.info("HazelcastDataManager initialized with instance: {}", hazelcastInstance.getName());
    }

    @SuppressWarnings("unchecked")
    public <K, V> IMapWrapper<K, V> map(ShareDataAddr addr) {
        return (IMapWrapper<K, V>) mapWrappers.computeIfAbsent(addr,
            a -> new IMapWrapper<>(hazelcastInstance, a.getAddr()));
    }

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

        public KeyRef<K, V> atomicCompute(Function<V, V> fn) {
            this.stage = CompletableFuture.supplyAsync(() ->
                wrapper.getIMap().compute(key, (k, v) -> fn.apply(v)));
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
}
