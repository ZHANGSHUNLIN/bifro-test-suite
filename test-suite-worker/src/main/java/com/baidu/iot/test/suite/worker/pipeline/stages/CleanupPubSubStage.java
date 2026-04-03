
package com.baidu.iot.test.suite.worker.pipeline.stages;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.baidu.iot.test.suite.ClientTask;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

/**
 * Stage for cleaning up pub/sub clients.
 */
@Slf4j
public class CleanupPubSubStage implements PipelineStage<PipelineContext> {

    private final Vertx vertx;
    private final String taskId;

    public CleanupPubSubStage(Vertx vertx, String taskId) {
        this.vertx = vertx;
        this.taskId = taskId;
    }

    @Override
    public String getName() {
        return "CleanupPubSub";
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

                com.google.common.util.concurrent.RateLimiter rateLimiter =
                        (com.google.common.util.concurrent.RateLimiter) context.getConfig()
                                .get("disConnectRateLimiter");

                // Close pub clients
                if (pubClients != null && !pubClients.isEmpty()) {
                    for (ClientTask client : pubClients.values()) {
                        rateLimiter.acquire();
                        try {
                            client.close();
                        } catch (Exception e) {
                            log.warn("Failed to close pub client", e);
                        }
                    }
                    pubClients.clear();
                }

                // Close sub clients
                if (subClients != null && !subClients.isEmpty()) {
                    for (ClientTask client : subClients.values()) {
                        rateLimiter.acquire();
                        try {
                            client.close();
                        } catch (Exception e) {
                            log.warn("Failed to close sub client", e);
                        }
                    }
                    subClients.clear();
                }

                // Reset stats managers
                com.baidu.iot.test.suite.stats.TaskPubStatsManager pubStatsManager =
                        (com.baidu.iot.test.suite.stats.TaskPubStatsManager) context.getConfig()
                                .get("pubStatsManager");
                com.baidu.iot.test.suite.stats.TaskSubStatsManager subStatsManager =
                        (com.baidu.iot.test.suite.stats.TaskSubStatsManager) context.getConfig()
                                .get("subStatsManager");

                if (pubStatsManager != null) {
                    pubStatsManager.reset();
                }
                if (subStatsManager != null) {
                    subStatsManager.reset();
                }

                // Shutdown stats executor
                ExecutorService statsExecutor =
                        (ExecutorService) context.getConfig().get("statsExecutor");
                if (statsExecutor != null) {
                    statsExecutor.shutdown();
                }

                log.info("Cleaned up pub/sub clients for task: {}", taskId);
                vertx.runOnContext(v -> future.complete(StageResult.success("Cleanup completed")));

            } catch (Exception e) {
                log.error("Failed to cleanup pub/sub clients", e);
                vertx.runOnContext(v -> future.complete(StageResult.failure(e)));
            }
            return null;
        });

        return future;
    }

    @Override
    public void onBefore(PipelineContext context) {
        log.info("Starting cleanup of pub/sub clients");
    }

    @Override
    public void onAfter(PipelineContext context, StageResult result) {
        log.info("Cleanup completed: {}", result.getMessage());
    }

    @Override
    public void onError(PipelineContext context, Throwable error) {
        log.error("Error during cleanup", error);
    }
}
