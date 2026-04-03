
package com.baidu.iot.test.suite.worker;

import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.TaskEvent;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.client.MQTTClientWrapper;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.TaskPipeline;
import com.baidu.iot.test.suite.pipeline.stages.ErrorHandlingStage;
import com.baidu.iot.test.suite.pipeline.stages.StateTransitionStage;
import com.baidu.iot.test.suite.statemachine.StateMachine;
import com.baidu.iot.test.suite.statemachine.TaskStateMachineConfig;
import com.baidu.iot.test.suite.worker.pipeline.stages.CleanupConnStage;
import com.baidu.iot.test.suite.worker.pipeline.stages.InitConnClientsStage;
import com.baidu.iot.test.suite.worker.pipeline.stages.StartConnClientsStage;
import com.baidu.iot.test.suite.worker.pipeline.stages.StressStage;
import com.baidu.iot.test.suite.worker.pipeline.stages.TaskFinishEventStage;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import io.vertx.core.Vertx;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Standard connection task worker.
 */
public class ConnStandardWorker extends BaseTaskWorker {


    public ConnStandardWorker(Vertx vertx, TaskConfig taskConfig) {
        super(vertx, taskConfig);
        initPipeline();
    }

    @Override
    StateMachine<TaskStage, TaskEvent> getStateMachine() {
        return TaskStateMachineConfig.createConn();
    }

    @Override
    protected TaskPipeline<PipelineContext> buildPipeline() {

        CleanupConnStage cleanupConnStage = new CleanupConnStage(Constants.CONN_CLIENT_TAG);

        return TaskPipeline.builder()
                // State: START
                .addStage(new StateTransitionStage(TaskStage.START, TaskEvent.INIT_CONN))
                // Initialize connection client objects
                .addStage(new InitConnClientsStage())
                // State: CONNECTING
                .addStage(new StateTransitionStage(TaskStage.INIT_CLIENT, TaskEvent.START_CONN_CLIENT_TASK))
                // Start actual connections
                .addStage(new StartConnClientsStage(Constants.CONN_CLIENT_TAG))
                // State: STRESS
                .addStage(new StressStage(vertx, taskConfig).withTransition(TaskStage.ONGOING, TaskEvent.SHUTTING))
                .addStage(cleanupConnStage)
                .addStage(new TaskFinishEventStage())
                // State: SHUTDOWN
                .addStage(new StateTransitionStage(TaskStage.SHUTTING, TaskEvent.SHUTDOWN))
                .onError(new ErrorHandlingStage())
                // 取消动作
                .onCancel(cleanupConnStage)
                .build();
    }


    @Override
    protected PipelineContext createPipelineContext() {
        PipelineContext pipelineContext = super.createPipelineContext();
        pipelineContext.getStageData().put(Constants.CONN_CLIENT_TAG, new ConcurrentHashMap<>());
        return pipelineContext;
    }
}
