package com.baidu.duhome.local.consumer;

import com.baidu.duhome.cluster.ClusterDataManager;
import com.baidu.duhome.database.pojo.NodeTask;
import com.baidu.duhome.database.repository.NodeTaskRepository;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.ShareDataAddr;
import com.baidu.iot.test.suite.ShareDataManager;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.worker.TaskConfig;
import com.baidu.iot.test.suite.worker.TaskWorker;
import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@Component
public class LocalConsumer {

    @Resource
    private Vertx vertx;

    @Resource
    private ClusterDataManager clusterDataManager;

    @Resource
    private ShareDataManager shareDataManager;

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Resource
    private NodeTaskRepository nodeTaskRepository;


    public Map<String, TaskStage> runningTask(Map<String, TaskWorker> runningTaskMap) {
        return runningTaskMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().getTaskState()));
    }

    public void stopTaskConsumer(Map<String, TaskWorker> runningTaskMap) {
// reg stop task.
        vertx.eventBus().<String>consumer(Constants.STOP_CLUSTER_TASK_ADDR, taskIdEvent -> {

            String taskId = taskIdEvent.body();
            TaskWorker taskWorker = runningTaskMap.get(taskId);
            if (taskWorker != null) {
                log.info("cluster task STOP , {}", taskId);
                taskWorker.stopTask().thenAccept(r -> {
                    log.info("taskId stopped: {}", taskId);
                    taskInfoMetadataRepository.findById(taskId).ifPresent(t -> {
                        TaskConfig taskConfig = t.getTaskConfig();
                        taskConfig.setTaskWorkStage(TaskStage.STOPPED);
                        taskInfoMetadataRepository.updateTaskConfigById(taskId, taskConfig);
                    });
                    runningTaskMap.remove(taskId);
                    List<NodeTask> nodeTasks = nodeTaskRepository.searchAllByTaskId(taskId);
                    for (NodeTask nodeTask : nodeTasks) {
                        nodeTask.getTaskConfig().setTaskWorkStage(TaskStage.STOPPED);
                    }
                    nodeTaskRepository.saveAll(nodeTasks);
                    clusterDataManager.upgradeClusterNodeTaskStage(runningTask(runningTaskMap));
                });
            } else {
                clusterDataManager.currentNode().thenAccept(nodeInfo -> {
                    Optional<NodeTask> nodeTaskOptional = nodeTaskRepository.searchFirstByTaskId(taskId);
                    if (nodeTaskOptional.isEmpty()) {
                        log.error("taskId: {}, nodeTask not found", taskId);
                        return;
                    }
                    NodeTask nodeTask = nodeTaskOptional.get();
                    String dbNodeName = nodeTask.getNodeName();
                    String nodeName = nodeInfo.getNodeName();
                    log.info("taskId: {}, nodeName: {}, dbNodeName: {}", taskId, nodeName, dbNodeName);
                    if (Objects.equals(dbNodeName, nodeName)) {
                        taskInfoMetadataRepository.findById(taskId).ifPresent(t -> {
                            TaskConfig taskConfig = t.getTaskConfig();
                            taskConfig.setTaskWorkStage(TaskStage.STOPPED);
                            taskInfoMetadataRepository.updateTaskConfigById(taskId, taskConfig);
                            List<NodeTask> nodeTasks = nodeTaskRepository.searchAllByTaskId(taskId);
                            for (NodeTask nt : nodeTasks) {
                                nt.getTaskConfig().setTaskWorkStage(TaskStage.STOPPED);
                            }
                            nodeTaskRepository.saveAll(nodeTasks);
                        });
                    }
                });
            }
        });
    }

    public void delTaskConsumer(Map<String, TaskWorker> runningTaskMap) {
// del
        vertx.eventBus().<String>consumer(Constants.DEL_CLUSTER_TASK_ADDR, taskIdEvent -> {
            String taskId = taskIdEvent.body();
            TaskWorker taskWorker = runningTaskMap.get(taskId);
            if (taskWorker != null) {
                log.warn("taskId found, ignore to del: {}", taskId);
            } else {
                log.info("taskId not found: {}", taskId);
            }
            delTask(taskId);
        });
    }

    private void delTask(String taskId) {
        log.info("删除任务: {}", taskId);
        shareDataManager.map(ShareDataAddr.CLUSTER_TASK_CONFIGS)
                .key(taskId)
                .remove()
                .thenAccept(r -> shareDataManager.map(ShareDataAddr.NODE_TASK_CONFIGS)
                        .key(taskId).remove());
    }

}
