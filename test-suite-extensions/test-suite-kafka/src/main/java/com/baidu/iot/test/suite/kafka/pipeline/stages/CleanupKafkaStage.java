package com.baidu.iot.test.suite.kafka.pipeline.stages;

import com.baidu.iot.test.suite.kafka.client.BaseKafkaClientWrapper;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage for cleaning up Kafka clients.
 */
@Slf4j
public class CleanupKafkaStage implements PipelineStage<PipelineContext> {

    private final Vertx vertx;
    private final ConcurrentHashMap<String, BaseKafkaClientWrapper> kafkaClients;
    private final String taskId;

    public CleanupKafkaStage(Vertx vertx,
                            ConcurrentHashMap<String, BaseKafkaClientWrapper> kafkaClients,
                            String taskId) {
        this.vertx = vertx;
        this.kafkaClients = kafkaClients;
        this.taskId = taskId;
    }

    @Override
    public String getName() {
        return "CleanupKafka";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();

        log.info("Cleaning up {} Kafka clients for task {}", kafkaClients.size(), taskId);

        if (kafkaClients.isEmpty()) {
            future.complete(StageResult.success());
            return future;
        }

        List<CompletableFuture<Void>> disconnectFutures = new ArrayList<>();

        for (BaseKafkaClientWrapper client : kafkaClients.values()) {
            CompletableFuture<Void> disconnectFuture = client.disconnect()
                    .exceptionally(e -> {
                        log.warn("Failed to disconnect client {}: {}",
                                client.getClientId(), e.getMessage());
                        return null;
                    });
            disconnectFutures.add(disconnectFuture);
        }

        CompletableFuture.allOf(disconnectFutures.toArray(new CompletableFuture[0]))
                .whenComplete((v, e) -> {
                    kafkaClients.clear();
                    log.info("Kafka clients cleanup complete for task {}", taskId);
                    future.complete(StageResult.success());
                });

        return future;
    }

    @Override
    public CompletableFuture<Void> cancel(PipelineContext context) {
        log.info("Canceling Kafka clients, disconnecting all for task {}", taskId);

        List<CompletableFuture<Void>> cancelFutures = new ArrayList<>();

        for (BaseKafkaClientWrapper client : kafkaClients.values()) {
            cancelFutures.add(client.disconnect().exceptionally(ex -> null));
        }

        kafkaClients.clear();

        return CompletableFuture.allOf(cancelFutures.toArray(new CompletableFuture[0]));
    }
}
