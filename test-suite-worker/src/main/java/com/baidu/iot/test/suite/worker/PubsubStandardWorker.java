
package com.baidu.iot.test.suite.worker;

import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.TaskEvent;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.StageResult;
import com.baidu.iot.test.suite.pipeline.TaskPipeline;
import com.baidu.iot.test.suite.pipeline.stages.StateTransitionStage;
import com.baidu.iot.test.suite.statemachine.StateMachine;
import com.baidu.iot.test.suite.statemachine.TaskStateMachineConfig;
import com.baidu.iot.test.suite.worker.pipeline.stages.CleanupConnStage;
import com.baidu.iot.test.suite.worker.pipeline.stages.InitPubClientsStage;
import com.baidu.iot.test.suite.worker.pipeline.stages.InitSubClientsStage;
import com.baidu.iot.test.suite.worker.pipeline.stages.StartConnClientsStage;
import com.baidu.iot.test.suite.worker.pipeline.stages.StartPubSubClientsStage;
import com.baidu.iot.test.suite.worker.pipeline.stages.StressStage;
import com.baidu.iot.test.suite.worker.pipeline.stages.TaskFinishEventStage;
import io.vertx.core.Vertx;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PubsubStandardWorker extends BaseTaskWorker {


    public PubsubStandardWorker(Vertx vertx, TaskConfig taskConfig) {
        super(vertx, taskConfig);
        // Create init stages (they only need Vertx)
        initPipeline();
    }

    @Override
    StateMachine<TaskStage, TaskEvent> getStateMachine() {
        return TaskStateMachineConfig.createPubSub();
    }




    @Override
    protected TaskPipeline<PipelineContext> buildPipeline() {


        return TaskPipeline.builder()
                // State: START
                .addStage(new StateTransitionStage(TaskStage.START, TaskEvent.INIT_PUB))
                // Initialize pub clients
                .addStage(new InitPubClientsStage())
                // State: INIT_PUB_CLIENT
                .addStage(new StateTransitionStage(TaskStage.INIT_PUB_CLIENT, TaskEvent.PUB_READY))
                // Initialize sub clients
                .addStage(new InitSubClientsStage())
                // State: INIT_SUB_CLIENT
                .addStage(new StateTransitionStage(TaskStage.INIT_SUB_CLIENT, TaskEvent.INIT_SUB))
                // Wait for stageTimeoutInSec before starting (mimics original behavior)
                .addStage(new WaitForStageTimeoutStage(taskConfig.getStageTimeoutInSec()))
                // State: ONGOING
                .addStage(new StateTransitionStage(TaskStage.PUB_SUB_CLIENT_READY, TaskEvent.ALL_CLIENTS_READY))
                .addStage(new StartConnClientsStage(Constants.PUB_CLIENT_TAG))

                .addStage(new StateTransitionStage(TaskStage.PUB_CLIENT_CONN, TaskEvent.PUB_CONN))
                .addStage(new StartConnClientsStage(Constants.SUB_CLIENT_TAG))

                .addStage(new StateTransitionStage(TaskStage.SUB_CLIENT_CONN, TaskEvent.SUB_CONN))

                .addStage(new StartPubSubClientsStage(vertx))

                .addStage(new StateTransitionStage(TaskStage.PUB_SUB_CLIENT_START, TaskEvent.START_PUBSUB_CLIENT_TASK))

                // Start pub/sub clients
                .addStage(new StressStage(vertx, taskConfig))

                // Collect results during stress test duration
                .addStage(new StateTransitionStage(TaskStage.ONGOING, TaskEvent.SHUTTING))


                // State: SHUTDOWN
                // Cleanup
                .addStage(new CleanupConnStage(Constants.PUB_CLIENT_TAG))
                .addStage(new CleanupConnStage(Constants.SUB_CLIENT_TAG))
                .addStage(new TaskFinishEventStage())
                .addStage(new StateTransitionStage(TaskStage.SHUTTING, TaskEvent.SHUTDOWN))
                .build();



    }

    /**
     * Stage that waits for stageTimeoutInSec before starting clients.
     * Matches original behavior where a timer is set after init.
     */
    private static class WaitForStageTimeoutStage implements com.baidu.iot.test.suite.pipeline.PipelineStage<PipelineContext> {

        private final int timeoutSec;

        WaitForStageTimeoutStage(int timeoutSec) {
            this.timeoutSec = timeoutSec;
        }

        @Override
        public String getName() {
            return "WaitForStageTimeout";
        }

        @Override
        public CompletableFuture<StageResult> execute(PipelineContext context) {
            CompletableFuture<com.baidu.iot.test.suite.pipeline.StageResult> future =
                    new CompletableFuture<>();
            context.getVertx().setTimer(timeoutSec * 1000L, id -> {
                future.complete(com.baidu.iot.test.suite.pipeline.StageResult.success(
                        "Stage timeout reached, ready to start clients"));
            });
            return future;
        }
    }

    @Override
    protected PipelineContext createPipelineContext() {
        PipelineContext pipelineContext = super.createPipelineContext();
        pipelineContext.getStageData().put(Constants.PUB_CLIENT_TAG, new java.util.concurrent.ConcurrentHashMap<>());
        pipelineContext.getStageData().put(Constants.SUB_CLIENT_TAG, new ConcurrentHashMap<>());
        return pipelineContext;
    }
}
