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

package org.apache.bifromq.testsuite.app.shutdown;

import java.util.Comparator;
import java.util.List;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NodeShutdownManager implements SmartLifecycle {

    private final GracefulShutdownProperties properties;
    private final List<ShutdownParticipant> participants;
    private final AtomicReference<NodeShutdownState> state = new AtomicReference<>(NodeShutdownState.RUNNING);
    private final AtomicReference<CompletableFuture<Void>> shutdownFuture = new AtomicReference<>();

    public NodeShutdownManager(GracefulShutdownProperties properties, List<ShutdownParticipant> participants) {
        this.properties = properties;
        this.participants = participants.stream()
            .sorted(Comparator.comparingInt(ShutdownParticipant::order))
            .toList();
    }

    @Override
    public void start() {
        state.set(NodeShutdownState.RUNNING);
    }

    @Override
    public void stop() {
        shutdown().join();
    }

    @Override
    public void stop(Runnable callback) {
        shutdown().whenComplete((ignored, error) -> {
            if (error != null) {
                log.warn("Node graceful shutdown finished with error", error);
            }
            callback.run();
        });
    }

    @Override
    public boolean isRunning() {
        return state.get() == NodeShutdownState.RUNNING;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    public NodeShutdownState state() {
        return state.get();
    }

    public CompletableFuture<Void> shutdown() {
        CompletableFuture<Void> existing = shutdownFuture.get();
        if (existing != null) {
            return existing;
        }
        CompletableFuture<Void> created = doShutdown();
        if (shutdownFuture.compareAndSet(null, created)) {
            return created;
        }
        return shutdownFuture.get();
    }

    private CompletableFuture<Void> doShutdown() {
        if (!properties.isGracefulEnabled()) {
            state.set(NodeShutdownState.COMPLETED);
            return CompletableFuture.completedFuture(null);
        }
        state.set(NodeShutdownState.SHUTTING_DOWN);
        log.info("Node graceful shutdown started, participants={}",
            participants.stream().map(ShutdownParticipant::name).toList());
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (ShutdownParticipant participant : participants) {
            chain = chain.thenCompose(ignored -> runParticipant(participant));
        }
        return chain.whenComplete((ignored, error) -> {
            state.set(NodeShutdownState.COMPLETED);
            if (error == null) {
                log.info("Node graceful shutdown completed");
            }
        });
    }

    private CompletableFuture<Void> runParticipant(ShutdownParticipant participant) {
        log.info("Shutdown participant started: {}", participant.name());
        Duration timeout = participant.timeout(properties);
        return participant.shutdown()
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .whenComplete((ignored, error) -> {
                if (error == null) {
                    log.info("Shutdown participant completed: {}", participant.name());
                } else {
                    log.warn("Shutdown participant failed: {}", participant.name(), error);
                }
            });
    }
}
