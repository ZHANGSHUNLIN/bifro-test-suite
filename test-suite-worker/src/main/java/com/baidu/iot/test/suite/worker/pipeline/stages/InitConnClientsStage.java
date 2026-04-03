
package com.baidu.iot.test.suite.worker.pipeline.stages;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.baidu.iot.test.suite.ClientTask;
import com.baidu.iot.test.suite.ConnClientTask;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.StageResult;
import com.baidu.iot.test.suite.worker.TaskConfig;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

/**
 * Stage for initializing connection clients.
 */
@Slf4j
public class InitConnClientsStage extends BaseInitClientsStage {


    @Override
    int clientSize(PipelineContext context) {
        TaskConfig taskConfig = context.getConfigValue("taskConfig", TaskConfig.class);
        return taskConfig.getTotalClientCount();
    }


    @Override
    ClientTask buildClientTask(
            int index,
            Vertx vertx,
            TaskConfig taskConfig, ClientTaskConfig clientTaskConfig,
            MqttClientConfig mqttClientConfig,
            AtomicReference<TaskStage> sAtomicReference) {

        return new ConnClientTask(
                vertx, clientTaskConfig, mqttClientConfig,
                sAtomicReference);
    }

    @Override
    public String getName() {
        return "InitConnClients";
    }


    @Override
    public void onBefore(PipelineContext context) {
        TaskConfig taskConfig = context.getConfigValue("taskConfig", TaskConfig.class);
        log.info("Starting to initialize connection clients, taskId: {}", taskConfig.getTaskId());
    }

    @Override
    public void onAfter(PipelineContext context, StageResult result) {
        if (result.isSuccess()) {
            Map<String, ClientTask> connClients = taskClientMap(context);
            log.info("Successfully initialized {} connection clients", connClients.size());
            context.getStageData().put("connClientsCount", connClients.size());
        } else {
            log.error("Failed to initialize connection clients: {}", result.getMessage());
        }
    }


    @Override
    public void onError(PipelineContext context, Throwable error) {
        log.error("Error during connection client initialization", error);
    }

    @Override
    Map<String, ClientTask> taskClientMap(PipelineContext context) {
        Object connClients = context.getStageData().get(Constants.CONN_CLIENT_TAG);
        assert connClients != null : "connClients should not be null";
        return (Map<String, ClientTask>) connClients;
    }


}
