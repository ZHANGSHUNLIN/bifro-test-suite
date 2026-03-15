package com.baidu.duhome.cluster;


import com.baidu.duhome.bean.ApiResponse;
import com.baidu.duhome.bean.TaskDetailResponse;
import com.baidu.duhome.bean.dto.NodeTaskAllocationRequest;
import com.baidu.duhome.bean.vo.NodeTaskAllocationVO;
import com.baidu.duhome.database.pojo.NodeTask;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.duhome.bean.dto.TaskRequest;
import com.baidu.duhome.bean.TaskStatistics;
import com.baidu.duhome.database.repository.NodeTaskRepository;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import com.baidu.duhome.database.service.TaskInfoMetadataService;
import com.baidu.duhome.exception.ApiException;
import com.baidu.iot.test.suite.worker.TaskConfig;
import com.baidu.iot.test.suite.worker.TaskStage;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 所有的任务均爆粗你在vertx的分布式数据结构中存储和通信。暂无引入持久化机制。
 * 任务分为两类，一类是集群任务，另一类是本地任务。
 * 集群任务为本次的测试的最终目标，本地任务为按照分配策略将集群任务分配给具体节点的子任务分片。
 */
@Component
@Slf4j
public class TaskManager {

    @Resource
    private Vertx vertx;

    @Resource
    private ClusterDataManager clusterDataManager;

    @Resource
    private TaskInfoMetadataService taskInfoMetadataService;

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Resource
    private NodeTaskRepository nodeTaskRepository;

    public TaskInfoMetadata addTask(TaskRequest taskRequest) {
        if (taskRequest == null) {
            throw new ApiException("Task request cannot be null");
        }

        List<MqttBroker> brokers = taskRequest.getBrokers().stream().map(r -> MqttBroker.builder()
                .brokerId(r.getBrokerId())
                .host(r.getHost())
                .port(r.getPort())
                .build()).toList();

        TaskConfig mainTaskConfig = taskRequest.toTaskConfig();
        String nextTaskId = RandomStringUtils.secure().next(8, true, true);
        mainTaskConfig.setTaskId(nextTaskId);
        TaskInfoMetadata taskInfoMetadata = TaskInfoMetadata.builder()
                .taskName(taskRequest.getTaskName())
                .taskConfig(mainTaskConfig)
                .createTime(LocalDateTime.now())
                .brokers(brokers)
                .build();
        log.info("add task taskInfoMetadata: {}", taskInfoMetadata);
        return taskInfoMetadataService.insertTaskInfoMetadata(taskInfoMetadata);
        // todo 不要删除，需要根据配置切换，使用内存和mongo可选。
//        return clusterDataManager.addTask(taskInfoMetadata);
    }


    public ApiResponse<TaskConfig> assignTask(String id, NodeTaskAllocationRequest nodeTaskAllocationRequest) {

        Optional<TaskInfoMetadata> metadata = taskInfoMetadataRepository.findById(id);
        if (metadata.isPresent()) {
            TaskInfoMetadata taskInfoMetadata = metadata.get();
            TaskConfig taskConfig = taskInfoMetadata.getTaskConfig();
            clusterDataManager.assignCheck(taskConfig,nodeTaskAllocationRequest);
            taskConfig.setTaskWorkStage(TaskStage.ASSIGNED);
            taskInfoMetadataRepository.updateTaskConfigById(id, taskConfig);
            return ApiResponse.success(taskConfig);
        } else {
            return ApiResponse.error("任务不存在");
        }

//        return clusterDataManager.assignTask(taskId).thenApply(r -> {
//            clusterDataManager.upgradeMainTaskStage(taskId, TaskStage.ASSIGNED);
//            return r;
//        });
    }

    public ApiResponse<NodeTaskAllocationVO> calculateNodeTaskAllocation(String id) {
        Optional<TaskInfoMetadata> metadata = taskInfoMetadataRepository.findById(id);
        if (metadata.isPresent()) {
            TaskInfoMetadata taskInfoMetadata = metadata.get();
            TaskConfig taskConfig = taskInfoMetadata.getTaskConfig();
            return ApiResponse.success(clusterDataManager.calcuTasksToNodes(taskConfig));
        } else {
            return ApiResponse.error("任务不存在");
        }
    }


    public TaskInfoMetadata modifyTask(String taskId, TaskRequest taskRequest) {
        if (taskRequest == null) {
            throw new ApiException("Task request cannot be null");
        }

        if (taskId == null) {
            throw new ApiException("Task id cannot be null");
        }

        List<MqttBroker> brokers = taskRequest.getBrokers().stream().map(r -> MqttBroker.builder()
                .brokerId(r.getBrokerId())
                .host(r.getHost())
                .port(r.getPort())
                .build()).toList();

        TaskConfig mainTaskConfig = taskRequest.toTaskConfig();
        mainTaskConfig.setTaskId(taskId);
        TaskInfoMetadata taskInfoMetadata = TaskInfoMetadata.builder()
                .taskConfig(mainTaskConfig)
                .brokers(brokers)
                .build();
        log.info("modify task: {}", taskInfoMetadata);

        return taskInfoMetadataRepository.save(taskInfoMetadata);


//        return clusterDataManager.replaceTask(taskId, taskInfoMetadata);
    }


    public ApiResponse<TaskDetailResponse> delTask(String taskId) {
        Optional<TaskInfoMetadata> taskInfoMetadata = taskInfoMetadataRepository.findById(taskId);
        if (taskInfoMetadata.isPresent()) {
            TaskConfig taskConfig = taskInfoMetadata.map(TaskInfoMetadata::getTaskConfig).orElseThrow();
            TaskStage taskWorkStage = taskConfig.getTaskWorkStage();
            if (!Objects.equals(taskWorkStage, TaskStage.INIT) && !Objects.equals(taskWorkStage, TaskStage.SHUTDOWN)
                    && !Objects.equals(taskWorkStage, TaskStage.ASSIGNED)) {
                return ApiResponse.error("任务已开始无法删除");
            }
            taskInfoMetadataRepository.deleteById(taskId);
            nodeTaskRepository.deleteByTaskId(taskId);
            return ApiResponse.success();
        } else {
            return ApiResponse.error("任务不存在");
        }
//        return clusterDataManager.getMainTask(taskId)
//                .thenApply(r -> {
//                    if (r.isEmpty()) {
//                        return ResponseEntity.ok(TaskDetailResponse.error("未找到该任务"));
//                    }
//
//                    TaskConfig taskConfig = r.map(TaskInfoMetadata::getTaskConfig).orElseThrow();
//                    TaskStage taskWorkStage = taskConfig.getTaskWorkStage();
//                    if (!Objects.equals(taskWorkStage, TaskStage.INIT) && !Objects.equals(taskWorkStage, TaskStage.SHUTDOWN)) {
//                        return ResponseEntity.ok(TaskDetailResponse.error("任务已开始无法删除"));
//                    }
//                    vertx.eventBus().publish(Constants.DEL_CLUSTER_TASK_ADDR, taskId);
//                    return ResponseEntity.ok(TaskDetailResponse.error("del task success"));
//                });
    }


    public Page<TaskInfoMetadata> getAllTask(Pageable pageable) {
        return taskInfoMetadataService.findAll(pageable);
        // todo 不要删除，需要根据配置切换，使用内存和mongo可选。
//        return clusterDataManager.getAllTask();
    }

    public ApiResponse<TaskDetailResponse> getTaskDetails(String taskId) {

        Optional<TaskInfoMetadata> taskInfoMetadataOpt = taskInfoMetadataRepository.findById(taskId);
        if (taskInfoMetadataOpt.isPresent()) {
            TaskInfoMetadata taskInfoMetadata = taskInfoMetadataOpt.get();
            TaskConfig mainTask = taskInfoMetadata.getTaskConfig();

            TaskDetailResponse response = new TaskDetailResponse();
            response.setSuccess(true);
            response.setTaskId(taskId);
            // todo 当前 hard code
            String topic = mainTask.getTopic();
            if (!StringUtil.isNullOrEmpty(topic)) {
                mainTask.setTopic(String.format("%s/%s/%s/{num}", topic, taskInfoMetadata.getId(), "xxxx"));
            }
            response.setMainTask(mainTask);
            response.setBrokers(taskInfoMetadata.getBrokers());
            response.setTaskName(taskInfoMetadata.getTaskName());
            List<NodeTask> nodeTasks = nodeTaskRepository.searchAllByTaskId(mainTask.getTaskId());
            Map<String, TaskConfig> subTasks = nodeTasks.stream()
                    .collect(Collectors.toMap(
                            NodeTask::getNodeId,    // 用来提取 map 的 key
                            NodeTask::getTaskConfig // 用来提取 map 的 value
                    ));

            response.setSubTasks(subTasks);

            // 计算统计信息
            calculateStatistics(response);

            return ApiResponse.success(response);

        } else {
            return ApiResponse.error("任务不存在");
        }

//        return clusterDataManager.getMainTask(taskId)
//                .thenCombine(clusterDataManager.getSubTasks(taskId),
//                        (taskInfoMetadataOpt, subTasks) -> {
//                            if (taskInfoMetadataOpt.isEmpty()) {
//                                return TaskDetailResponse.error("任务不存在: " + taskId);
//                            }
//                            TaskInfoMetadata taskInfoMetadata = taskInfoMetadataOpt.get();
//
//                            TaskConfig mainTask = taskInfoMetadata.getTaskConfig();
//                            // 构建响应
//                            TaskDetailResponse response = new TaskDetailResponse();
//                            response.setSuccess(true);
//                            response.setTaskId(taskId);
//                            // todo 当前 hard code
//                            String topic = mainTask.getTopic();
//                            if (!StringUtil.isNullOrEmpty(topic)) {
//                                mainTask.setTopic(String.format("%s/%s/%s/{num}", topic, taskId, "xxxx"));
//                            }
//                            response.setMainTask(mainTask);
//                            response.setBrokers(taskInfoMetadata.getBrokers());
//                            response.setSubTasks(subTasks);
//
//                            // 计算统计信息
//                            calculateStatistics(response);
//
//                            return response;
//                        })
//                .orTimeout(5, TimeUnit.SECONDS)
//                .exceptionally(ex -> {
//                    log.error("查询任务详情超时或出错: {}", taskId, ex);
//                    return TaskDetailResponse.error("查询任务详情失败: " + ex.getMessage());
//                });
    }

    /**
     * 计算任务统计信息
     */
    private void calculateStatistics(TaskDetailResponse response) {
        if (response.getSubTasks() == null || response.getSubTasks().isEmpty()) {
            return;
        }

        int totalAssignedClients = 0;
        int minClientsPerNode = Integer.MAX_VALUE;
        int maxClientsPerNode = 0;
        Set<String> activeNodes = new HashSet<>();

        for (TaskConfig subTask : response.getSubTasks().values()) {
            int clientCount = subTask.getTotalClientCount();
            totalAssignedClients += clientCount;
            minClientsPerNode = Math.min(minClientsPerNode, clientCount);
            maxClientsPerNode = Math.max(maxClientsPerNode, clientCount);
            activeNodes.add(subTask.getNodeId());
        }

        TaskStatistics stats = new TaskStatistics();
        stats.setTotalNodes(activeNodes.size());
        stats.setTotalAssignedClients(totalAssignedClients);
        stats.setMinClientsPerNode(minClientsPerNode == Integer.MAX_VALUE ? 0 : minClientsPerNode);
        stats.setMaxClientsPerNode(maxClientsPerNode);
        stats.setAverageClientsPerNode(activeNodes.isEmpty() ? 0 : totalAssignedClients / activeNodes.size());

        response.setStatistics(stats);
    }

}
