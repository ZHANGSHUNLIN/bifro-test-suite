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

package org.apache.bifromq.testsuite.app.cluster.member;

import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.cluster.ClusterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MemberHealthMonitor {

    private static final Logger heartbeatLogger = LoggerFactory.getLogger("HEARTBEAT");

    private static final int MAX_RETRY_COUNT = 3;

    private static final long RETRY_INTERVAL_MS = 1000;

    private static final String HEARTBEAT_THREAD_NAME = "heartbeat-";

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final AtomicLong heartbeatCount = new AtomicLong(0);
    @Resource
    private Vertx vertx;
    @Resource
    private ClusterConfig clusterConfig;
    @Resource
    private MemberRegistry memberRegistry;

    private ScheduledExecutorService scheduler;

    public void start() {
        if (!started.compareAndSet(false, true)) {
            log.warn("Health monitor already started, skipping");
            return;
        }

        long interval = clusterConfig.getHeartbeatIntervalMillis();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, HEARTBEAT_THREAD_NAME + "scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::performHeartbeatWithRetry, 0, interval, TimeUnit.MILLISECONDS);

        log.info("Health monitor started, interval={}ms, thread={}", interval, HEARTBEAT_THREAD_NAME + "scheduler");
        heartbeatLogger.info("Health monitor started, interval={}ms", interval);
    }

    private void performHeartbeatWithRetry() {
        long startTime = System.currentTimeMillis();
        String localMemberId = memberRegistry.getLocalMemberId();
        long count = heartbeatCount.incrementAndGet();

        heartbeatLogger.info("Heartbeat #[{}] starting: memberId={}, timestamp={}", count, localMemberId, startTime);

        performRetry(0, startTime, localMemberId, count);
    }

    private void performRetry(int retryCount, long startTime, String memberId, long count) {
        memberRegistry.updateLocalHeartbeat()
            .onComplete(ar -> {
                long elapsedTime = System.currentTimeMillis() - startTime;

                if (ar.succeeded()) {
                    heartbeatLogger.info("Heartbeat #[{}] success: memberId={}, elapsedMs={}, retries={}",
                        count, memberId, elapsedTime, retryCount);
                    log.debug("Heartbeat #[{}] updated successfully, elapsedMs={}", count, elapsedTime);
                } else {
                    Throwable cause = ar.cause();
                    if (retryCount < MAX_RETRY_COUNT - 1) {

                        heartbeatLogger.warn(
                            "Heartbeat #[{}] failed (will retry {}/{}): memberId={}, elapsedMs={}, error={}",
                            count, retryCount + 1, MAX_RETRY_COUNT, memberId, elapsedTime, cause.getMessage());

                        scheduler.schedule(() -> performRetry(retryCount + 1, startTime, memberId, count),
                            RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    } else {
                        heartbeatLogger.error(
                            "Heartbeat #[{}] failed after {} retries: memberId={}, totalElapsedMs={}, error={}",
                            count, MAX_RETRY_COUNT, memberId, elapsedTime, cause.getMessage());
                        log.error("Heartbeat #[{}] update failed after {} retries", count, MAX_RETRY_COUNT, cause);
                    }
                }
            });
    }

    public void stop() {
        if (!started.compareAndSet(true, false)) {
            log.warn("Health monitor not started, skipping");
            return;
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {

                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            heartbeatLogger.info("Health monitor stopped");
        }
        log.info("Health monitor stopped");
    }

    public boolean isRunning() {
        return started.get() && scheduler != null && !scheduler.isShutdown();
    }
}