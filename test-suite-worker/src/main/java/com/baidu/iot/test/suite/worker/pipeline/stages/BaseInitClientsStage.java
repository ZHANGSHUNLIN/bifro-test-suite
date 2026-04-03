package com.baidu.iot.test.suite.worker.pipeline.stages;

import com.baidu.iot.test.suite.ClientTask;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.client.MQTTClientWrapper;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.pipeline.PipelineContext;
import com.baidu.iot.test.suite.pipeline.PipelineStage;
import com.baidu.iot.test.suite.pipeline.StageResult;
import com.baidu.iot.test.suite.worker.TaskConfig;
import com.baidu.iot.test.suite.worker.utils.ConfigHelper;
import io.netty.util.internal.StringUtil;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public abstract class BaseInitClientsStage implements PipelineStage<PipelineContext> {






    abstract int clientSize(PipelineContext context);


    abstract  ClientTask buildClientTask(
            int index,
            Vertx vertx,
            TaskConfig taskConfig, ClientTaskConfig clientTaskConfig,
            MqttClientConfig mqttClientConfig,
            AtomicReference<TaskStage> sAtomicReference

    );

    @Override
    public CompletableFuture<StageResult> execute(PipelineContext context) {
        CompletableFuture<StageResult> future = new CompletableFuture<>();
        Vertx vertx = context.getConfigValue("vertx", Vertx.class);

        vertx.executeBlocking(() -> {

            Map<String, ClientTask> clientTaskMap = taskClientMap(context);


            TaskConfig taskConfig = context.getConfigValue("taskConfig", TaskConfig.class);
            try {
                AtomicInteger subscribeCount = new AtomicInteger(taskConfig.getThingIdStartAt());
                int clientTotalCount = clientSize(context);

                for (int i = 0; i < clientTotalCount; i++) {
                    if (context.isCancelled()) {
                        vertx.runOnContext(v -> future.complete(
                                StageResult.failure("Task cancelled during initialization")));
                        return null;
                    }

                    MqttClientConfig mqttClientConfig = taskConfig.getMqttClientConfig(
                            i, subscribeCount);
                    ClientTaskConfig clientTaskConfig = new ClientTaskConfig();
                    ConfigHelper.fillCommonTaskConfig(clientTaskConfig, taskConfig);


                    ClientTask clientTask = buildClientTask(i,vertx,taskConfig ,clientTaskConfig, mqttClientConfig,
                             context.getStateMachine().getCurrentStateReference());

                    clientTaskMap.put(mqttClientConfig.getClientId(), clientTask);
                }

                log.info("Initialized {} connection clients", clientTaskMap.size());
                vertx.runOnContext(v -> future.complete(
                        StageResult.success("Initialized " + clientTaskMap.size() + " clients")));

            } catch (Exception e) {
                log.error("Failed to initialize connection clients", e);
                vertx.runOnContext(v -> future.complete(StageResult.failure(e)));
            }
            return null;
        });

        return future;
    }

    @Override
    public void onBefore(PipelineContext context) {
        TaskConfig taskConfig = context.getConfigValue("taskConfig", TaskConfig.class);
        log.info("Starting to initialize connection clients, taskId: {}", taskConfig.getTaskId());
    }


    @Override
    public void onError(PipelineContext context, Throwable error) {
        log.error("Error during connection client initialization", error);
    }

//    public Map<String, ClientTask> taskClientMap(PipelineContext context){
//        ClientTaskType clientTaskType = taskType();
//        Map<String, ClientTask> connClients;
//        if (clientTaskType == ClientTaskType.CONN) {
//            connClients = (Map<String, ClientTask>) context.getStageData().get("connClients");
//        } else if (clientTaskType == ClientTaskType.PUB) {
//            connClients = (Map<String, ClientTask>) context.getStageData().get(Constants.SUB_CLIENT_TAG);
//        } else {
//            connClients = (Map<String, ClientTask>) context.getStageData().get(Constants.PUB_CLIENT_TAG);
//        }
//        return connClients;
//    }

    abstract Map<String, ClientTask> taskClientMap(PipelineContext context);


    protected String buildClientTopic(TaskConfig taskConfig, int topicIndex, boolean isSub, boolean isWildcard) {
        StringBuilder result = new StringBuilder();
        String finalTopic;
        // 如果是subOnly将不再拼接topic。
        if (taskConfig.isSubOnly()) {
            finalTopic = taskConfig.getTopic();
        } else {
            finalTopic = taskConfig.isFixedTopic() ?
                    String.format("%s/%d", taskConfig.getTopic(), topicIndex) :
                    String.format("%s/%s/%s/%d", taskConfig.getTopic(), taskConfig.getTaskId(), nodeIdPrefix(taskConfig.getNodeId()), topicIndex);
        }

        result.append(finalTopic);
        if (isWildcard) {
            result.append("/");
            result.append(isSub ? "+" : "suffix");
        }
        if (isSub) {
            log.trace("sub_result_topic: {}", result);
        } else {
            log.trace("pub_result_topic: {}", result);
        }
        return result.toString();
    }

    private String nodeIdPrefix(String nodeId) {
        if (StringUtil.isNullOrEmpty(nodeId)) {
            log.warn("nodeId is null");
            return "";
        }
        return nodeId.substring(0, 4);
    }

}
