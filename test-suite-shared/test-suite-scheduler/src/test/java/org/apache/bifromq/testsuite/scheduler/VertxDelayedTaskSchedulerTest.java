/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VertxDelayedTaskSchedulerTest {
    private Vertx vertx;
    private RecordingExecutor executor;
    private VertxDelayedTaskScheduler scheduler;
    private SchedulerProperties properties;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        executor = new RecordingExecutor();
        properties = new SchedulerProperties();
        properties.setMaxDelay(Duration.ofSeconds(5));
        scheduler = new VertxDelayedTaskScheduler(
            vertx,
            new ScheduledTaskExecutorRegistry(List.of(executor)),
            properties,
            new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() throws Exception {
        scheduler.close();
        vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void scheduleShouldExecuteAfterDelay() throws Exception {
        ScheduledTaskResult result = scheduler.schedule(request("task-1", 20));

        assertThat(result.isAccepted()).isTrue();
        assertThat(executor.await()).isEqualTo("task-1");
        assertThat(scheduler.listPending()).isEmpty();
    }

    @Test
    void cancelShouldPreventExecution() throws Exception {
        scheduler.schedule(request("task-1", 200));

        boolean cancelled = scheduler.cancel("task-1");
        Thread.sleep(300);

        assertThat(cancelled).isTrue();
        assertThat(executor.count()).isZero();
    }

    @Test
    void scheduleShouldReplaceExistingTaskWithSameKey() throws Exception {
        scheduler.schedule(request("task-1", 200));
        scheduler.schedule(request("task-1", 20));

        assertThat(executor.await()).isEqualTo("task-1");
        assertThat(executor.count()).isOne();
    }

    @Test
    void scheduleShouldRejectDelayOutOfRange() {
        ScheduledTaskResult result = scheduler.schedule(request("task-1", 6000));

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getState()).isEqualTo(ScheduledTaskState.REJECTED);
    }

    @Test
    void scheduleShouldRejectWhenCapacityExceeded() {
        properties.setMaxPendingTasks(0);

        ScheduledTaskResult result = scheduler.schedule(request("task-1", 200));

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.getReason()).contains("limit");
    }

    private ScheduledTaskRequest request(String taskKey, long delayMs) {
        return ScheduledTaskRequest.builder()
            .taskKey(taskKey)
            .scope(ScheduledTaskScope.LOCAL)
            .kind(ScheduledTaskKind.TASK_METRICS_CLEANUP)
            .delayMs(delayMs)
            .build();
    }

    private static class RecordingExecutor implements ScheduledTaskExecutor {
        private final AtomicInteger count = new AtomicInteger();
        private final CompletableFuture<String> executedTaskKey = new CompletableFuture<>();

        @Override
        public ScheduledTaskKind kind() {
            return ScheduledTaskKind.TASK_METRICS_CLEANUP;
        }

        @Override
        public CompletableFuture<ScheduledTaskExecutionResult> execute(ScheduledTaskContext context) {
            count.incrementAndGet();
            executedTaskKey.complete(context.getTaskKey());
            return CompletableFuture.completedFuture(ScheduledTaskExecutionResult.builder()
                .success(true)
                .build());
        }

        String await() throws Exception {
            return executedTaskKey.get(5, TimeUnit.SECONDS);
        }

        int count() {
            return count.get();
        }
    }
}
