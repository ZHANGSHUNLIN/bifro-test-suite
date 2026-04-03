package com.baidu.iot.test.suite.kafka.pipeline.stages;

import com.baidu.iot.test.suite.kafka.client.BaseKafkaClientWrapper;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stage for producing messages to Kafka.
 */
@Slf4j
public class ProduceKafkaMessagesStage implements PipelineStage<PipelineContext> {

    private final ConcurrentHashMap<String, BaseKafkaClientWrapper> kafkaClients;
    private final int messageCount;
    private final int messageSize;
    private final String messagePrefix;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong totalBytesSent = new AtomicLong(0);

    public ProduceKafkaMessagesStage(ConcurrentHashMap<String, BaseKafkaClientWrapper> kafkaClients,
                                   int messageCount,
                                   int messageSize) {
        this(kafkaClients, messageCount, messageSize, "kafka-test-msg");
    }

    public ProduceKafkaMessagesStage(ConcurrentHashMap<String, BaseKafkaClientWrapper> kafkaClients,
                                   int messageCount,
                                   int messageSize,
                                   String messagePrefix) {
        this.kafkaClients = kafkaClients;
        this.messageCount = messageCount;
        this.messageSize = messageSize;
        this.messagePrefix = messagePrefix;
    }

    @Override
    public String getName() {
        return "ProduceKafkaMessages";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();

        log.info("Producing {} messages, size={} bytes each", messageCount, messageSize);

        List<CompletableFuture<Void>> produceFutures = new ArrayList<>();
        int messagesPerClient = Math.max(1, messageCount / kafkaClients.size());

        for (BaseKafkaClientWrapper client : kafkaClients.values()) {
            for (int i = 0; i < messagesPerClient && successCount.get() < messageCount; i++) {
                String message = generateMessage(successCount.get());
                CompletableFuture<Void> produceFuture = client.produce(message)
                        .thenRun(() -> {
                            int current = successCount.incrementAndGet();
                            totalBytesSent.addAndGet(message.length());
                            if (current % 1000 == 0) {
                                log.debug("Produced {} messages", current);
                            }
                        })
                        .exceptionally(e -> {
                            errorCount.incrementAndGet();
                            log.warn("Failed to produce message: {}", e.getMessage());
                            return null;
                        });
                produceFutures.add(produceFuture);
            }
        }

        CompletableFuture.allOf(produceFutures.toArray(new CompletableFuture[0]))
                .whenComplete((v, e) -> {
                    int successful = successCount.get();
                    int failed = errorCount.get();
                    long bytes = totalBytesSent.get();

                    log.info("Message production complete - success: {}, failed: {}, totalBytes: {}",
                            successful, failed, bytes);

                    // Store results in context
                    context.getStageData().put("produceSuccessCount", successful);
                    context.getStageData().put("produceErrorCount", failed);
                    context.getStageData().put("produceTotalBytes", bytes);

                    if (failed > successful) {
                        future.complete(StageResult.failure(
                                String.format("More errors than successes: %d failures vs %d successes",
                                        failed, successful)));
                    } else {
                        future.complete(StageResult.success());
                    }
                });

        return future;
    }

    /**
     * Generate a test message.
     *
     * @param index message index
     * @return message string
     */
    private String generateMessage(int index) {
        StringBuilder sb = new StringBuilder(messagePrefix);
        sb.append("-").append(index);
        sb.append("-").append(System.currentTimeMillis());

        while (sb.length() < messageSize) {
            sb.append("A");
        }

        return sb.substring(0, Math.min(sb.length(), messageSize));
    }

    /**
     * Get success count.
     *
     * @return number of successfully produced messages
     */
    public int successCount() {
        return successCount.get();
    }

    /**
     * Get error count.
     *
     * @return number of failed productions
     */
    public int errorCount() {
        return errorCount.get();
    }

    /**
     * Get total bytes sent.
     *
     * @return total bytes sent
     */
    public long totalBytesSent() {
        return totalBytesSent.get();
    }
}
