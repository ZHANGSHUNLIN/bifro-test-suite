
package com.baidu.iot.test.suite.worker.pipeline.stages;

import com.baidu.iot.test.suite.ClientTask;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.models.ClientTaskEvent;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;
import com.google.common.util.concurrent.RateLimiter;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stage for starting connection clients.
 */
@Slf4j
public abstract class BaseConnClientsStage implements PipelineStage<PipelineContext> {

    private final CompletableFuture<StageResult> stageFuture = new CompletableFuture<>();

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        AtomicInteger connectedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        // Register event handler for connection results
        Map<String, ClientTask> connClients = taskClientMap(context);
        Vertx vertx = context.getConfigValue("vertx", Vertx.class);

        // Start all clients with rate limiting (matching original TaskConnWorker behavior)
        RateLimiter rateLimiter = (RateLimiter) context.getConfig().get("connectRateLimiter");
        vertx.executeBlocking(() -> {
            log.info("Start to connect: {}", connClients.size());
            CompletableFuture<?>[] completableFutures = new CompletableFuture[connClients.size()];

            int idx =0;
            for (ClientTask clientTask : connClients.values()) {
                if (context.isCancelled()) {
                    break;
                }
                rateLimiter.acquire();
                log.debug("Start to connect to {}", clientTask.getCId());
                completableFutures[idx++] = clientTask.initTask();
            }
            return CompletableFuture.allOf(completableFutures);
        }).onComplete((result, failure) -> {

            if (failure != null) {
                log.error("Error during connection", failure);
                stageFuture.complete(StageResult.failure(
                        String.format("Start to connect clients error: %s", failure.getMessage())));
                return;
            }

            int total = connClients.size();
            int success = connectedCount.get();
            int failed = failedCount.get();

            log.info("Connection completed: success={}, failed={}, total={}", success, failed, total);

            result.whenComplete((__, throwable) -> {
                if (throwable != null) {
                    stageFuture.complete(StageResult.failure(
                    String.format("Connected %d clients, failed %d", success, failed)));
                    return;
                }

                stageFuture.complete(StageResult.success(
                        String.format("Connected %d clients, failed %d", success, failed)));
            });


        });

        return stageFuture;
    }


    @Override
    public void onBefore(PipelineContext context) {
        Map<String, ClientTask> connClients = taskClientMap(context);
        log.info("Starting to connect {} clients", connClients.size());
    }

    @Override
    public void onAfter(PipelineContext context, StageResult result) {
        log.info("Connection stage completed: {}", result.getMessage());
    }

    @Override
    public void onError(PipelineContext context, Throwable error) {
        log.error("Error during client connection", error);
    }

    @Override
    public CompletableFuture<Void> cancel(PipelineContext context) {
        stageFuture.complete(StageResult.success());
        return CompletableFuture.completedFuture(null);
    }


    abstract Map<String, ClientTask> taskClientMap(PipelineContext context);

}
