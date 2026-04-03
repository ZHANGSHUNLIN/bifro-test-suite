package com.baidu.iot.test.suite.kafka.pipeline.stages;

import com.baidu.iot.test.suite.kafka.client.BaseKafkaClientWrapper;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stage for collecting Kafka client results.
 */
@Slf4j
public class CollectKafkaResultsStage implements PipelineStage<PipelineContext> {

    private final ConcurrentHashMap<String, BaseKafkaClientWrapper> kafkaClients;

    private final AtomicInteger totalProduced = new AtomicInteger(0);
    private final AtomicInteger totalConsumed = new AtomicInteger(0);
    private final AtomicLong totalLatency = new AtomicLong(0);

    public CollectKafkaResultsStage(ConcurrentHashMap<String, BaseKafkaClientWrapper> kafkaClients) {
        this.kafkaClients = kafkaClients;
    }

    @Override
    public String getName() {
        return "CollectKafkaResults";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();

        log.info("Collecting results from {} Kafka clients", kafkaClients.size());

        List<CompletableFuture<Void>> collectFutures = new ArrayList<>();

        for (BaseKafkaClientWrapper client : kafkaClients.values()) {
            CompletableFuture<Void> collectFuture = CompletableFuture.runAsync(() -> {
                Map<String, Object> metrics = client.getMetrics();
                Map<String, Object> producerMetrics = client.getProducerMetrics();

                if (producerMetrics != null) {
                    producerMetrics.forEach((key, value) -> {
                        if (key.contains("record-send-total")) {
                            totalProduced.addAndGet(getIntValue(value));
                        }
                        if (key.contains("record-queue-time-avg")) {
                            totalLatency.addAndGet(getLongValue(value));
                        }
                    });
                }
            }).exceptionally(e -> {
                log.warn("Failed to collect results from client {}: {}",
                        client.getClientId(), e.getMessage());
                return null;
            });
            collectFutures.add(collectFuture);
        }

        CompletableFuture.allOf(collectFutures.toArray(new CompletableFuture[0]))
                .whenComplete((v, e) -> {
                    int produced = totalProduced.get();
                    int consumed = totalConsumed.get();
                    long avgLatency = produced > 0 ? totalLatency.get() / produced : 0;

                    log.info("Kafka results - produced: {}, consumed: {}, avgLatency: {}ns",
                            produced, consumed, avgLatency);

                    // Store results in context
                    context.getStageData().put("kafkaTotalProduced", produced);
                    context.getStageData().put("kafkaTotalConsumed", consumed);
                    context.getStageData().put("kafkaAvgLatency", avgLatency);

                    future.complete(StageResult.success());
                });

        return future;
    }

    /**
     * Get integer value from object.
     *
     * @param value value object
     * @return integer value
     */
    private int getIntValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    /**
     * Get long value from object.
     *
     * @param value value object
     * @return long value
     */
    private long getLongValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0;
    }

    /**
     * Get total produced count.
     *
     * @return total produced messages
     */
    public int totalProduced() {
        return totalProduced.get();
    }

    /**
     * Get total consumed count.
     *
     * @return total consumed messages
     */
    public int totalConsumed() {
        return totalConsumed.get();
    }

    /**
     * Get average latency.
     *
     * @return average latency in nanoseconds
     */
    public long avgLatency() {
        long produced = totalProduced.get();
        return produced > 0 ? totalLatency.get() / produced : 0;
    }
}
