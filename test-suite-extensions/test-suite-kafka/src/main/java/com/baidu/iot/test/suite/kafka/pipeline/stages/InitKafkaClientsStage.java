package com.baidu.iot.test.suite.kafka.pipeline.stages;

import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.kafka.client.BaseKafkaClientWrapper;
import com.baidu.iot.test.suite.kafka.config.KafkaClientConfig;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stage for initializing Kafka clients.
 */
@Slf4j
public class InitKafkaClientsStage implements PipelineStage<PipelineContext> {

    private final Vertx vertx;
    private final KafkaClientConfig config;
    private final ConcurrentHashMap<String, BaseKafkaClientWrapper> kafkaClients;
    private final int clientCount;
    private final AtomicReference<TaskStage> taskStageRef;

    public InitKafkaClientsStage(Vertx vertx, KafkaClientConfig config, int clientCount,
                                 AtomicReference<TaskStage> taskStageRef) {
        this.vertx = vertx;
        this.config = config;
        this.kafkaClients = new ConcurrentHashMap<>();
        this.clientCount = clientCount;
        this.taskStageRef = taskStageRef;
    }

    @Override
    public String getName() {
        return "InitKafkaClients";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();

        log.info("Initializing {} Kafka clients, topic={}", clientCount, config.getTopic());

        try {
            for (int i = 0; i < clientCount; i++) {
                String clientId = config.getClientId() + "-" + i;
                KafkaClientConfig clientConfig = KafkaClientConfig.builder()
                        .bootstrapServers(config.getBootstrapServers())
                        .clientId(clientId)
                        .topic(config.getTopic())
                        .consumerGroupId(config.getConsumerGroupId())
                        .autoOffsetReset(config.getAutoOffsetReset())
                        .enableAutoCommit(config.getEnableAutoCommit())
                        .maxPollRecords(config.getMaxPollRecords())
                        .requestTimeoutMs(config.getRequestTimeoutMs())
                        .sessionTimeoutMs(config.getSessionTimeoutMs())
                        .reconnectMaxAttempts(config.getReconnectMaxAttempts())
                        .reconnectIntervalInMs(config.getReconnectIntervalInMs())
                        .build();

                BaseKafkaClientWrapper clientWrapper = new BaseKafkaClientWrapper(
                        vertx, clientConfig, taskStageRef);
                kafkaClients.put(clientId, clientWrapper);
            }

            log.info("Initialized {} Kafka clients", kafkaClients.size());
            future.complete(StageResult.success());

        } catch (Exception e) {
            log.error("Failed to initialize Kafka clients", e);
            future.complete(StageResult.failure(e.getMessage()));
        }

        return future;
    }

    /**
     * Get initialized Kafka clients.
     *
     * @return map of client id to client wrapper
     */
    public ConcurrentHashMap<String, BaseKafkaClientWrapper> kafkaClients() {
        return kafkaClients;
    }

    /**
     * Get client count.
     *
     * @return number of clients
     */
    public int clientCount() {
        return clientCount;
    }
}
