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

package org.apache.bifromq.testsuite.worker.pipeline.publish;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.PubMqttClientTask;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.ratelimit.DynamicRateController;
import org.apache.bifromq.testsuite.worker.ratelimit.GuavaRateLimiter;
import org.apache.bifromq.testsuite.worker.ratelimit.IRateLimiter;

@Slf4j
public class NodePublishScheduler {

    private static final int MIN_PUBLISH_DYNAMIC_QPS = 0;

    private final QpsStrategy qpsStrategy;
    private final long timeOriginMs;
    private final List<PubMqttClientTask> clients;
    private final IRateLimiter rateLimiter;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong(0);
    private DynamicRateController dynamicRateController;
    private int clientCursor = 0;

    public NodePublishScheduler(QpsStrategy qpsStrategy, long timeOriginMs, Collection<PubMqttClientTask> clients) {
        this(qpsStrategy, timeOriginMs, clients, new GuavaRateLimiter(initialPublishQps(qpsStrategy)));
    }

    NodePublishScheduler(QpsStrategy qpsStrategy, long timeOriginMs, Collection<PubMqttClientTask> clients,
                         IRateLimiter rateLimiter) {
        if (qpsStrategy == null) {
            throw new IllegalArgumentException("qpsStrategy must not be null");
        }
        if (clients == null || clients.isEmpty()) {
            throw new IllegalArgumentException("clients must not be empty");
        }
        if (rateLimiter == null) {
            throw new IllegalArgumentException("rateLimiter must not be null");
        }
        this.qpsStrategy = qpsStrategy;
        this.timeOriginMs = timeOriginMs;
        this.clients = new ArrayList<>(clients);
        this.rateLimiter = rateLimiter;
    }

    public void start() {
        if (stopped.get() || !started.compareAndSet(false, true)) {
            return;
        }
        if (qpsStrategy.isDynamic()) {
            dynamicRateController = new DynamicRateController(
                "publish", rateLimiter, qpsStrategy, timeOriginMs, MIN_PUBLISH_DYNAMIC_QPS);
            dynamicRateController.start();
        }
        rateLimiter.executeContinuously(index -> {
            if (stopped.get()) {
                return CompletableFuture.completedFuture(null);
            }
            PubMqttClientTask client = nextClient();
            long sequenceNo = sequence.getAndIncrement();
            return client.publishOnce(sequenceNo);
        });
    }

    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            if (dynamicRateController != null) {
                dynamicRateController.stop();
            }
            rateLimiter.dispose();
        }
    }

    long emittedCount() {
        return sequence.get();
    }

    long timeOriginMs() {
        return timeOriginMs;
    }

    synchronized PubMqttClientTask nextClient() {
        PubMqttClientTask client = clients.get(clientCursor);
        clientCursor = (clientCursor + 1) % clients.size();
        return client;
    }

    private static double initialPublishQps(QpsStrategy qpsStrategy) {
        if (qpsStrategy == null) {
            throw new IllegalArgumentException("qpsStrategy must not be null");
        }
        return Math.max(1D, qpsStrategy.currentQpsValue(0L));
    }
}
