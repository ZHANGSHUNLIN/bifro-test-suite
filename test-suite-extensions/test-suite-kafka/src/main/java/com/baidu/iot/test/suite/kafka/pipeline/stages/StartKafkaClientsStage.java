package com.baidu.iot.test.suite.kafka.pipeline.stages;

import com.baidu.iot.test.suite.TaskStage;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stage for connecting Kafka clients.
 */
@Slf4j
public class StartKafkaClientsStage implements PipelineStage<PipelineContext> {

    private final ConcurrentHashMap<String, BaseKafkaClientWrapper> kafkaClients;
    private final String taskId;
    private final InitKafkaClientsStage initStage;

    private final AtomicInteger connectedCount = new AtomicInteger(0);
    private final AtomicInteger connectErrorCount = new AtomicInteger(0);

    public StartKafkaClientsStage(ConcurrentHashMap<String, BaseKafkaClientWrapper> kafkaClients,
                                  String taskId,
                                  InitKafkaClientsStage initStage) {
        this.kafkaClients = kafkaClients;
        this.taskId = taskId;
        this.initStage = initStage;
    }

    @Override
    public String getName() {
        return "StartKafkaClients";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();
        int totalClients = initStage.clientCount();

        log.info("Starting {} Kafka clients for task {}", totalClients, taskId);

        List<CompletableFuture<Void>> connectFutures = new ArrayList<>();

        for (BaseKafkaClientWrapper client : kafkaClients.values()) {
            CompletableFuture<Void> connectFuture = client.connect()
                    .whenComplete((v, e) -> {
                        if (e == null) {
                            int current = connectedCount.incrementAndGet();
                            log.debug("Kafka client connected: {}/{}", current, totalClients);
                        } else {
                            int current = connectErrorCount.incrementAndGet();
                            log.warn("Kafka client connect error: {}/{}", current, totalClients, e);
                        }
                    });
            connectFutures.add(connectFuture);
        }

        CompletableFuture.allOf(connectFutures.toArray(new CompletableFuture[0]))
                .whenComplete((v, e) -> {
                    int successful = connectedCount.get();
                    int failed = connectErrorCount.get();
                    int total = successful + failed;

                    log.info("Kafka clients connection complete - success: {}, failed: {}, total: {}",
                            successful, failed, total);

                    if (failed > 0) {
                        future.complete(StageResult.failure(
                                String.format("%d/%d clients failed to connect", failed, total)));
                    } else {
                        future.complete(StageResult.success());
                    }
                });

        return future;
    }

    /**
     * Get connected count.
     *
     * @return number of connected clients
     */
    public int connectedCount() {
        return connectedCount.get();
    }

    /**
     * Get connect error count.
     *
     * @return number of connection errors
     */
    public int connectErrorCount() {
        return connectErrorCount.get();
    }
}
