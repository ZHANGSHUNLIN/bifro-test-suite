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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GuavaRateLimiterTest {

    @Test
    void executeWithRateLimit_shouldAutoResumeWhenPausedTooLong() {
        GuavaRateLimiter limiter = new GuavaRateLimiter(100);
        limiter.setRate(0);

        AtomicInteger executed = new AtomicInteger(0);

        CompletableFuture<Void> future = limiter.executeWithRateLimit(3, idx -> {
            executed.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });

        assertTrue(future.orTimeout(8, TimeUnit.SECONDS).join() == null);
        assertEquals(3, executed.get());
        assertEquals(3, limiter.getAcquiredCount());
        assertEquals(1, limiter.getPermitsPerSecond());

        limiter.dispose();
    }

    @Test
    void constructor_shouldSupportFractionalPermitsPerSecond() {
        GuavaRateLimiter limiter = new GuavaRateLimiter(0.5);

        assertEquals(0.5, limiter.getPermitsPerSecondValue());
        assertEquals(TimeUnit.SECONDS.toNanos(2), limiter.getIntervalNanos());

        limiter.dispose();
    }

    @Test
    void executeContinuously_shouldRunUntilDisposed() throws Exception {
        GuavaRateLimiter limiter = new GuavaRateLimiter(1000);
        AtomicInteger executed = new AtomicInteger(0);

        CompletableFuture<Void> future = limiter.executeContinuously(idx -> {
            executed.incrementAndGet();
            if (executed.get() >= 3) {
                limiter.dispose();
            }
            return CompletableFuture.completedFuture(null);
        });

        future.get(2, TimeUnit.SECONDS);
        assertTrue(executed.get() >= 3);
    }
}
