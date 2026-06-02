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

import static java.util.stream.Collectors.joining;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class MetricsHelper {

    private static final ConcurrentHashMap<String, Counter> COUNTER_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Timer> TIMER_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicReference<Double>> GAUGE_CACHE = new ConcurrentHashMap<>();
    
    private static final ConcurrentHashMap<String, TimerMetricData> FROZEN_TIMER_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> FAILED_METRIC_KEYS = ConcurrentHashMap.newKeySet();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MetricsHelper.class);
    private static MeterRegistry registry = Metrics.globalRegistry;

    private MetricsHelper() {
    }

    
    public static void init(MeterRegistry meterRegistry) {
        registry = meterRegistry;
    }

    
    public static void counter(TaskMetric metric, Tags... tags) {
        String key = buildKey(metric, tags);
        if (FAILED_METRIC_KEYS.contains(key)) {
            return;
        }
        try {
            Counter counter = COUNTER_CACHE.computeIfAbsent(
                key,
                k -> Counter.builder(metric.getName())
                    .tags(Arrays.stream(tags).flatMap(Tags::stream).toList())
                    .register(registry)
            );

            counter.increment();
        } catch (RuntimeException | LinkageError e) {
            logMetricFailure(key, "counter", metric, e);
        }
    }

    
    public static void counter(TaskMetric metric, double amount, String... tags) {
        String key = buildKey(metric, tags);
        if (FAILED_METRIC_KEYS.contains(key)) {
            return;
        }
        try {
            Counter counter = COUNTER_CACHE.computeIfAbsent(
                key,
                k -> Counter.builder(metric.getName())
                    .tags(tags)
                    .register(registry)
            );
            counter.increment(amount);
        } catch (RuntimeException | LinkageError e) {
            logMetricFailure(key, "counter", metric, e);
        }
    }

    
    public static void gauge(TaskMetric metric, double value, String... tags) {
        String key = buildKey(metric, tags);
        if (FAILED_METRIC_KEYS.contains(key)) {
            return;
        }
        try {
            AtomicReference<Double> gaugeValue = GAUGE_CACHE.computeIfAbsent(key, k -> {
                AtomicReference<Double> reference = new AtomicReference<>(0.0);
                Gauge.builder(metric.getName(), reference, AtomicReference::get)
                    .tags(tags)
                    .register(registry);
                return reference;
            });
            gaugeValue.set(value);
        } catch (RuntimeException | LinkageError e) {
            logMetricFailure(key, "gauge", metric, e);
        }
    }

    public static void gaugeDelta(TaskMetric metric, double delta, String... tags) {
        String key = buildKey(metric, tags);
        if (FAILED_METRIC_KEYS.contains(key)) {
            return;
        }
        try {
            AtomicReference<Double> gaugeValue = GAUGE_CACHE.computeIfAbsent(key, k -> {
                AtomicReference<Double> reference = new AtomicReference<>(0.0);
                Gauge.builder(metric.getName(), reference, AtomicReference::get)
                    .tags(tags)
                    .register(registry);
                return reference;
            });
            gaugeValue.updateAndGet(current -> Math.max(0.0, current + delta));
        } catch (RuntimeException | LinkageError e) {
            logMetricFailure(key, "gauge", metric, e);
        }
    }

    public static void removeGaugesForTaskNode(String taskId, String nodeId, TaskMetric... metrics) {
        if (taskId == null || nodeId == null || metrics == null || metrics.length == 0) {
            return;
        }
        java.util.Set<String> metricNames = Arrays.stream(metrics)
            .map(TaskMetric::getName)
            .collect(Collectors.toSet());
        registry.getMeters().stream()
            .filter(meter -> metricNames.contains(meter.getId().getName()))
            .filter(meter -> taskId.equals(meter.getId().getTag("taskId")))
            .filter(meter -> nodeId.equals(meter.getId().getTag("nodeId")))
            .toList()
            .forEach(registry::remove);
        GAUGE_CACHE.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            return Arrays.stream(metrics).anyMatch(metric -> matchesGaugeCacheKey(key, metric, taskId, nodeId));
        });
    }

    public static int removeMetersForTaskNode(String taskId, String nodeId) {
        if (taskId == null || taskId.isBlank() || nodeId == null || nodeId.isBlank()) {
            return 0;
        }
        List<Meter> meters = registry.getMeters().stream()
            .filter(meter -> matchesTaskNode(meter.getId(), taskId, nodeId))
            .toList();
        meters.forEach(registry::remove);
        COUNTER_CACHE.entrySet().removeIf(entry -> matchesCacheKey(entry.getKey(), taskId, nodeId));
        TIMER_CACHE.entrySet().removeIf(entry -> matchesCacheKey(entry.getKey(), taskId, nodeId));
        GAUGE_CACHE.entrySet().removeIf(entry -> matchesCacheKey(entry.getKey(), taskId, nodeId));
        FROZEN_TIMER_SNAPSHOTS.entrySet().removeIf(entry -> matchesCacheKey(entry.getKey(), taskId, nodeId));
        FAILED_METRIC_KEYS.removeIf(key -> matchesCacheKey(key, taskId, nodeId));
        return meters.size();
    }

    private static boolean matchesGaugeCacheKey(String key, TaskMetric metric, String taskId, String nodeId) {
        return key.startsWith(metric.getName() + ".")
            && containsTagValue(key, "taskId", taskId)
            && containsTagValue(key, "nodeId", nodeId);
    }

    private static boolean matchesTaskNode(Meter.Id id, String taskId, String nodeId) {
        return taskId.equals(id.getTag("taskId")) && nodeId.equals(id.getTag("nodeId"));
    }

    private static boolean matchesCacheKey(String key, String taskId, String nodeId) {
        return containsTagValue(key, "taskId", taskId) && containsTagValue(key, "nodeId", nodeId);
    }

    private static boolean containsTagValue(String key, String tag, String value) {
        String dottedTagValue = "." + tag + "." + value;
        return key.contains(dottedTagValue + ".") || key.endsWith(dottedTagValue)
            || containsTokenizedValue(key, tag + "=" + value);
    }

    private static boolean containsTokenizedValue(String key, String value) {
        int index = key.indexOf(value);
        while (index >= 0) {
            int end = index + value.length();
            boolean startsAtBoundary = index == 0 || key.charAt(index - 1) == '.' || key.charAt(index - 1) == ',';
            boolean endsAtBoundary = end == key.length() || key.charAt(end) == '.' || key.charAt(end) == ',';
            if (startsAtBoundary && endsAtBoundary) {
                return true;
            }
            index = key.indexOf(value, index + 1);
        }
        return false;
    }

    public static void recordTimeNanos(TaskMetric metric, long durationNanos, String... tags) {
        String key = buildKey(metric, tags);
        if (FAILED_METRIC_KEYS.contains(key)) {
            return;
        }
        try {
            Timer timer = getOrCreateTimer(key, metric, tags);

            timer.record(durationNanos, TimeUnit.NANOSECONDS);
        } catch (RuntimeException | LinkageError e) {
            logMetricFailure(key, "timer", metric, e);
        }
    }

    
    public static Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    
    public static void stopTimer(Timer.Sample sample, TaskMetric metric, String... tags) {
        String key = buildKey(metric, tags);
        if (FAILED_METRIC_KEYS.contains(key)) {
            return;
        }
        try {
            Timer timer = getOrCreateTimer(key, metric, tags);

            sample.stop(timer);
        } catch (RuntimeException | LinkageError e) {
            logMetricFailure(key, "timer", metric, e);
        }
    }

    private static void logMetricFailure(String key, String operation, TaskMetric metric, Throwable error) {
        if (FAILED_METRIC_KEYS.add(key)) {
            LOG.warn("Metric {} failed, metric={}, reason={}", operation, metric.getName(), error.toString());
        }
    }

    private static Timer getOrCreateTimer(String key, TaskMetric metric, String... tags) {
        return TIMER_CACHE.computeIfAbsent(key, k -> buildTimer(metric, tags));
    }

    private static Timer buildTimer(TaskMetric metric, String[] tags) {
        return Timer.builder(metric.getName())
            .tags(tags)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    
    public static List<CounterMetricData> readCounters(String taskId, List<String> metricNames) {
        return registry.getMeters().stream()
            .filter(meter -> meter instanceof Counter)
            .map(meter -> (Counter) meter)
            .filter(counter -> {
                Meter.Id id = counter.getId();
                if (metricNames != null && !metricNames.isEmpty()
                    && !metricNames.contains(id.getName())) {
                    return false;
                }
                if (taskId != null) {
                    String taskTag = id.getTag("taskId");
                    return taskId.equals(taskTag);
                }
                return true;
            })
            .map(counter -> {
                Meter.Id id = counter.getId();
                Map<String, String> tags = new HashMap<>();
                for (Tag tag : id.getTagsAsIterable()) {
                    tags.put(tag.getKey(), tag.getValue());
                }
                return CounterMetricData.builder()
                    .name(id.getName())
                    .tags(tags)
                    .count(counter.count())
                    .build();
            })
            .collect(Collectors.toList());
    }

    
    public static void freezeTimerSnapshot(TaskMetric metric, String... tags) {
        String key = buildKey(metric, tags);
        Timer timer = TIMER_CACHE.get(key);
        if (timer == null) {
            return;
        }
        TimerMetricData snapshot = buildFrozenTimerMetricData(timer);
        if (snapshot != null) {
            FROZEN_TIMER_SNAPSHOTS.put(key, snapshot);
        }
    }

    public static void freezeTimerSnapshots(String taskId, String nodeId) {
        registry.getMeters().stream()
            .filter(meter -> meter instanceof Timer)
            .map(meter -> (Timer) meter)
            .filter(timer -> matchesTimerForTaskNode(timer.getId(), taskId, nodeId))
            .forEach(timer -> {
                String cacheKey = findTimerCacheKey(timer);
                if (cacheKey == null) {
                    return;
                }
                TimerMetricData snapshot = buildFrozenTimerMetricData(timer);
                if (snapshot != null) {
                    FROZEN_TIMER_SNAPSHOTS.put(cacheKey, snapshot);
                }
            });
    }

    
    public static List<TimerMetricData> readTimers(String taskId, List<String> metricNames) {
        return registry.getMeters().stream()
            .filter(meter -> meter instanceof Timer)
            .map(meter -> (Timer) meter)
            .filter(timer -> matchesMeterFilter(timer.getId(), taskId, metricNames))
            .map(timer -> buildTimerMetricData(timer))
            .collect(Collectors.toList());
    }

    private static boolean matchesMeterFilter(Meter.Id id, String taskId, List<String> metricNames) {
        if (metricNames != null && !metricNames.isEmpty() && !metricNames.contains(id.getName())) {
            return false;
        }
        if (taskId != null) {
            String taskTag = id.getTag("taskId");
            return taskId.equals(taskTag);
        }
        return true;
    }

    private static double[] extractPercentiles(HistogramSnapshot snapshot) {
        double p50 = 0.0;
        double p95 = 0.0;
        double p99 = 0.0;
        for (io.micrometer.core.instrument.distribution.ValueAtPercentile vap : snapshot.percentileValues()) {
            double valueMs = vap.value(TimeUnit.MILLISECONDS);
            if (Double.compare(vap.percentile(), 0.5) == 0) {
                p50 = valueMs;
            } else if (Double.compare(vap.percentile(), 0.95) == 0) {
                p95 = valueMs;
            } else if (Double.compare(vap.percentile(), 0.99) == 0) {
                p99 = valueMs;
            }
        }
        return new double[] {p50, p95, p99};
    }

    private static TimerMetricData buildTimerMetricData(Timer timer) {
        Meter.Id id = timer.getId();
        Map<String, String> tags = new HashMap<>();
        for (Tag tag : id.getTagsAsIterable()) {
            tags.put(tag.getKey(), tag.getValue());
        }
        HistogramSnapshot snapshot = timer.takeSnapshot();
        long count = snapshot.count();
        double mean = count > 0 ? snapshot.mean(TimeUnit.MILLISECONDS) : 0.0;
        double max = count > 0 ? snapshot.max(TimeUnit.MILLISECONDS) : 0.0;
        double totalTime = count > 0 ? snapshot.total(TimeUnit.MILLISECONDS) : 0.0;

        double[] percentiles = snapshot.percentileValues().length > 0
            ? extractPercentiles(snapshot) : new double[] {0.0, 0.0, 0.0};
        double p50 = percentiles[0];
        double p95 = percentiles[1];
        double p99 = percentiles[2];

        
        if (p50 == 0.0 && count > 0) {
            String cacheKey = findTimerCacheKey(timer);
            if (cacheKey != null) {
                TimerMetricData frozen = FROZEN_TIMER_SNAPSHOTS.get(cacheKey);
                if (frozen != null) {
                    p50 = frozen.getP50();
                    p95 = frozen.getP95();
                    p99 = frozen.getP99();
                    max = frozen.getMax();
                }
            }
        }

        return TimerMetricData.builder()
            .name(id.getName())
            .tags(tags)
            .count(count)
            .mean(mean)
            .p50(p50)
            .p95(p95)
            .p99(p99)
            .max(max)
            .totalTime(totalTime)
            .hasData(count > 0)
            .build();
    }

    private static boolean matchesTimerForTaskNode(Meter.Id id, String taskId, String nodeId) {
        if (taskId != null && !taskId.equals(id.getTag("taskId"))) {
            return false;
        }
        return nodeId == null || nodeId.equals(id.getTag("nodeId"));
    }

    private static String findTimerCacheKey(Timer timer) {
        return TIMER_CACHE.entrySet().stream()
            .filter(e -> e.getValue() == timer)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private static TimerMetricData buildFrozenTimerMetricData(Timer timer) {
        HistogramSnapshot snapshot = timer.takeSnapshot();
        long count = snapshot.count();
        if (count == 0) {
            return null;
        }
        double[] percentiles = snapshot.percentileValues().length > 0
            ? extractPercentiles(snapshot) : new double[] {0.0, 0.0, 0.0};
        double p50 = percentiles[0];
        double p95 = percentiles[1];
        double p99 = percentiles[2];
        double max = snapshot.max(TimeUnit.MILLISECONDS);
        if (p50 == 0.0 && p95 == 0.0 && p99 == 0.0 && max == 0.0) {
            return null;
        }
        Map<String, String> tags = new HashMap<>();
        for (Tag tag : timer.getId().getTagsAsIterable()) {
            tags.put(tag.getKey(), tag.getValue());
        }
        return TimerMetricData.builder()
            .name(timer.getId().getName())
            .tags(tags)
            .count(count)
            .mean(snapshot.mean(TimeUnit.MILLISECONDS))
            .p50(p50)
            .p95(p95)
            .p99(p99)
            .max(max)
            .totalTime(snapshot.total(TimeUnit.MILLISECONDS))
            .hasData(true)
            .build();
    }

    
    private static String buildKey(TaskMetric metric, String... tags) {

        StringBuilder sb = new StringBuilder(metric.getName());

        if (tags != null) {
            for (String tag : tags) {
                sb.append('.').append(tag);
            }
        }

        return sb.toString();
    }

    private static String buildKey(TaskMetric metric, Tags... tags) {

        StringBuilder sb = new StringBuilder(metric.getName());

        if (tags != null) {
            for (Tags tag : tags) {
                sb.append('.').append(tag.stream().map(Tag::toString).collect(joining(",")));
            }
        }

        return sb.toString();
    }
}
