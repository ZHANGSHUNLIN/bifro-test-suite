
package com.baidu.iot.test.suite.worker.pipeline.stages;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.baidu.iot.test.suite.ClientTask;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

/**
 * Stage for waiting for all clients to be ready.
 */
@Slf4j
public class WaitForClientsReadyStage implements PipelineStage<PipelineContext> {

    private final Vertx vertx;
    private final String taskId;

    public WaitForClientsReadyStage(Vertx vertx, String taskId) {
        this.vertx = vertx;
        this.taskId = taskId;
    }

    @Override
    public String getName() {
        return "WaitForClientsReady";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();

        // Check if all clients are ready
        @SuppressWarnings("unchecked")
        Map<String, ClientTask> pubClients =
                (Map<String, ClientTask>) context.getStageData().get(Constants.PUB_CLIENT_TAG);
        @SuppressWarnings("unchecked")
        Map<String, ClientTask> subClients =
                (Map<String, ClientTask>) context.getStageData().get(Constants.SUB_CLIENT_TAG);

        int readyPubCount = pubClients != null ? 0 : pubClients.size();
        int readySubCount = subClients != null ? 0 : subClients.size();

        int expectPubCount = context.getExpectPubCount();
        int expectSubCount = context.getExpectSubCount();

        // Check if ready counts meet thresholds
        boolean pubReady = readyPubCount >= expectPubCount;
        boolean subReady = readySubCount >= expectSubCount;

        log.info("Clients ready: pub={}/{}, sub={}/{}, expectedPub={}, expectedSub={}",
                readyPubCount, expectPubCount, readySubCount, expectSubCount);

        if (pubReady && subReady) {
            log.info("All clients ready, transitioning to ONGOING");
            context.getStateMachine()
                    .transition(com.baidu.iot.test.suite.TaskEvent.ALL_CLIENTS_READY)
                    .thenApply(success -> {
                        if (success) {
                            return StageResult.success("All clients ready");
                        }
                        return StageResult.failure("State transition failed");
                    })
                    .whenComplete((result, e) -> future.complete(result));
        } else {
            // Not all clients ready within stage timeout
            log.warn("Not all clients ready, pub={}/{}, sub={}/{}",
                    readyPubCount, expectPubCount, readySubCount, expectSubCount);
            future.complete(StageResult.success(
                    "Proceeding with " + readyPubCount + " pub and " + readySubCount + " sub clients"));
        }

        return future;
    }

    @Override
    public void onBefore(PipelineContext context) {
        log.info("Waiting for clients ready...");
    }
}
