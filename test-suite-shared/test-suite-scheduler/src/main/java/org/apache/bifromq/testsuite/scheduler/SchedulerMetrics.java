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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

class SchedulerMetrics {
    private final MeterRegistry registry;

    SchedulerMetrics(MeterRegistry registry) {
        this.registry = registry == null ? Metrics.globalRegistry : registry;
    }

    void bindGauge(String name, Object target, ToDoubleFunction<Object> valueFunction) {
        Gauge.builder(name, target, valueFunction)
            .register(registry);
    }

    void scheduled(ScheduledTaskKind kind, String scope, String outcome) {
        counter("bifro_scheduler_tasks_scheduled_total",
            kind, "scope", scope, "outcome", outcome).increment();
    }

    void executed(ScheduledTaskKind kind, String outcome, long durationNanos) {
        counter("bifro_scheduler_tasks_executed_total", kind, "outcome", outcome).increment();
        Timer.builder("bifro_scheduler_task_execution_duration")
            .tag("kind", kindName(kind))
            .tag("outcome", outcome)
            .register(registry)
            .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    void cancelled(ScheduledTaskKind kind, String outcome) {
        counter("bifro_scheduler_tasks_cancelled_total", kind, "outcome", outcome).increment();
    }

    void rejected(ScheduledTaskKind kind, String reason) {
        counter("bifro_scheduler_tasks_rejected_total", kind, "reason", reason).increment();
    }

    void delay(ScheduledTaskKind kind, long delayMs) {
        Timer.builder("bifro_scheduler_task_delay")
            .tag("kind", kindName(kind))
            .register(registry)
            .record(Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
    }

    private Counter counter(String name, ScheduledTaskKind kind, String... tags) {
        Counter.Builder builder = Counter.builder(name)
            .tag("kind", kindName(kind));
        for (int i = 0; i + 1 < tags.length; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
        return builder.register(registry);
    }

    private String kindName(ScheduledTaskKind kind) {
        return kind == null ? "UNKNOWN" : kind.name();
    }
}
