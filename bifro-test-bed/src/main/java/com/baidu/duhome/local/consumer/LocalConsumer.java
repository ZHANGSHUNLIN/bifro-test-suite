package com.baidu.duhome.local.consumer;

import com.baidu.duhome.cluster.ClusterDataManager;
import com.baidu.duhome.database.repository.NodeTaskRepository;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.ShareDataAddr;
import com.baidu.iot.test.suite.ShareDataManager;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.worker.TaskWorker;
import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
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
    private ShareDataManager shareDataManager;

    public void currentNodeDelTaskConsumer(Map<String, TaskWorker> runningTaskMap) {
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
