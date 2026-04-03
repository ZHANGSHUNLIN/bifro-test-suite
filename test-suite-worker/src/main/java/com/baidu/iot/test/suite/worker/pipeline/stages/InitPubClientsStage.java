
package com.baidu.iot.test.suite.worker.pipeline.stages;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.baidu.iot.test.suite.ClientTask;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.PubClientTask;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.pipeline.PipelineContext;

import com.baidu.iot.test.suite.worker.TaskConfig;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import static com.baidu.iot.test.suite.constants.ClientTaskType.PUB;

/**
 * Stage for initializing pub clients.
 */
@Slf4j
public class InitPubClientsStage extends BaseInitClientsStage {


    @Override
    public String getName() {
        return "InitPubClients";
    }

    @Override
    int clientSize(PipelineContext context) {
        return context.getExpectPubCount();
    }

    @Override
    Map<String, ClientTask> taskClientMap(PipelineContext context) {
        Object pubClients = context.getStageData().get(Constants.PUB_CLIENT_TAG);
        assert pubClients != null : "pubClients should not be null";
        return (Map<String, ClientTask>) pubClients;
    }

    @Override
    ClientTask buildClientTask(
            int index,

            Vertx vertx, TaskConfig taskConfig, ClientTaskConfig clientTaskConfig, MqttClientConfig mqttClientConfig,
            AtomicReference<TaskStage> sAtomicReference) {

        clientTaskConfig.setType(PUB);
        int topicIndex = taskConfig.getFanIn() > 1 ?
                (index + taskConfig.getFanIn() - 1) / taskConfig.getFanIn() : index;

        clientTaskConfig.setPubTopic(buildClientTopic(taskConfig,topicIndex, false, taskConfig.isWildcard()));


        return new PubClientTask(vertx, clientTaskConfig, mqttClientConfig, sAtomicReference);
    }

    @Override
    public void onBefore(PipelineContext context) {
        log.info("Starting to initialize pub clients, count: {}",
                context.getExpectPubCount());
    }
}
