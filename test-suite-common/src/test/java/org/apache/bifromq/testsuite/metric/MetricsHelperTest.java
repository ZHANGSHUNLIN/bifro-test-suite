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

package org.apache.bifromq.testsuite.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MetricsHelperTest {

    @AfterEach
    void cleanup() throws Exception {
        resetPrivateStaticCollection("COUNTER_CACHE");
        resetPrivateStaticCollection("TIMER_CACHE");
        resetPrivateStaticCollection("GAUGE_CACHE");
        resetPrivateStaticCollection("FROZEN_TIMER_SNAPSHOTS");
        resetPrivateStaticCollection("FAILED_METRIC_KEYS");
        MetricsHelper.init(new SimpleMeterRegistry());
    }

    @Test
    void runtimeClasspathShouldSupportPublishedTimerPercentiles() throws Exception {
        assertThatCode(() -> Class.forName("org.HdrHistogram.DoubleRecorder"))
            .doesNotThrowAnyException();

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Timer timer = Timer.builder("probe")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        timer.record(Duration.ofMillis(10));

        assertThat(timer.takeSnapshot().percentileValues()).hasSize(3);
    }

    @Test
    void stopTimerShouldPublishPercentiles() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsHelper.init(registry);
        Timer.Sample sample = MetricsHelper.startTimer();

        MetricsHelper.stopTimer(sample, BifroTaskMetric.CONNECT_LATENCY, "taskId", "task-1");

        Timer timer = registry.find(BifroTaskMetric.CONNECT_LATENCY.getName()).timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.takeSnapshot().percentileValues()).hasSize(3);
    }

    @Test
    void freezeTimerSnapshotsShouldPreserveFinishedTaskTimerData() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsHelper.init(registry);

        MetricsHelper.recordTimeNanos(BifroTaskMetric.PUBLISH_LATENCY, 10_000_000,
            "taskId", "task-1", "nodeId", "node-1");
        MetricsHelper.recordTimeNanos(BifroTaskMetric.PUBLISH_LATENCY, 20_000_000,
            "taskId", "task-1", "nodeId", "node-1");

        MetricsHelper.freezeTimerSnapshots("task-1", "node-1");

        Timer timer = registry.find(BifroTaskMetric.PUBLISH_LATENCY.getName()).timer();
        assertThat(timer).isNotNull();
        TimerMetricData frozen = readTimerMetric("task-1", timer);

        assertThat(frozen.getCount()).isEqualTo(2);
        assertThat(frozen.getP50()).isGreaterThan(0.0);
        assertThat(frozen.getP95()).isGreaterThan(0.0);
        assertThat(frozen.getP99()).isGreaterThan(0.0);
        assertThat(frozen.getMax()).isGreaterThan(0.0);
    }

    @Test
    void removeGaugesForTaskNodeShouldRemoveMetersAndCache() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsHelper.init(registry);

        MetricsHelper.gauge(BifroTaskMetric.CLIENT_READY_GAUGE, 10,
            "taskId", "task-1", "nodeId", "node-1", "clientType", "conn");
        MetricsHelper.gauge(BifroTaskMetric.CLIENT_ACTIVE_GAUGE, 5,
            "taskId", "task-1", "nodeId", "node-1", "clientType", "conn");
        MetricsHelper.gauge(BifroTaskMetric.CLIENT_READY_GAUGE, 20,
            "taskId", "task-2", "nodeId", "node-1", "clientType", "conn");

        MetricsHelper.removeGaugesForTaskNode("task-1", "node-1",
            BifroTaskMetric.CLIENT_READY_GAUGE,
            BifroTaskMetric.CLIENT_ACTIVE_GAUGE);

        assertThat(registry.find(BifroTaskMetric.CLIENT_READY_GAUGE.getName())
            .tag("taskId", "task-1")
            .gauge()).isNull();
        assertThat(registry.find(BifroTaskMetric.CLIENT_ACTIVE_GAUGE.getName())
            .tag("taskId", "task-1")
            .gauge()).isNull();
        assertThat(registry.find(BifroTaskMetric.CLIENT_READY_GAUGE.getName())
            .tag("taskId", "task-2")
            .gauge()).isNotNull();
    }

    @Test
    void metricRegistrationFailureShouldNotEscapeBusinessPath() {
        MetricsHelper.init(new ThrowingMeterRegistry());
        Timer.Sample sample = MetricsHelper.startTimer();

        assertThatCode(() -> MetricsHelper.counter(BifroTaskMetric.CONNECT_SUCCESS_COUNT))
            .doesNotThrowAnyException();
        assertThatCode(() -> MetricsHelper.stopTimer(sample, BifroTaskMetric.CONNECT_LATENCY,
            "taskId", "task-1"))
            .doesNotThrowAnyException();
    }

    private static void resetPrivateStaticCollection(String fieldName) throws Exception {
        Field field = MetricsHelper.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(null);
        if (value instanceof java.util.Map<?, ?> map) {
            map.clear();
        } else if (value instanceof java.util.Set<?> set) {
            set.clear();
        }
    }

    private static TimerMetricData readTimerMetric(String taskId, Timer timer) {
        return MetricsHelper.readTimers(taskId, null).stream()
            .filter(metric -> metric.getName().equals(timer.getId().getName()))
            .findFirst()
            .orElseThrow();
    }

    private static final class ThrowingMeterRegistry extends SimpleMeterRegistry {

        @Override
        protected Counter newCounter(Meter.Id id) {
            throw new NoClassDefFoundError("org/HdrHistogram/DoubleRecorder");
        }

        @Override
        protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
                                 PauseDetector pauseDetector) {
            throw new NoClassDefFoundError("org/HdrHistogram/DoubleRecorder");
        }
    }
}
