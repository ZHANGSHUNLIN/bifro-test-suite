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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.apache.bifromq.testsuite.PubMqttClientTask;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.ratelimit.IRateLimiter;
import org.junit.jupiter.api.Test;

class NodePublishSchedulerTest {

    @Test
    void start_usesContinuousRateLimiter() {
        PubMqttClientTask client = pubClient();
        TrackingRateLimiter limiter = new TrackingRateLimiter();
        NodePublishScheduler scheduler = new NodePublishScheduler(QpsStrategy.fixed(10), 0L, List.of(client), limiter);

        scheduler.start();

        assertThat(limiter.continuousAction).isNotNull();
        assertThat(limiter.disposed.get()).isFalse();
    }

    @Test
    void continuousDispatch_roundRobinsAcrossClients() {
        PubMqttClientTask first = pubClient();
        PubMqttClientTask second = pubClient();
        TrackingRateLimiter limiter = new TrackingRateLimiter();
        NodePublishScheduler scheduler =
            new NodePublishScheduler(QpsStrategy.fixed(10), 0L, List.of(first, second), limiter);

        scheduler.start();
        limiter.emit(0L);
        limiter.emit(1L);
        limiter.emit(2L);

        verify(first, times(1)).publishOnce(eq(0L));
        verify(second, times(1)).publishOnce(eq(1L));
        verify(first, times(1)).publishOnce(eq(2L));
        assertThat(scheduler.emittedCount()).isEqualTo(3);
    }

    @Test
    void stop_disposesRateLimiter() {
        PubMqttClientTask client = pubClient();
        TrackingRateLimiter limiter = new TrackingRateLimiter();
        NodePublishScheduler scheduler = new NodePublishScheduler(QpsStrategy.fixed(10), 0L, List.of(client), limiter);

        scheduler.start();
        scheduler.stop();

        assertThat(limiter.disposed.get()).isTrue();
    }

    private PubMqttClientTask pubClient() {
        PubMqttClientTask client = mock(PubMqttClientTask.class);
        when(client.publishOnce(org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn(CompletableFuture.completedFuture(null));
        return client;
    }

    private static final class TrackingRateLimiter implements IRateLimiter {
        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private Function<Long, CompletableFuture<Void>> continuousAction;
        private double permitsPerSecond = 1D;

        @Override
        public int getPermitsPerSecond() {
            return (int) Math.round(permitsPerSecond);
        }

        @Override
        public double getPermitsPerSecondValue() {
            return permitsPerSecond;
        }

        @Override
        public long getIntervalNanos() {
            return 1_000_000_000L;
        }

        @Override
        public long getAcquiredCount() {
            return 0;
        }

        @Override
        public long getFailedCount() {
            return 0;
        }

        @Override
        public void resetMetrics() {
        }

        @Override
        public long getTotalWaitNanos() {
            return 0;
        }

        @Override
        public void setRate(int permitsPerSecond) {
            setRate((double) permitsPerSecond);
        }

        @Override
        public void setRate(double permitsPerSecond) {
            this.permitsPerSecond = permitsPerSecond;
        }

        @Override
        public void dispose() {
            disposed.set(true);
        }

        @Override
        public CompletableFuture<Void> executeWithRateLimit(
            int total,
            Function<Integer, CompletableFuture<Void>> action) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> executeContinuously(Function<Long, CompletableFuture<Void>> action) {
            this.continuousAction = action;
            return CompletableFuture.completedFuture(null);
        }

        void emit(long index) {
            continuousAction.apply(index).join();
        }
    }
}
