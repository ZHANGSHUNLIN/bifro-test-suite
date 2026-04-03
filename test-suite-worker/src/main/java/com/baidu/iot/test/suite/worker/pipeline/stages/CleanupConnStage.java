
package com.baidu.iot.test.suite.worker.pipeline.stages;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.baidu.iot.test.suite.ClientTask;
import com.baidu.iot.test.suite.ConnClientTask;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;
import com.baidu.iot.test.suite.worker.TaskConfig;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

/**
 * Stage for cleaning up connection clients.
 */
@Slf4j
public class CleanupConnStage implements PipelineStage<PipelineContext> {


    private final String clientTag;

    public CleanupConnStage(String clientTag) {
        this.clientTag = clientTag;
    }

    @Override
    public String getName() {
        return "CleanupConn";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();

        Vertx vertx = context.getConfigValue("vertx", Vertx.class);
        Map<String,ClientTask> clientTaskMap = (Map<String, ClientTask>) context.getStageData().get(clientTag);
        TaskConfig taskConfig = context.getConfigValue("taskConfig", TaskConfig.class);
        vertx.executeBlocking(() -> {
            try {
                com.google.common.util.concurrent.RateLimiter rateLimiter = taskConfig.getDisConnectRateLimiter();

                for (ClientTask client : clientTaskMap.values()) {
                    rateLimiter.acquire();
                    try {
                        client.close();
                    } catch (Exception e) {
                        log.warn("Failed to close client", e);
                    }
                }
                log.info("Cleaned up {} connection clients", clientTaskMap.size());
                clientTaskMap.clear();
                future.complete(StageResult.success("Cleanup completed"));

            } catch (Exception e) {
                log.error("Failed to cleanup connection clients", e);
                future.complete(StageResult.failure(e));
            }
            return null;
        });

        return future;
    }

    @Override
    public void onBefore(PipelineContext context) {
        Map<String,ClientTask> clientTaskMap = (Map<String, ClientTask>) context.getStageData().get(clientTag);
        log.info("Starting cleanup of {} connection clients", clientTaskMap.size());
    }

    @Override
    public void onAfter(PipelineContext context, StageResult result) {
        log.info("Cleanup completed: {}", result.getMessage());
    }

    @Override
    public void onError(PipelineContext context, Throwable error) {
        log.error("Error during cleanup", error);
    }

    @Override
    public void onCancelled(PipelineContext context) {
        this.execute(context);
    }
}
