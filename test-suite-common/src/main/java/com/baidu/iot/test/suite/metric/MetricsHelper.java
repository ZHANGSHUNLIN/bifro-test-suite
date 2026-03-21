package com.baidu.iot.test.suite.metric;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.joining;

public final class MetricsHelper {

    private static MeterRegistry registry = Metrics.globalRegistry;

    private static final ConcurrentHashMap<String, Counter> COUNTER_CACHE = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Timer> TIMER_CACHE = new ConcurrentHashMap<>();

    private MetricsHelper() {
    }

    /**
     * 初始化
     */
    public static void init(MeterRegistry meterRegistry) {
        registry = meterRegistry;
    }


    public static void counter(TaskMetric metric, Tags... tags) {

        Counter counter = COUNTER_CACHE.computeIfAbsent(
                buildKey(metric, tags),
                k -> Counter.builder(metric.getName())
                        .tags(Arrays.stream(tags).flatMap(Tags::stream).toList())
                        .register(registry)
        );

        counter.increment();
    }

    /**
     * counter +n
     */
    public static void counter(TaskMetric metric, double amount, String... tags) {

        Counter counter = COUNTER_CACHE.computeIfAbsent(
                buildKey(metric, tags),
                k -> Counter.builder(metric.getName())
                        .tags(tags)
                        .register(registry)
        );

        counter.increment(amount);
    }

    /**
     * 记录耗时
     */
    public static void recordTime(TaskMetric metric, long durationMs, String... tags) {
        Timer timer = TIMER_CACHE.computeIfAbsent(
                buildKey(metric, tags),
                k -> Timer.builder(metric.getName())
                        .tags(tags)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry)
        );

        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public static Timer timer(TaskMetric metric, String... tags) {
        return TIMER_CACHE.computeIfAbsent(
                buildKey(metric, tags),
                k -> Timer.builder(metric.getName())
                        .tags(tags)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry)
        );
    }

    /**
     * start timer
     */
    public static Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    /**
     * stop timer
     */
    public static void stopTimer(Timer.Sample sample, TaskMetric metric, String... tags) {

        Timer timer = TIMER_CACHE.computeIfAbsent(
                buildKey(metric, tags),
                k -> Timer.builder(metric.getName())
                        .tags(tags)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry)
        );

        sample.stop(timer);
    }

    public static void recordTimer(TaskMetric metric, Runnable task) {
        recordTimer(metric, null, task);
    }

        public static void recordTimer(TaskMetric metric, Tags tags, Runnable task) {
        Timer.Sample start = Timer.start(registry);
        Timer timer = TIMER_CACHE.computeIfAbsent(
                buildKey(metric, tags),
                k -> Timer.builder(metric.getName())
                        .tags(tags)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry)
        );
        try {
            task.run();
        } finally {
            start.stop(timer);
        }
    }


    /**
     * cache key
     */
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