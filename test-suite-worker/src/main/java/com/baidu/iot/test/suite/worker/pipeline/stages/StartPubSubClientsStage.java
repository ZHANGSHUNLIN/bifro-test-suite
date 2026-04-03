
package com.baidu.iot.test.suite.worker.pipeline.stages;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.baidu.iot.test.suite.ClientTask;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.PubClientTask;
import com.baidu.iot.test.suite.SubClientTask;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

/**
 * Stage for starting pub/sub clients.
 */
@Slf4j
public class StartPubSubClientsStage implements PipelineStage<PipelineContext> {

    private final Vertx vertx;

    public StartPubSubClientsStage(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public String getName() {
        return "StartPubSubClients";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();

        vertx.executeBlocking(() -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, ClientTask> pubClients =
                        (Map<String, ClientTask>) context.getStageData().get(Constants.PUB_CLIENT_TAG);
                @SuppressWarnings("unchecked")
                Map<String, ClientTask> subClients =
                        (Map<String, ClientTask>) context.getStageData().get(Constants.SUB_CLIENT_TAG);

                // Start pub clients
                if (pubClients != null && !pubClients.isEmpty()) {
                    for (ClientTask client : pubClients.values()) {
                        if (context.isCancelled()) {
                            vertx.runOnContext(v -> future.complete(
                                    StageResult.failure("Task cancelled")));
                            return null;
                        }
                        try {
                            // Start pub clients by calling startTask with callback
                            client.startTask();
                        } catch (Exception e) {
                            log.warn("Failed to start pub client: {}", client, e);
                        }
                    }
                }

                // Start sub clients
                if (subClients != null && !subClients.isEmpty()) {
                    for (ClientTask client : subClients.values()) {
                        if (context.isCancelled()) {
                            vertx.runOnContext(v -> future.complete(
                                    StageResult.failure("Task cancelled")));
                            return null;
                        }
                        try {
                            // Start sub clients
                            client.startTask();
                        } catch (Exception e) {
                            log.warn("Failed to start sub client: {}", client, e);
                        }
                    }
                }

                log.info("Started {} pub clients, {} sub clients",
                        pubClients != null ? pubClients.size() : 0,
                        subClients != null ? subClients.size() : 0);

                vertx.runOnContext(v -> future.complete(StageResult.success(
                        "Started pub/sub clients")));

            } catch (Exception e) {
                log.error("Failed to start pub/sub clients", e);
                vertx.runOnContext(v -> future.complete(StageResult.failure(e)));
            }
            return null;
        });

        return future;
    }

    @Override
    public void onBefore(PipelineContext context) {
        log.info("Starting pub/sub clients...");
    }
}
