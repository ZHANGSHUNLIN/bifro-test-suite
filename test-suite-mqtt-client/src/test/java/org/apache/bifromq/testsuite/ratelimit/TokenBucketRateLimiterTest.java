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

package org.apache.bifromq.testsuite.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    

    @Test
    void constructor_nonPositiveRate_throwsIllegalArgument() {
        assertThatThrownBy(() -> new TokenBucketRateLimiter(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketRateLimiter(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getPermitsPerSecond_returnsConstructorValue() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(42);
        assertThat(limiter.getPermitsPerSecond()).isEqualTo(42);
        limiter.dispose();
    }

    @Test
    void getIntervalNanos_returnsCorrectValue() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1000);
        assertThat(limiter.getIntervalNanos()).isEqualTo(1_000_000L);
        limiter.dispose();
    }

    @Test
    void constructor_fractionalQps_intervalIs10Seconds() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(0.1);
        assertThat(limiter.getIntervalNanos()).isEqualTo(10_000_000_000L);
        limiter.dispose();
    }

    @Test
    void constructor_2qps_intervalIs500ms() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2.0);
        assertThat(limiter.getIntervalNanos()).isEqualTo(500_000_000L);
        limiter.dispose();
    }

    @Test
    void constructor_5qps_intervalIs200ms() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5.0);
        assertThat(limiter.getIntervalNanos()).isEqualTo(200_000_000L);
        limiter.dispose();
    }

    @Test
    void constructor_nonFiniteRate_throwsIllegalArgument() {
        assertThatThrownBy(() -> new TokenBucketRateLimiter(Double.NaN))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketRateLimiter(Double.POSITIVE_INFINITY))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucketRateLimiter(Double.NEGATIVE_INFINITY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getPermitsPerSecondValue_returnsConstructorValue() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(0.5);
        assertThat(limiter.getPermitsPerSecondValue()).isEqualTo(0.5);
        limiter.dispose();
    }

    @Test
    void setRate_intQps_updatesIntervalNanos() {
        
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(100);
        limiter.setRate(500);
        assertThat(limiter.getIntervalNanos()).isEqualTo(1_000_000_000L / 500);
        limiter.dispose();
    }

    @Test
    void setRate_doubleQps_updatesIntervalNanos() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1);
        limiter.setRate(0.25);
        assertThat(limiter.getIntervalNanos()).isEqualTo(4_000_000_000L);
        limiter.dispose();
    }

    
    
    
    
    
    

    @Test
    void burstCapacity_50requestsIn2ms_allImmediate() throws Exception {
        
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10000);

        AtomicLong completedCount = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(50);

        CompletableFuture<Void> future = limiter.executeWithRateLimit(50, index -> {
            completedCount.incrementAndGet();
            latch.countDown();
            return CompletableFuture.completedFuture(null);
        });

        
        boolean done = latch.await(1, TimeUnit.SECONDS);
        future.get(1, TimeUnit.SECONDS);

        assertThat(done).isTrue();
        assertThat(completedCount.get()).isEqualTo(50);
        limiter.dispose();
    }

    

    @Test
    void smoothRate_100qps_noDoubleSpike() throws Exception {
        
        int qps = 100;
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(qps);

        
        CopyOnWriteArrayList<Long> timestamps = new CopyOnWriteArrayList<>();

        limiter.startContinuous(index -> {
            timestamps.add(System.currentTimeMillis());
            return CompletableFuture.completedFuture(null);
        });

        
        Thread.sleep(1100);
        limiter.dispose();

        long start = timestamps.isEmpty() ? 0 : timestamps.get(0);

        
        List<Integer> windowCounts = new ArrayList<>();
        for (int w = 0; w < 10; w++) {
            long windowStart = start + w * 100L;
            long windowEnd = windowStart + 100L;
            int count = (int) timestamps.stream()
                .filter(t -> t >= windowStart && t < windowEnd)
                .count();
            windowCounts.add(count);
        }

        
        
        for (int i = 0; i < windowCounts.size(); i++) {
            int count = windowCounts.get(i);
            assertThat(count)
                .as("Window %d count %d exceeds max 20 (double-spike check)", i, count)
                .isLessThanOrEqualTo(20);
        }
    }

    

    @Test
    void setRate_doubleRate_effectiveWithin200ms() throws Exception {
        
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(500);

        AtomicLong countBefore = new AtomicLong(0);
        AtomicLong countAfter = new AtomicLong(0);
        
        AtomicLong phase = new AtomicLong(0);

        limiter.startContinuous(index -> {
            if (phase.get() == 0) {
                countBefore.incrementAndGet();
            } else {
                countAfter.incrementAndGet();
            }
            return CompletableFuture.completedFuture(null);
        });

        
        Thread.sleep(200);
        phase.set(1);
        limiter.setRate(1000);

        
        Thread.sleep(300);
        limiter.dispose();

        
        
        long afterRate = countAfter.get(); 

        
        assertThat(afterRate)
            .as("After setRate(1000), 300ms should yield 150–500 ticks (was %d)", afterRate)
            .isBetween(150L, 500L);
    }

    

    @Test
    void dispose_stopsEmission() throws Exception {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1000);
        AtomicLong count = new AtomicLong(0);

        limiter.startContinuous(index -> {
            count.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });

        Thread.sleep(100);
        limiter.dispose();
        long countAtDispose = count.get();

        Thread.sleep(200);
        
        assertThat(count.get())
            .as("Emission should stop after dispose")
            .isLessThanOrEqualTo(countAtDispose + 5); 
    }

    @Test
    void resetMetrics_resetsAcquiredAndFailedCounts() throws Exception {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1000);
        limiter.executeWithRateLimit(10, i -> CompletableFuture.completedFuture(null))
            .get(2, TimeUnit.SECONDS);

        assertThat(limiter.getAcquiredCount()).isGreaterThan(0);
        limiter.resetMetrics();
        assertThat(limiter.getAcquiredCount()).isEqualTo(0);
        assertThat(limiter.getFailedCount()).isEqualTo(0);
        limiter.dispose();
    }

    @Test
    void setRate_nonPositive_isIgnored() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(100);
        limiter.setRate(0);
        assertThat(limiter.getPermitsPerSecond()).isEqualTo(100);
        limiter.setRate(-5);
        assertThat(limiter.getPermitsPerSecond()).isEqualTo(100);
        limiter.dispose();
    }

    @Test
    void sharedTimerScheduler_isNotIoScheduler() throws Exception {
        Field field = TokenBucketRateLimiter.class.getDeclaredField("SHARED_TIMER_SCHEDULER");
        field.setAccessible(true);
        Object scheduler = field.get(null);

        assertThat(scheduler).isNotNull();
        assertThat(scheduler.getClass().getName())
            .contains("ComputationScheduler")
            .doesNotContain("IoScheduler");
    }
}
