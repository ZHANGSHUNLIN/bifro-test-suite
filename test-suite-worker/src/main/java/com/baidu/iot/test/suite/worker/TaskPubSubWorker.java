/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite.worker;

import static com.baidu.iot.test.suite.constants.ClientTaskType.PUB;
import static com.baidu.iot.test.suite.constants.ClientTaskType.SUB;
import static com.baidu.iot.test.suite.constants.CommonConstants.PUB_LATENCY_STATS_RESULT;
import static com.baidu.iot.test.suite.constants.CommonConstants.SUB_LATENCY_STATS_RESULT;
import static com.baidu.iot.test.suite.constants.CommonConstants.SUCCESS;

import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.PubClientTask;
import com.baidu.iot.test.suite.ShareDataManager;
import com.baidu.iot.test.suite.SubClientTask;
import com.baidu.iot.test.suite.ClientTask;
import com.baidu.iot.test.suite.TaskSchedule;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.models.ClientTaskEvent;
import com.baidu.iot.test.suite.models.TopicFilter;
import com.baidu.iot.test.suite.stats.TaskPubStatsManager;
import com.baidu.iot.test.suite.stats.TaskSubStatsManager;
import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;
import com.baidu.iot.test.suite.stats.pojo.StatsPubResult;
import com.baidu.iot.test.suite.stats.pojo.StatsSubResult;
import com.baidu.iot.test.suite.utils.TaskUtils;
import com.baidu.iot.test.suite.worker.models.WorkerTaskEvent;
import com.baidu.iot.test.suite.worker.utils.ConfigHelper;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by mafei01 in 3/15/21 11:27 AM
 */
@Slf4j
public class TaskPubSubWorker extends BaseTaskWorker {

    /**
     * 打印周期性tag的专用日志打印
     */
    Logger tagLogger = LoggerFactory.getLogger("tagLogger");

    @Getter
    private final TaskPubStatsManager pubStatsManager;
    @Getter
    private final TaskSubStatsManager subStatsManager;


    private final Map<String, ClientTask> pubClients = new HashMap<>();
    private final Map<String, ClientTask> subClients = new HashMap<>();
    private final String workerEventAddr;
    private final Set<String> readyPubClients = new HashSet<>();
    private final Set<String> readySubClients = new HashSet<>();
    private final ExecutorService statsExecutor;

    private int expectPubCount;
    private int expectSubCount;
    private long stageTimer;
    private long startClientTimer;
    private long periodCollectTimer;

    private final AtomicInteger subscribeCount;

    public TaskPubSubWorker(Vertx vertx, TaskConfig taskConfig) {
        super(vertx, taskConfig);
        subscribeCount = new AtomicInteger(taskConfig.getThingIdStartAt());
        this.statsExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder()
                        .setDaemon(false)
                        .setNameFormat("stats-executor-%d")
                        .setPriority(Thread.MAX_PRIORITY)
                        .build());
        this.pubStatsManager =
                new TaskPubStatsManager(taskConfig.getTaskId(), taskConfig.getSkipStatsPeriod(), statsExecutor);
        this.subStatsManager =
                new TaskSubStatsManager(taskConfig.getTaskId(), taskConfig.getSkipStatsPeriod(), statsExecutor);
        this.workerEventAddr = TaskUtils.getWorkerTaskAddr(taskConfig.getTaskId());
    }

    public void startTask() {
        taskStage.set(TaskStage.START);
        eventReport();
//        统计报告
        this.vertx.eventBus()
                .localConsumer(TaskUtils.getClientTaskAddr(taskConfig.getTaskId()), this::handleClientTaskEvent);
        vertx.executeBlocking(() -> {

            // 计算 pub 和 sub的client 数量；
            if (taskConfig.isPubOnly()) {
                expectSubCount = 0;
                expectPubCount = taskConfig.getTotalClientCount();
            } else if (taskConfig.isSubOnly()) {
                expectSubCount = taskConfig.getTotalClientCount();
                expectPubCount = 0;
            } else if (taskConfig.getFanIn() > 1) {
                /*
                    公式注解：fanin为特定比例的 pub和sub的消息模式； 多个pub给同一个topic发消息；
                    设 n = topic 数量，本次实例中topic数量和sub相同；故 n = topic = sub; pub = fanin * sub;
                    total = (fanin * sub) + sub;   注意： (fanin * sub) 为pub的数量；
                    (fanin + 1) * sub = total;
                    答： sub 数量为 total / (fanin + 1)
                        pub 数量为 total / sub
                 */
                expectPubCount =
                        taskConfig.getFanIn() * (taskConfig.getTotalClientCount() / (taskConfig.getFanIn() + 1));

                expectSubCount = taskConfig.getTotalClientCount() / (taskConfig.getFanIn() + 1);
            } else {
                expectSubCount =
                        taskConfig.getFanOut() * (taskConfig.getTotalClientCount() / (taskConfig.getFanOut() + 1));
                expectPubCount = taskConfig.getTotalClientCount() / (taskConfig.getFanOut() + 1);
            }
            log.info("expectPubCount: {}, expectSubCount: {}", expectPubCount, expectSubCount);
            initPubClients();
            initSubClients();
            if (canceled()) {
                return null;
            }
            stageTimer = vertx.setTimer(taskConfig.getStageTimeoutInSec() * 1000L, t -> startClientTask());
            return null;
        });
    }

    public CompletableFuture<Void> stopTask() {
        log.info("stopTask , taskId: {} ", taskConfig.getTaskId());
        taskStage.set(TaskStage.SHUTDOWN_ING);
        eventReport();
        vertx.cancelTimer(stageTimer);
        vertx.cancelTimer(startClientTimer);
        vertx.cancelTimer(periodCollectTimer);
        CompletableFuture<Void> result = new CompletableFuture<>();
        vertx.executeBlocking(() -> {
            RateLimiter rateLimiter = taskConfig.getDisConnectRateLimiter();
            for (Map.Entry<String, ClientTask> clientWrapperEntry : pubClients.entrySet()) {
                rateLimiter.acquire();
                clientWrapperEntry.getValue().close();
            }
            for (Map.Entry<String, ClientTask> clientWrapperEntry : subClients.entrySet()) {
                rateLimiter.acquire();
                clientWrapperEntry.getValue().close();
            }
            WorkerTaskEvent taskEvent = WorkerTaskEvent.builder()
                    .taskId(taskConfig.getTaskId())
                    .eventType(WorkerTaskEvent.EventType.TASK_END)
                    .build();
            vertx.eventBus().send(workerEventAddr, taskEvent, ShareDataManager.getLocalDeliveryOptions());
            result.complete(null);
            return null;
        });
        statsExecutor.shutdown();
        vertx.eventBus().publish(Constants.CLUSTER_TASK_MESSAGE, TaskSchedule.builder()
                .op(TaskSchedule.Op.TASK_FINISH)
                .id(taskConfig.getTaskId().substring(0, 8))
                .build(), ShareDataManager.getLocalDeliveryOptions());
        subStatsManager.reset();
        pubStatsManager.reset();
        taskStage.set(TaskStage.SHUTDOWN);
        eventReport();
        return result;
    }

    /**
     * Stage 1, init mqtt clients and connect.
     */
    private void initPubClients() {
        taskStage.compareAndSet(TaskStage.START, TaskStage.INIT_PUB_CLIENT);
        eventReport();
        int clientIndex = 0;
        int topicIndex;
        RateLimiter rateLimiter = taskConfig.getConnectRateLimiter();
        log.info("expectPubCount: {}", expectPubCount);
        for (int i = 0; i < expectPubCount; i++) {
            if (canceled()) {
                return;
            }
            rateLimiter.acquire();

            MqttClientConfig mqttClientConfig = taskConfig.getMqttClientConfig(clientIndex++, subscribeCount);
            String clientId = mqttClientConfig.getClientId();
            log.trace("mqtt pub client : clientid:{} , username: {}, pwd: {}", clientId, mqttClientConfig.getUsername(),
                    mqttClientConfig.getPassword());

            ClientTaskConfig pubTaskConfig = new ClientTaskConfig();
            pubTaskConfig.setType(PUB);
            topicIndex = taskConfig.getFanIn() > 1 ?
                    (clientIndex + taskConfig.getFanIn() - 1) / taskConfig.getFanIn() : clientIndex;
            pubTaskConfig.setPubTopic(buildClientTopic(topicIndex, false, taskConfig.isWildcard()));
            ConfigHelper.fillCommonTaskConfig(pubTaskConfig, taskConfig);
            PubClientTask pubClientTask =
                    new PubClientTask(vertx, pubTaskConfig, mqttClientConfig, pubStatsManager, taskStage);
            pubClients.put(clientId, pubClientTask);
            pubClientTask.initTask();
        }
        log.info("pubClients size: {}", pubClients.size());
        taskStage.compareAndSet(TaskStage.INIT_PUB_CLIENT, TaskStage.INIT_PUB_CLIENTED);

    }

    /**
     * Stage 1, init mqtt clients and connect and sub.
     */
    private void initSubClients() {
        taskStage.compareAndSet(TaskStage.INIT_PUB_CLIENTED, TaskStage.INIT_SUB_CLIENT);
        eventReport();
        int clientIndex = 0;
        int topicIndex;
        RateLimiter rateLimiter = taskConfig.getConnectRateLimiter();
        log.info("expectSubCount: {}", expectSubCount);
        for (int i = 0; i < expectSubCount; i++) {
            if (canceled()) {
                return;
            }
            rateLimiter.acquire();

            MqttClientConfig mqttClientConfig = taskConfig.getMqttClientConfig(clientIndex++, subscribeCount);
            String clientId = mqttClientConfig.getClientId();
            log.trace("mqtt sub client : clientId:{} , username: {}, pwd: {}", clientId, mqttClientConfig.getUsername(),
                    mqttClientConfig.getPassword());

            ClientTaskConfig subTaskConfig = new ClientTaskConfig();
            subTaskConfig.setType(SUB);
            // TODO Only plain topicFilter now
            topicIndex = taskConfig.getFanOut() > 1 ?
                    (clientIndex + taskConfig.getFanOut() - 1) / taskConfig.getFanOut() : clientIndex;
            String topic = buildClientTopic(topicIndex, true, taskConfig.isWildcard());
            subTaskConfig.setTopicFilters(new HashSet<TopicFilter>() {{
                add(new TopicFilter(topic, taskConfig.getQos()));
            }});
            ConfigHelper.fillCommonTaskConfig(subTaskConfig, taskConfig);
            SubClientTask subClientTask =
                    new SubClientTask(vertx, subTaskConfig, mqttClientConfig, subStatsManager, taskStage);
            subClients.put(clientId, subClientTask);
            subClientTask.initTask();
        }
        log.info("subClients size: {}", subClients.size());
        taskStage.compareAndSet(TaskStage.INIT_SUB_CLIENT, TaskStage.INIT_SUB_CLIENTED);
    }

    /**
     * Stage 2, start pub&sub in clients.
     */
    private void startClientTask() {
        taskStage.compareAndSet(TaskStage.INIT_SUB_CLIENTED, TaskStage.ONGOING);
        eventReport();
        log.info("Pubsub task: {} start , pubClients: {} , subClients: {}, readyPubClients: {} , readySubClients: {}",
                taskConfig.getTaskId(), pubClients.size(), subClients.size(), readySubClients.size(), readySubClients.size());
        startReadyClients(pubClients, readyPubClients::contains);
        startReadyClients(subClients, readySubClients::contains);

        readyPubClients.clear();
        readySubClients.clear();
        stageTimer = vertx.setTimer((taskConfig.getStressDurationInSec() + taskConfig.getStageTimeoutInSec()) * 1000L,
                t -> collectTaskResults());
        if (taskConfig.getTagPeriodIntervalInSec() > 0) {
            periodCollectTimer = vertx.setPeriodic(taskConfig.getTagPeriodIntervalInSec() * 1000L, t -> {
                statsExecutor.execute(() -> {
                    long start = System.currentTimeMillis();
                    StatsBasicResult periodPubLatencyStats = pubStatsManager.tagPeriodResult();
                    StatsBasicResult periodSubLatencyStats = subStatsManager.tagPeriodResult();
                    eventReport(periodPubLatencyStats);
                    eventReport(periodSubLatencyStats);
                    // TODO just print result
                    tagLogger.info("Task {} period pub latency stats: {}",
                            taskConfig.getTaskId(), Json.encodePrettily(periodPubLatencyStats));
                    tagLogger.info("Task {} period sub latency stats: {}",
                            taskConfig.getTaskId(), Json.encodePrettily(periodSubLatencyStats));
                    tagLogger.info("period stats costs {}ms", System.currentTimeMillis() - start);
                    WorkerTaskEvent event = WorkerTaskEvent.builder()
                            .taskId(taskConfig.getTaskId())
                            .eventType(WorkerTaskEvent.EventType.PERIOD_RESULT)
                            .build();
                    event.putDetail(SUB_LATENCY_STATS_RESULT, Json.encode(periodSubLatencyStats));
                    event.putDetail(PUB_LATENCY_STATS_RESULT, Json.encode(periodPubLatencyStats));
                    vertx.eventBus().send(workerEventAddr, event, ShareDataManager.getLocalDeliveryOptions());
                });
            });
        }
    }

    private void startReadyClients(Map<String, ClientTask> clients, Predicate<String> filter) {
        for (Iterator<Map.Entry<String, ClientTask>> iterator = clients.entrySet().iterator();
             iterator.hasNext(); ) {
            Map.Entry<String, ClientTask> entry = iterator.next();
            if (canceled()) {
                break;
            }
            if (filter.test(entry.getKey())) {
                entry.getValue().startTask(null);
            } else {
                iterator.remove();
                entry.getValue().close();
            }
        }
    }

    /**
     * Stage 3, collect task results after stress duration and stage timeout.
     */
    private void collectTaskResults() {
        taskStage.compareAndSet(TaskStage.ONGOING, TaskStage.COLLECTING);

        vertx.cancelTimer(periodCollectTimer);
        StatsPubResult totalPubResult = pubStatsManager.getTotalResult();
        totalPubResult.setActualPubClientCount(pubClients.size());
        totalPubResult.setActualPubMsgCount(totalPubResult.getActualResult().getCount());
        totalPubResult.setActualPubQps(totalPubResult.getActualResult().getQps());
        totalPubResult.setExpectPubClientCount(expectPubCount);
        totalPubResult.setExpectPubQps((1000.0 / taskConfig.getPubIntervalInMs())
                * totalPubResult.getActualPubClientCount());
        // TODO should concern actual success pub client count
        StatsSubResult totalSubResult = subStatsManager.getTotalResult();
        totalSubResult.setActualSubClientCount(subClients.size());
        totalSubResult.setActualSubMsgCount(totalSubResult.getActualResult().getCount());
        totalSubResult.setActualSubQps(totalSubResult.getActualResult().getQps());
        totalSubResult.setExpectSubClientCount(expectSubCount);
        totalSubResult.setExpectSubMsgCount(totalPubResult.getActualPubMsgCount() * taskConfig.getFanOut());
        totalSubResult.setExpectSubQps(totalPubResult.getActualPubQps() * taskConfig.getFanOut());
        totalSubResult.setSubMsgLoss(totalSubResult.getExpectSubMsgCount() - totalSubResult.getActualSubMsgCount());
        log.info("Task {} total pub latency stats: {}",
                taskConfig.getTaskId(), Json.encodePrettily(totalPubResult));
        log.info("Task {} total sub latency stats: {}",
                taskConfig.getTaskId(), Json.encodePrettily(totalSubResult));
        WorkerTaskEvent event = WorkerTaskEvent.builder()
                .taskId(taskConfig.getTaskId())
                .eventType(WorkerTaskEvent.EventType.TOTAL_PUB_SUB_RESULT)
                .build();
        eventReport(totalPubResult, totalSubResult);
        event.putDetail(SUB_LATENCY_STATS_RESULT, Json.encode(totalSubResult));
        event.putDetail(PUB_LATENCY_STATS_RESULT, Json.encode(totalPubResult));
        vertx.eventBus().send(workerEventAddr, event, ShareDataManager.getLocalDeliveryOptions());
    }

    private void handleClientTaskEvent(Message<ClientTaskEvent> event) {
        ClientTaskEvent taskEvent = event.body();
        String clientId = taskEvent.getClientId();
        // Only do this during START stage.
        TaskStage stage = taskStage.get();
        if (Objects.requireNonNull(taskEvent.getEventType()) == ClientTaskEvent.EventType.CONNECT_RESULT) {
            Boolean result = (Boolean) taskEvent.getDetails().get(SUCCESS);
            switch (taskEvent.getClientTaskType()) {
                case PUB:
                    if (result) {
                        readyPubClients.add(clientId);
                    }
                    break;
                case SUB:
                    if (result) {
                        readySubClients.add(clientId);
                    }
                    break;
                default:
            }
            // All clients ready.
            if ((readyPubClients.size() == expectPubCount)
                    && (readySubClients.size() == expectSubCount)) {
                log.info("All pub&sub clients ready, taskId={}, readyPubClients:{}, expectPubCount:{}, "
                                + "readySubClients:{}, expectSubCount:{}",
                        taskConfig.getTaskId(), readyPubClients.size(), expectPubCount,
                        readySubClients.size(), expectSubCount);
                vertx.cancelTimer(stageTimer);
                long delay = Math.max(taskConfig.getDelayAfterReadyInSec(), 1);
                startClientTimer = vertx.setTimer(delay * 1000, event1 -> startClientTask());
            }

        }
    }

    private String buildClientTopic(int topicIndex, boolean isSub, boolean isWildcard) {
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
