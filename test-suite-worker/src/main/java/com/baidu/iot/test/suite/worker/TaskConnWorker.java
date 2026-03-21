package com.baidu.iot.test.suite.worker;

import static com.baidu.iot.test.suite.constants.CommonConstants.CONN_LATENCY_STATS_RESULT;

import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.ShareDataManager;
import com.baidu.iot.test.suite.TaskSchedule;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.client.MQTTClientWrapper;
import com.google.common.util.concurrent.RateLimiter;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.Json;

import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.baidu.iot.test.suite.ConnClientTask;
import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.constants.ClientTaskType;
import com.baidu.iot.test.suite.models.ClientTaskEvent;
import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;
import com.baidu.iot.test.suite.stats.pojo.StatsConnResult;
import com.baidu.iot.test.suite.stats.TaskConnStatsManager;
import com.baidu.iot.test.suite.utils.TaskUtils;
import com.baidu.iot.test.suite.worker.models.WorkerTaskEvent;
import com.baidu.iot.test.suite.worker.utils.ConfigHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public class TaskConnWorker extends BaseTaskWorker {

    private final Logger tagLogger = LoggerFactory.getLogger("tagLogger");
    private final TaskConnStatsManager connStatsManager;
    private final Map<String, ConnClientTask> connClients = new HashMap<>();
    private final Set<String> connClientIds = new HashSet<>();
    private long periodCollectTimer;
    private final String workerEventAddr;
    private final AtomicInteger subscribeCount;
    private MessageConsumer<ClientTaskEvent> taskEventConsumer;
    private MessageConsumer<WorkerTaskEvent> taskFinishConsumer;
    private final Subject<MQTTClientWrapper> onTaskInitObservable = PublishSubject.<MQTTClientWrapper>create()
            .toSerialized();

    public TaskConnWorker(Vertx vertx, TaskConfig taskConfig) {
        super(vertx, taskConfig);
        this.subscribeCount = new AtomicInteger(taskConfig.getThingIdStartAt());
        this.connStatsManager = new TaskConnStatsManager(taskConfig.getTaskId(), taskConfig.getSkipStatsPeriod());
        this.workerEventAddr = TaskUtils.getWorkerTaskAddr(taskConfig.getTaskId());
    }

    public void startTask() {
        taskStage.set(TaskStage.START);
        eventReport();
        this.taskEventConsumer = this.vertx.eventBus()
                .localConsumer(TaskUtils.getClientTaskAddr(taskConfig.getTaskId()), this::handleClientTaskEvent);
        this.taskFinishConsumer = this.vertx.eventBus()
                .localConsumer(TaskUtils.getWorkerTaskAddr(taskConfig.getTaskId()), this::handleTaskFinishConsumer);
        initConnClients();
        startClientTask();
    }

    public CompletableFuture<Void> stopTask() {
        log.info("stop task, taskId: {}", taskConfig.getTaskId());
        if (Objects.equals(taskStage.get(), TaskStage.SHUTDOWN_ING)) {
            log.info("shutting down task");
            return CompletableFuture.completedFuture(null);
        }
        taskStage.set(TaskStage.SHUTDOWN_ING);
        eventReport();
        CompletableFuture<Void> result = new CompletableFuture<>();
        ForkJoinPool.commonPool().execute(() -> {
            RateLimiter rateLimiter = taskConfig.getDisConnectRateLimiter();
            for (Map.Entry<String, ConnClientTask> clientWrapperEntry : connClients.entrySet()) {
                rateLimiter.acquire();
                clientWrapperEntry.getValue().close();
            }
            WorkerTaskEvent taskEvent = WorkerTaskEvent.builder()
                    .taskId(taskConfig.getTaskId())
                    .eventType(WorkerTaskEvent.EventType.TASK_END)
                    .build();
            vertx.eventBus().send(workerEventAddr, taskEvent, ShareDataManager.getLocalDeliveryOptions());
            result.complete(null);
            vertx.eventBus().publish(Constants.CLUSTER_TASK_MESSAGE, TaskSchedule.builder()
                    .op(TaskSchedule.Op.TASK_FINISH)
                    .id(taskConfig.getTaskId().substring(0, 8))
                    .build(), ShareDataManager.getLocalDeliveryOptions());
        });
        connStatsManager.reset();
        if (taskEventConsumer != null) {
            taskEventConsumer.unregister();
        }

        vertx.cancelTimer(periodCollectTimer);

        if (taskFinishConsumer != null) {
            taskFinishConsumer.unregister();
        }
        taskStage.compareAndSet(TaskStage.SHUTDOWN_ING, TaskStage.SHUTDOWN);
        eventReport();
        return result;
    }

    /**
     * Stage 1, init mqtt clients and connect.
     */
    private void initConnClients() {
        int clientIndex = taskConfig.getThingIdStartAt();
        for (int i = 0; i < taskConfig.getTotalClientCount(); i++) {
            if (canceled()) {
                return;
            }
            MqttClientConfig mqttClientConfig = taskConfig.getMqttClientConfig(clientIndex++, subscribeCount);
            ClientTaskConfig clientTaskConfig = new ClientTaskConfig();
            clientTaskConfig.setType(ClientTaskType.CONN);
            ConfigHelper.fillCommonTaskConfig(clientTaskConfig, taskConfig);
            ConnClientTask connClientTask =
                    new ConnClientTask(vertx, clientTaskConfig, mqttClientConfig, connStatsManager,taskStage);
            connClients.put(mqttClientConfig.getClientId(), connClientTask);
        }
        connClientIds.addAll(connClients.keySet());
    }

    /**
     * Stage 2, start pub&sub in clients.
     */
    private void startClientTask() {
        taskStage.compareAndSet(TaskStage.START, TaskStage.ONGOING);
        eventReport();
        periodCollectTimer = vertx.setPeriodic(5000, t -> {
            StatsBasicResult periodConnLatencyStats = connStatsManager.tagPeriodResult();
            eventReport(periodConnLatencyStats);
            tagLogger.info("Task {} period connect latency stats: {}",
                    taskConfig.getTaskId(), Json.encodePrettily(periodConnLatencyStats));
            WorkerTaskEvent event = WorkerTaskEvent.builder()
                    .taskId(taskConfig.getTaskId())
                    .eventType(WorkerTaskEvent.EventType.PERIOD_RESULT)
                    .build();
            event.putDetail(CONN_LATENCY_STATS_RESULT, Json.encode(periodConnLatencyStats));
            vertx.eventBus().send(workerEventAddr, event, ShareDataManager.getLocalDeliveryOptions());
        });
        vertx.executeBlocking(promise -> {
            RateLimiter rateLimiter = taskConfig.getConnectRateLimiter();
            log.info("Start to connect: {}", connClients.size());
            for (ConnClientTask clientWrapper : connClients.values()) {
                if (canceled()) {
                    break;
                }
                rateLimiter.acquire();
                log.debug("Start to connect to {}", clientWrapper.getCId());
                clientWrapper.startTask(onTaskInitObservable::onNext);
            }
            promise.complete();
        });
    }

    /**
     * Stage 3, collect task results after stress duration and stage timeout.
     */
    private void collectTaskResults() {
        taskStage.compareAndSet(TaskStage.ONGOING, TaskStage.COLLECTING);
        vertx.cancelTimer(periodCollectTimer);
        StatsConnResult totalResult = connStatsManager.getTotalResult();
        totalResult.setExpectConnCount(taskConfig.getTotalClientCount());
        totalResult.setExpectConnQps(taskConfig.getConnectRate());
        totalResult.setActualConnCount(totalResult.getActualResult().getCount());
        totalResult.setActualConnQps(totalResult.getActualResult().getQps());
        eventReport(totalResult);
        log.debug("Task {} total connect latency stats: {}",
                taskConfig.getTaskId(), Json.encodePrettily(totalResult));

        WorkerTaskEvent event = WorkerTaskEvent.builder()
                .taskId(taskConfig.getTaskId())
                .eventType(WorkerTaskEvent.EventType.TOTAL_CONN_RESULT)
                .build();
        event.putDetail(CONN_LATENCY_STATS_RESULT, Json.encode(totalResult));
        vertx.eventBus().send(workerEventAddr, event, ShareDataManager.getLocalDeliveryOptions());
    }

    private void handleClientTaskEvent(Message<ClientTaskEvent> event) {
        ClientTaskEvent taskEvent = event.body();
        String clientId = taskEvent.getClientId();
        if (Objects.requireNonNull(taskEvent.getEventType()) == ClientTaskEvent.EventType.CONNECT_RESULT) {
            connClientIds.remove(clientId);
//            log.info("Remove client: {}, left clients: {}", clientId, connClientIds.size());
            if (connClientIds.isEmpty()) {
                log.debug("Task collectTaskResults");
                collectTaskResults();
            } else {
                log.trace("Task collectTaskResults, still have clients to connect: {}", connClientIds.size());
            }
        }
    }

    private void handleTaskFinishConsumer(Message<WorkerTaskEvent> event) {
        if (Objects.requireNonNull(event.body().getEventType()) == WorkerTaskEvent.EventType.TOTAL_CONN_RESULT) {
            vertx.setTimer(TimeUnit.SECONDS.toMillis(taskConfig.getStressDurationInSec()),
                    t -> {
                        try {
                            log.info("Call Stop: conn task start time: {}", taskConfig.getStressDurationInSec());
//                                this.stopTask().get();
                        } catch (Exception e) {
                            log.error("Failed to stop connect task: {}",
                                    taskConfig.getTaskId(), e);
                        }
                    });
        }
    }

    public Subject<MQTTClientWrapper> clientPostConnectObservable() {
        return this.onTaskInitObservable;
    }

}
