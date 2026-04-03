
package com.baidu.iot.test.suite.worker.pipeline.stages;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.baidu.iot.test.suite.ClientTask;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.SubClientTask;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.models.TopicFilter;
import com.baidu.iot.test.suite.pipeline.PipelineContext;

import com.baidu.iot.test.suite.worker.TaskConfig;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import static com.baidu.iot.test.suite.constants.ClientTaskType.SUB;

/**
 * Stage for initializing sub clients.
 */
@Slf4j
public class InitSubClientsStage extends BaseInitClientsStage{



    @Override
    public String getName() {
        return "InitSubClients";
    }

    @Override
    int clientSize(PipelineContext context) {
        return context.getExpectSubCount();
    }

    @Override
    Map<String, ClientTask> taskClientMap(PipelineContext context) {
        Object subClients = context.getStageData().get(Constants.SUB_CLIENT_TAG);
        assert subClients != null : "subClients should not be null";
        return (Map<String, ClientTask>) subClients;
    }

    @Override
      ClientTask buildClientTask(
            int index,
            Vertx vertx, TaskConfig taskConfig, ClientTaskConfig clientTaskConfig, MqttClientConfig mqttClientConfig, AtomicReference<TaskStage> sAtomicReference) {

        clientTaskConfig.setType(SUB);
        clientTaskConfig.setType(SUB);
        // TODO Only plain topicFilter now
        int topicIndex = taskConfig.getFanOut() > 1 ?
                (index + taskConfig.getFanOut() - 1) / taskConfig.getFanOut() : index;
        String topic = buildClientTopic(taskConfig,topicIndex, true, taskConfig.isWildcard());
        clientTaskConfig.setTopicFilters(new HashSet<TopicFilter>() {{
            add(new TopicFilter(topic, taskConfig.getQos()));
        }});

        return new SubClientTask(
                vertx, clientTaskConfig, mqttClientConfig,
                sAtomicReference);
    }

    @Override
    public void onBefore(PipelineContext context) {
        log.info("Starting to initialize sub clients, count: {}",
                context.getExpectSubCount());
    }


}
