
package com.baidu.iot.test.suite.worker.pipeline.stages;

import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.TaskSchedule;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Stage for cleaning up connection clients.
 */
@Slf4j
public class TaskFinishEventStage implements PipelineStage<PipelineContext> {


    @Override
    public String getName() {
        return "TaskFinishEvent";
    }

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        String taskId = context.getConfigValue("taskId", String.class);
        Vertx vertx = context.getConfigValue("vertx", Vertx.class);

        TaskSchedule taskSchedule =
                TaskSchedule.builder().op(TaskSchedule.Op.TASK_FINISH).id(taskId).build();

        // 通知全部节点任务准备
        vertx.eventBus().publish(Constants.CLUSTER_TASK_MESSAGE, taskSchedule);


        return CompletableFuture.completedFuture(StageResult.success());
    }

    @Override
    public void onBefore(PipelineContext context) {
        log.info("TaskFinishEventStage start");
    }

    @Override
    public void onAfter(PipelineContext context, StageResult result) {
        log.info("TaskFinishEventStage end , result: {}", result);
    }

    @Override
    public void onError(PipelineContext context, Throwable error) {
        log.error("Error during cleanup", error);
    }
}
