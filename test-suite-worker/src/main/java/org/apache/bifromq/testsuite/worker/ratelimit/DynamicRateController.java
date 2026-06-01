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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.qps.QpsStrategy;

@Slf4j
public class DynamicRateController {

    private static final long DEFAULT_UPDATE_INTERVAL_MS = 20L;

    private final String name;
    private final IRateLimiter rateLimiter;
    private final QpsStrategy strategy;
    private final long timeOriginMs;
    private final int minActiveRate;
    private final long updateIntervalMs;

    private ScheduledExecutorService scheduler;

    public DynamicRateController(String name, IRateLimiter rateLimiter, QpsStrategy strategy, long timeOriginMs,
                                 int minActiveRate) {
        this(name, rateLimiter, strategy, timeOriginMs, minActiveRate, DEFAULT_UPDATE_INTERVAL_MS);
    }

    public DynamicRateController(String name, IRateLimiter rateLimiter, QpsStrategy strategy, long timeOriginMs,
                                 int minActiveRate, long updateIntervalMs) {
        this.name = name;
        this.rateLimiter = rateLimiter;
        this.strategy = strategy;
        this.timeOriginMs = timeOriginMs;
        this.minActiveRate = minActiveRate;
        this.updateIntervalMs = updateIntervalMs;
    }

    public boolean start() {
        if (rateLimiter == null || strategy == null || !strategy.isDynamic()) {
            return false;
        }
        update(System.currentTimeMillis());
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, name + "-dynamic-rate");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> update(System.currentTimeMillis()),
            updateIntervalMs, updateIntervalMs, TimeUnit.MILLISECONDS);
        return true;
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            log.debug("{} dynamic rate scheduler stopped", name);
        }
    }

    private void update(long nowMs) {
        if (nowMs < timeOriginMs) {
            rateLimiter.setRate(0);
            return;
        }
        long elapsed = DynamicQpsClock.elapsedMs(nowMs, timeOriginMs);
        double strategyQps = strategy.currentQpsValue(elapsed);
        double effectiveQps = Math.max(minActiveRate, strategyQps);
        rateLimiter.setRate(effectiveQps);
        if (strategyQps <= 0) {
            log.debug("{} dynamic QPS strategy reached non-positive value: strategyQps={}, "
                    + "effectiveQps={}, elapsed={}ms",
                name, strategyQps, effectiveQps, elapsed);
        } else {
            log.debug("{} dynamic QPS updated: {} qps, elapsed={}ms", name, effectiveQps, elapsed);
        }
    }
}
