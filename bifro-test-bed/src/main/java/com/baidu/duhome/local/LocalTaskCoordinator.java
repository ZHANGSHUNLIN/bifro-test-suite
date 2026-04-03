package com.baidu.duhome.local;

import com.baidu.duhome.cluster.ClusterDataManager;
import com.baidu.duhome.database.pojo.NodeTask;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.repository.NodeTaskRepository;
import com.baidu.duhome.database.repository.ReportRepository;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.HazelcastDataManager;
import com.baidu.iot.test.suite.TaskSchedule;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.worker.TaskConfig;
import com.baidu.iot.test.suite.worker.TaskWorker;
import com.baidu.iot.test.suite.worker.TaskWorkerFactory;
import com.google.common.collect.Maps;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


/**
 * 本地任务
 */
@Slf4j
@Component
public class LocalTaskCoordinator {

    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();

    @Getter
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
    private HazelcastDataManager hazelcastDataManager;


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
        switch (taskConfig.getTaskType()) {
            case CONN, PUBSUB:
                TaskWorker connWorker = TaskWorkerFactory.create(vertx, taskConfig);
                connWorker.startTask();
                runningTaskMap.put(id, connWorker);
                clusterDataManager.upgradeClusterNodeTaskStage(runningTask());

                break;
            default:
                break;
        }

        mainTask.setTaskWorkStage(TaskStage.ONGOING);
        taskInfoMetadataRepository.updateTaskConfigById(id, mainTask).block();

        List<NodeTask> nodeTasks = nodeTaskRepository.findAllByTaskId(id).collectList().block();
        if (nodeTasks != null) {
            for (NodeTask task : nodeTasks) {
                task.getTaskConfig().setTaskWorkStage(TaskStage.ONGOING);
                nodeTaskRepository.save(task).subscribe();
            }
            log.info("Task started: {}", id);
        } else {
            log.warn("Task not found: {}", id);
        }

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
        clusterDataManager.regClusterNodeInfoDirect(nodeName);

    }


    /**
     * 每个节点的任务完成后需要提交完成的事件
     *
     * @param taskId 任务id
     * @param nodeId 节点id
     */
    private void taskFinish(String taskId, String nodeId) {

        HazelcastDataManager.IMapWrapper<String, Set<String>> map =
                hazelcastDataManager.map(HazelcastDataManager.ShareDataAddr.FINISH_NODE_TASKS);
        map.key(taskId)
                .thenAccept((result) -> {
                    if (result == null) {
                        // 首次完成节点
                        HashSet<String> finishNodeIds = new HashSet<>();
                        finishNodeIds.add(nodeId);
                        map.key(taskId)
                                .putIfAbsent(finishNodeIds)
                                .thenAccept(r -> {
                                    nodeTaskRepository.findAllByTaskId(taskId).subscribe(
                                            nodeTask -> {
                                                nodeTask.getTaskConfig().setTaskWorkStage(TaskStage.SHUTDOWN);
                                                nodeTaskRepository.save(nodeTask).subscribe();
                                            }
                                    );
                                    log.info("first task finish, taskId: {}, nodeId: {}", taskId, nodeId);
                                    runningTaskMap.remove(taskId);
                                    checkAllTasksComplete(map,taskId, finishNodeIds);
                                });
                    } else {
                        // 已有节点完成记录
                        result.add(nodeId);
                        runningTaskMap.remove(taskId);
                        map.key(taskId)
                                .replace(result)
                                .thenAccept(r -> {
                                    log.info("task finish, taskId: {}, nodeId: {}, finish set: {}",
                                            taskId, nodeId, result);
                                    checkAllTasksComplete(map, taskId, result);
                                });
                    }
                })
        ;

    }


    // 提取出来的检查方法
    private void checkAllTasksComplete(HazelcastDataManager.IMapWrapper<String, Set<String>> map, String taskId, Set<String> finishNodeIds) {

        List<NodeTask> nodeTasks = nodeTaskRepository.findAllByTaskId(taskId).collectList().block();
        assert nodeTasks != null;
        Map<String, TaskConfig> subTasks = nodeTasks.stream()
                .collect(Collectors.toMap(NodeTask::getNodeId, NodeTask::getTaskConfig));

        Set<String> allNodeTasks = subTasks.keySet();
        if (allNodeTasks.size() == finishNodeIds.size()
                && allNodeTasks.containsAll(finishNodeIds)) {
            log.info("all node finish close task");
            map.key(taskId).remove();
            taskInfoMetadataRepository.findById(taskId).subscribe(val -> {
                TaskConfig taskConfig = val.getTaskConfig();
                taskConfig.setTaskWorkStage(TaskStage.SHUTDOWN);
                taskInfoMetadataRepository.updateTaskConfigById(taskId, taskConfig).subscribe();
            }, err -> log.error("Failed to stop task: {}", taskId, err));

        } else {
            log.info("have not finish task, skip close task stage");
        }
    }


    public Map<String, TaskStage> runningTask() {
        return runningTaskMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().getTaskState()));
    }


    public void stopTask(String taskId) {

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