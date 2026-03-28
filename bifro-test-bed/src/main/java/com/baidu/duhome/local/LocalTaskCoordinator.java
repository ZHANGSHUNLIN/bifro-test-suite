package com.baidu.duhome.local;

import com.baidu.duhome.cluster.ClusterDataManager;
import com.baidu.duhome.database.pojo.NodeTask;
import com.baidu.duhome.database.pojo.Report;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.repository.NodeTaskRepository;
import com.baidu.duhome.database.repository.ReportRepository;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import com.baidu.duhome.local.consumer.LocalConsumer;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.ShareDataAddr;
import com.baidu.iot.test.suite.TaskSchedule;
import com.baidu.iot.test.suite.ShareDataManager;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.client.MQTTClientWrapper;
import com.baidu.iot.test.suite.stats.pojo.StatsBasicResult;
import com.baidu.iot.test.suite.utils.TaskUtils;
import com.baidu.iot.test.suite.worker.TaskConfig;
import com.baidu.iot.test.suite.worker.TaskConnWorker;
import com.baidu.iot.test.suite.worker.TaskPubSubWorker;
import com.baidu.iot.test.suite.worker.TaskWorker;
import com.baidu.iot.test.suite.worker.models.WorkerTaskEvent;
import com.baidu.iot.test.suite.worker.pojo.EventReport;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Maps;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.Subject;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.Json;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.baidu.iot.test.suite.constants.CommonConstants.PUB_LATENCY_STATS_RESULT;
import static com.baidu.iot.test.suite.constants.CommonConstants.SUB_LATENCY_STATS_RESULT;


/**
 * 本地任务
 */
@Slf4j
@Component
public class LocalTaskCoordinator {

    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();

    private final Map<String, TaskWorker> runningTaskMap = Maps.newConcurrentMap();

    @Resource
    private ReportRepository reportRepository;

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Resource
    private Vertx vertx;

    @Resource
    private ClusterDataManager clusterDataManager;

    @Resource
    private ShareDataManager shareDataManager;

    @Resource
    private LocalConsumer localConsumer;

    @Resource
    private NodeTaskRepository nodeTaskRepository;

    @Value("${bifro.nodeName}")
    private String nodeName;

    /**
     * 通过制定的任务参数执行任务,
     * 当前的任务发布均为本地任务，不参与集群间传递哦
     */
    public void startTask(String id) {
        if (runningTasks.contains(id)) {
            log.warn("Task {} is running", id);
            return;
        }

        runningTasks.add(id);
        AtomicReference<MessageConsumer<WorkerTaskEvent>> internalConsumerRef =
                new AtomicReference<>();
        String currentNodeId = clusterDataManager.getCurrentNodeIdCache();
        TaskInfoMetadata taskInfoMetadata = taskInfoMetadataRepository.findById(id)
                .block();
        if (taskInfoMetadata == null) {
            throw new RuntimeException("Task not found");
        }
        TaskConfig mainTask = taskInfoMetadata.getTaskConfig();
        String taskId = mainTask.getTaskId();
        NodeTask nodeTask = nodeTaskRepository.findByTaskIdAndNodeId(taskId, currentNodeId).block();
        if (nodeTask == null) {
            log.debug("Task {} has no task", taskId);
            return;
        }
        TaskConfig taskConfig = nodeTask.getTaskConfig();
        log.info("Start to reg taskConfig: {}", taskConfig);
        String workerTaskAddr = TaskUtils.getWorkerTaskAddr(taskConfig.getTaskId());
        Queue<StatsBasicResult[]> periodResults = EvictingQueue.create(17280);
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        switch (taskConfig.getTaskType()) {
            case CONN:
                TaskConnWorker connWorker = new TaskConnWorker(vertx, taskConfig);

                for (String lifecycleAction : taskConfig.getLifecycleActions()) {
                    if (Objects.equals("postConnPub", lifecycleAction)) {
                        Disposable subscribe = postConnPub(connWorker.clientPostConnectObservable(), taskConfig.getLifecycleActionsConfig());
                        compositeDisposable.add(subscribe);
                    }
                }

                Subject<EventReport> eventReportSubject = connWorker.reportEventSubject();
                Disposable subscribe = eventReportSubject.subscribe(event -> {
                    log.trace("Conn event : {}", event);
                    Report report = new Report();
                    BeanUtils.copyProperties(event, report);
                    report.setTaskId(taskConfig.getTaskId());
                    report.setNodeId(currentNodeId);
                    report.setCreateTime(LocalDateTime.now());
                    reportRepository.insert(report).subscribe();
                });
                compositeDisposable.add(subscribe);


                connWorker.startTask();
                runningTaskMap.put(id, connWorker);
                clusterDataManager.upgradeClusterNodeTaskStage(runningTask());
                AtomicReference<MessageConsumer<WorkerTaskEvent>> atomicReference = new AtomicReference<>();
                MessageConsumer<WorkerTaskEvent> localed = connFinConsumer(id, taskConfig, connWorker, atomicReference, compositeDisposable);
                atomicReference.set(localed);

                break;
            case PUBSUB:
                TaskPubSubWorker pubSubWorker = new TaskPubSubWorker(vertx, taskConfig);
                Subject<EventReport> pubSub = pubSubWorker.reportEventSubject();
                Disposable pubSubDis = pubSub.subscribe(event -> {
                    log.trace("PubSub event : {}", event);
                    Report report = new Report();
                    BeanUtils.copyProperties(event, report);
                    report.setTaskId(taskConfig.getTaskId());
                    report.setNodeId(currentNodeId);
                    report.setTaskType(taskConfig.getTaskType());
                    report.setCreateTime(LocalDateTime.now());
                    reportRepository.insert(report).subscribe();
                });
                compositeDisposable.add(pubSubDis);

                pubSubWorker.startTask();
                runningTaskMap.put(id, pubSubWorker);
                clusterDataManager.upgradeClusterNodeTaskStage(runningTask());
                MessageConsumer<WorkerTaskEvent> pubsubConsumer = pubSubFinConsumer(id, taskConfig, workerTaskAddr, periodResults, pubSubWorker, internalConsumerRef);
                internalConsumerRef.set(pubsubConsumer);
                break;
            default:
                break;
        }

        mainTask.setTaskWorkStage(TaskStage.ONGOING);
        taskInfoMetadataRepository.updateTaskConfigById(id, mainTask).block();

        List<NodeTask> nodeTasks = nodeTaskRepository.findAllByTaskId(id).collectList().block();
        for (NodeTask task : nodeTasks) {
            task.getTaskConfig().setTaskWorkStage(TaskStage.ONGOING);
            nodeTaskRepository.save(task).block();
        }
        log.info("Task started: {}", id);

    }


    private MessageConsumer<WorkerTaskEvent> connFinConsumer(String id, TaskConfig taskConfig, TaskConnWorker connWorker, AtomicReference<MessageConsumer<WorkerTaskEvent>> atomicReference, CompositeDisposable compositeDisposable) {
        return this.vertx.eventBus()
                .localConsumer(TaskUtils.getWorkerTaskAddr(taskConfig.getTaskId()), event -> {
                    if (Objects.requireNonNull(event.body().getEventType()) == WorkerTaskEvent.EventType.TOTAL_CONN_RESULT) {
                        vertx.setTimer(TimeUnit.SECONDS.toMillis(taskConfig.getStressDurationInSec()),
                                t -> {
                                    try {
                                        log.info("Call Stop: conn task start time: {}", taskConfig.getStressDurationInSec());
                                        connWorker.stopTask()
                                                .thenAccept(r -> {
                                                    TaskInfoMetadata metadata = taskInfoMetadataRepository.findById(id).block();
                                                    if (metadata != null) {
                                                        metadata.getTaskConfig().setTaskWorkStage(TaskStage.SHUTDOWN);
                                                        taskInfoMetadataRepository.save(metadata).block();
                                                    }
                                                });
                                        runningTaskMap.remove(id);
                                        clusterDataManager.upgradeClusterNodeTaskStage(runningTask());
//                                        clusterDataManager.upgradeSubTaskStage(taskConfig, TaskStage.SHUTDOWN);

                                        List<NodeTask> nodeTasks = nodeTaskRepository.findAllByTaskId(id).collectList().block();
                                        for (NodeTask task : nodeTasks) {
                                            task.getTaskConfig().setTaskWorkStage(TaskStage.SHUTDOWN);
                                            nodeTaskRepository.save(task).block();
                                        }

                                    } catch (Exception e) {
                                        log.error("Failed to stop connect task: {}",
                                                taskConfig.getTaskId(), e);
                                    }
                                    MessageConsumer<WorkerTaskEvent> workerTaskEventMessageConsumer = atomicReference.get();
                                    if (workerTaskEventMessageConsumer != null) {
                                        workerTaskEventMessageConsumer.unregister();
                                    }
                                    if (!compositeDisposable.isDisposed()) {
                                        compositeDisposable.dispose();
                                    }
                                });
                    }
                });
    }

    private MessageConsumer<WorkerTaskEvent> pubSubFinConsumer(String id, TaskConfig taskConfig,
                                                               String workerTaskAddr,
                                                               Queue<StatsBasicResult[]> periodResults,
                                                               TaskPubSubWorker pubSubWorker,
                                                               AtomicReference<MessageConsumer<WorkerTaskEvent>>
                                                                       internalConsumerRef) {
        return vertx.eventBus().consumer(workerTaskAddr,
                event -> {
                    switch (event.body().getEventType()) {
                        case PERIOD_RESULT:
                            StatsBasicResult pubResult = Json.decodeValue(
                                    (String) event.body().getDetails().get(PUB_LATENCY_STATS_RESULT),
                                    StatsBasicResult.class);
                            StatsBasicResult subResult = Json.decodeValue(
                                    (String) event.body().getDetails().get(SUB_LATENCY_STATS_RESULT),
                                    StatsBasicResult.class);
                            if (pubResult.getCount() > 0 || subResult.getCount() > 0) {
                                periodResults.add(new StatsBasicResult[]{pubResult, subResult});
                            }
                            break;
                        case TOTAL_PUB_SUB_RESULT:
                            try {
                                log.info("start to stop pubsub task");
                                pubSubWorker.stopTask()
                                        .thenAccept(r -> {
                                            TaskInfoMetadata metadata = taskInfoMetadataRepository.findById(id).block();
                                            if (metadata != null) {
                                                metadata.getTaskConfig().setTaskWorkStage(TaskStage.SHUTDOWN);
                                                taskInfoMetadataRepository.save(metadata).block();
                                            }
                                        });
                                runningTaskMap.remove(id);
                                clusterDataManager.upgradeClusterNodeTaskStage(runningTask());

                                List<NodeTask> nodeTasks = nodeTaskRepository.findAllByTaskId(id).collectList().block();
                                for (NodeTask task : nodeTasks) {
                                    task.getTaskConfig().setTaskWorkStage(TaskStage.SHUTDOWN);
                                    nodeTaskRepository.save(task).block();
                                }

                            } catch (Exception e) {
                                log.error("Failed to stop pubsub task: {}",
                                        taskConfig.getTaskId(), e);
                            }
                            log.info("start to stop task, {}", id);
                            MessageConsumer<WorkerTaskEvent> consumer = internalConsumerRef.get();
                            if (consumer != null) {
                                consumer.unregister();
                                runningTasks.remove(id);
                            }
                            break;
                        default:
                            break;
                    }
                });
    }


///  注册一个全局的任务调度器

    /**
     * 注册一个全局的任务调度器,用于接收和处理集群任务消息
     */
    @PostConstruct
    public void registerGlobalTaskScheduler() {
        vertx.eventBus().<TaskSchedule>consumer(Constants.CLUSTER_TASK_MESSAGE, message -> {
            TaskSchedule taskSchedule = message.body();

            TaskSchedule.Op op = taskSchedule.getOp();
            String id = taskSchedule.getId();
            String nodeId = clusterDataManager.getCurrentNodeIdCache();

            switch (op) {
                // 任务确认，任务待执行
                case REG:
                    startTask(id);
                    break;
                // 任务取消
                case UN_REG:
                    stopTask(id);
                    break;
                case TASK_FINISH:
                    taskFinish(id, nodeId);
                    break;
                default:
                    log.warn("Unknown operation: {}", op);
                    break;
            }
        });

        // 将本机的基础信息同步到集群中
        clusterDataManager.regClusterNodeInfo(nodeName);

        localConsumer.currentNodeDelTaskConsumer(runningTaskMap);

    }


    /**
     * 每个节点的任务完成后需要提交完成的事件
     *
     * @param taskId 任务id
     * @param nodeId 节点id
     */
    private void taskFinish(String taskId, String nodeId) {

        ShareDataManager.ShareMap<String, Set<String>> map = shareDataManager.map(ShareDataAddr.FINISH_NODE_TASKS);
        map.key(taskId)
                .thenAccept((result) -> {
                    if (result == null) {
                        // 首次完成节点
                        HashSet<String> finishNodeIds = new HashSet<>();
                        finishNodeIds.add(nodeId);
                        map.key(taskId)
                                .putIfAbsent(finishNodeIds)
                                .thenAccept(r -> {
                                    log.info("first task finish, taskId: {}, nodeId: {}", taskId, nodeId);
                                    checkAllTasksComplete(taskId, finishNodeIds);
                                });
                    } else {
                        // 已有节点完成记录
                        result.add(nodeId);

                        map.key(taskId)
                                .replace(result)
                                .thenAccept(r -> {
                                    log.info("task finish, taskId: {}, nodeId: {}, finish set: {}",
                                            taskId, nodeId, result);
                                    checkAllTasksComplete(taskId, result);
                                });
                    }
                })
        ;

    }


    // 提取出来的检查方法
    private void checkAllTasksComplete(String taskId, Set<String> finishNodeIds) {

        List<NodeTask> nodeTasks = nodeTaskRepository.findAllByTaskId(taskId).collectList().block();
        Map<String, TaskConfig> subTasks = nodeTasks.stream()
                .collect(Collectors.toMap(
                        NodeTask::getNodeId,
                        NodeTask::getTaskConfig
                ));

        Set<String> allNodeTasks = subTasks.keySet();
        if (allNodeTasks.size() == finishNodeIds.size()
                && allNodeTasks.containsAll(finishNodeIds)) {
            log.info("all node finish close task");
            // todo 检查需不需要主动关闭任务
        } else {
            log.info("have not finish task, skip close task stage");
        }
    }


    /**
     * 追觅测试，连接后理解发送一个消息
     *
     */
    private Disposable postConnPub(Subject<MQTTClientWrapper> subject, Map<String, Object> lifecycleActionsConfig) {
        return subject.subscribe(client -> {
            log.debug("lifecycleActionsConfig: {}", lifecycleActionsConfig);
            String topic = (String) lifecycleActionsConfig.get("topic");
            Integer qos = (Integer) lifecycleActionsConfig.get("qos");
            Boolean retain = (Boolean) lifecycleActionsConfig.get("retain");
            String payload = (String) lifecycleActionsConfig.get("payload");
            Integer payloadSize = (Integer) lifecycleActionsConfig.get("payloadSize");

            if (StringUtils.isNotBlank(topic)) {
                topic = topic.replace("{clientId}", client.getClientId().substring(5, 12));
            } else {
                topic = "test/" + client.getClientId().substring(5, 12);
            }

            if (qos == null) {
                qos = 0;
            }

            if (retain == null) {
                retain = false;
            }
            byte[] payloadBytes;
            if (StringUtils.isNotBlank(payload)) {
                payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            } else {
                if (payloadSize == null) {
                    payloadBytes = new byte[16];
                } else {
                    payloadBytes = new byte[payloadSize];
                }
            }

            log.debug("clientPostConnectObservable: {}", client.getClientId());
            client.publish(payloadBytes, topic, qos, false, retain)
                    .exceptionally(e -> {
                        log.error("Failed to publish message: {}", client.getClientId(), e);
                        return null;
                    })
                    .thenAccept(r -> log.info("publish result: {}", client.getClientId()));
        });
    }

    public Map<String, TaskStage> runningTask() {
        return runningTaskMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().getTaskState()));
    }


    public void stopTask(String taskId){

            TaskWorker taskWorker = runningTaskMap.get(taskId);
            if (taskWorker != null) {
                log.info("cluster task STOP , {}", taskId);
                taskWorker.stopTask().thenAccept(r -> {
                    log.info("taskId stopped: {}", taskId);
                    TaskInfoMetadata metadata = taskInfoMetadataRepository.findById(taskId).block();
                    if (metadata != null) {
                        TaskConfig taskConfig = metadata.getTaskConfig();
                        taskConfig.setTaskWorkStage(TaskStage.STOPPED);
                        taskInfoMetadataRepository.updateTaskConfigById(taskId, taskConfig).block();
                    }
                    runningTaskMap.remove(taskId);
                    List<NodeTask> nodeTasks = nodeTaskRepository.findAllByTaskId(taskId).collectList().block();
                    for (NodeTask nodeTask : nodeTasks) {
                        nodeTask.getTaskConfig().setTaskWorkStage(TaskStage.STOPPED);
                    }
                    nodeTaskRepository.saveAll(nodeTasks).collectList().block();
                    clusterDataManager.upgradeClusterNodeTaskStage(runningTask());
                });
            } else {
                clusterDataManager.currentNode().thenAccept(nodeInfo -> {
                    NodeTask nodeTask = nodeTaskRepository.findFirstByTaskId(taskId).block();
                    if (nodeTask == null) {
                        log.error("taskId: {}, nodeTask not found", taskId);
                        return;
                    }
                    String dbNodeName = nodeTask.getNodeName();
                    String nodeName = nodeInfo.getNodeName();
                    log.info("taskId: {}, nodeName: {}, dbNodeName: {}", taskId, nodeName, dbNodeName);
                    if (Objects.equals(dbNodeName, nodeName)) {
                        TaskInfoMetadata metadata = taskInfoMetadataRepository.findById(taskId).block();
                        if (metadata != null) {
                            TaskConfig taskConfig = metadata.getTaskConfig();
                            taskConfig.setTaskWorkStage(TaskStage.STOPPED);
                            taskInfoMetadataRepository.updateTaskConfigById(taskId, taskConfig).block();
                            List<NodeTask> nodeTasks = nodeTaskRepository.findAllByTaskId(taskId).collectList().block();
                            for (NodeTask nt : nodeTasks) {
                                nt.getTaskConfig().setTaskWorkStage(TaskStage.STOPPED);
                            }
                            nodeTaskRepository.saveAll(nodeTasks).collectList().block();
                        }
                    }
                });
            }
    }

}