package com.baidu.duhome.cluster;


import com.baidu.duhome.bean.ApiResponse;
import com.baidu.duhome.bean.TaskDetailResponse;
import com.baidu.duhome.bean.dto.BrokerEntry;
import com.baidu.duhome.bean.dto.NodeTaskAllocationRequest;
import com.baidu.duhome.bean.vo.NodeTaskAllocationVO;
import com.baidu.duhome.database.pojo.NodeTask;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.duhome.bean.dto.TaskRequest;
import com.baidu.duhome.bean.TaskStatistics;
import com.baidu.duhome.database.repository.NodeTaskRepository;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import com.baidu.duhome.database.repository.MqttBrokerRepository;
import com.baidu.duhome.database.service.TaskInfoMetadataService;
import com.baidu.duhome.exception.ApiException;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.worker.TaskConfig;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    @Resource
    private MqttBrokerRepository mqttBrokerRepository;

    public Mono<TaskInfoMetadata> addTask(TaskRequest taskRequest) {
        if (taskRequest == null) {
            return Mono.error(new ApiException("Task request cannot be null"));
        }

        List<String> brokerIds = taskRequest.getBrokers().stream()
                .map(BrokerEntry::getBrokerId)
                .collect(Collectors.toList());

        // 校验 Broker 是否属于同一分组
        return validateBrokersInSameGroup(brokerIds)
                .flatMap(isValid -> {
                    if (!isValid) {
                        return Mono.error(new ApiException("选中的 Broker 必须属于同一分组"));
                    }

                    List<MqttBroker> brokers = taskRequest.getBrokers().stream()
                            .map(r -> MqttBroker.builder()
                                    .brokerId(r.getBrokerId())
                                    .host(r.getHost())
                                    .port(r.getPort())
                                    .build())
                            .toList();

                    TaskConfig mainTaskConfig = taskRequest.toTaskConfig();
                    String nextTaskId = RandomStringUtils.secure().next(8, true, true);
                    mainTaskConfig.setTaskId(nextTaskId);
                    mainTaskConfig.setGroup(taskRequest.getGroup());
                    TaskInfoMetadata taskInfoMetadata = TaskInfoMetadata.builder()
                            .taskName(taskRequest.getTaskName())
                            .taskConfig(mainTaskConfig)
                            .createTime(LocalDateTime.now())
                            .taskId(nextTaskId)
                            .brokers(brokers)
                            .group(taskRequest.getGroup())
                            .build();
                    log.info("add task taskInfoMetadata: {}", taskInfoMetadata);
                    return taskInfoMetadataService.insertTaskInfoMetadata(taskInfoMetadata);
                });
    }


    public Mono<ApiResponse<TaskConfig>> assignTask(String id, NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        return taskInfoMetadataRepository.findById(id)
                .flatMap(taskInfoMetadata -> {
                    TaskConfig taskConfig = taskInfoMetadata.getTaskConfig();
                    TaskStage currentStage = taskConfig.getTaskWorkStage();

                    if (currentStage == TaskStage.ONGOING || currentStage == TaskStage.START
                            || currentStage == TaskStage.COLLECTING || currentStage == TaskStage.SHUTDOWN_ING) {
                        return Mono.just(ApiResponse.<TaskConfig>error("当前状态不允许分配任务: " + currentStage));
                    }

                    return Mono.fromFuture(clusterDataManager.assignCheck(id, taskConfig, nodeTaskAllocationRequest))
                            .then(Mono.defer(() -> {
                                taskConfig.setTaskWorkStage(TaskStage.ASSIGNED);
                                return taskInfoMetadataRepository.updateTaskConfigById(id, taskConfig);
                            }))
                            .thenReturn(ApiResponse.success(taskConfig));
                })
                .switchIfEmpty(Mono.just(ApiResponse.error("任务不存在")));
    }

    public Mono<ApiResponse<NodeTaskAllocationVO>> calculateNodeTaskAllocation(String id) {
        return taskInfoMetadataRepository.findById(id)
                .map(taskInfoMetadata -> {
                    TaskConfig taskConfig = taskInfoMetadata.getTaskConfig();
                    return ApiResponse.success(clusterDataManager.calcuTasksToNodes(taskConfig));
                })
                .switchIfEmpty(Mono.just(ApiResponse.error("任务不存在")));
    }


    public Mono<TaskInfoMetadata> modifyTask(String taskId, TaskRequest taskRequest) {
        if (taskRequest == null) {
            return Mono.error(new ApiException("Task request cannot be null"));
        }

        if (taskId == null) {
            return Mono.error(new ApiException("Task id cannot be null"));
        }

        List<String> brokerIds = taskRequest.getBrokers().stream()
                .map(BrokerEntry::getBrokerId)
                .collect(Collectors.toList());

        // 校验 Broker 是否属于同一分组
        return validateBrokersInSameGroup(brokerIds)
                .flatMap(isValid -> {
                    if (!isValid) {
                        return Mono.error(new ApiException("选中的 Broker 必须属于同一分组"));
                    }

                    return taskInfoMetadataRepository.findById(taskId)
                            .flatMap(existingMetadata -> {
                                List<MqttBroker> brokers = taskRequest.getBrokers().stream()
                                        .map(r -> MqttBroker.builder()
                                                .brokerId(r.getBrokerId())
                                                .host(r.getHost())
                                                .port(r.getPort())
                                                .build())
                                        .toList();

                                TaskConfig mainTaskConfig = taskRequest.toTaskConfig();
                                mainTaskConfig.setTaskId(taskId);

                                // 更新现有记录的字段
                                existingMetadata.setTaskConfig(mainTaskConfig);
                                existingMetadata.setBrokers(brokers);
                                existingMetadata.setTaskName(taskRequest.getTaskName());
                                existingMetadata.setGroup(taskRequest.getGroup());

                                log.info("modify task: {}", existingMetadata);

                                return taskInfoMetadataRepository.save(existingMetadata);
                            })
                            .switchIfEmpty(Mono.error(new ApiException("Task not found with id: " + taskId)));
                });
    }


    public Mono<ApiResponse<TaskDetailResponse>> delTask(String taskId) {
        return taskInfoMetadataRepository.findById(taskId)
                .flatMap(taskInfoMetadata -> {
                    TaskConfig taskConfig = taskInfoMetadata.getTaskConfig();
                    TaskStage taskWorkStage = taskConfig.getTaskWorkStage();
                    if (taskWorkStage == TaskStage.ONGOING || taskWorkStage == TaskStage.START || taskWorkStage == TaskStage.COLLECTING) {
                        return Mono.just(ApiResponse.<TaskDetailResponse>error("任务已开始无法删除"));
                    }
                    return Mono.zip(
                            taskInfoMetadataRepository.deleteById(taskId),
                            nodeTaskRepository.deleteByTaskId(taskId)
                    ).thenReturn(ApiResponse.<TaskDetailResponse>success());
                })
                .switchIfEmpty(Mono.just(ApiResponse.<TaskDetailResponse>error("任务不存在")));
    }

    public Mono<ApiResponse<String>> batchDelTask(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Mono.just(ApiResponse.error("任务ID列表不能为空"));
        }

        List<String> deletedIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();

        return Flux.fromIterable(taskIds)
                .flatMap(taskId -> taskInfoMetadataRepository.findById(taskId)
                        .flatMap(taskInfoMetadata -> {
                            TaskConfig taskConfig = taskInfoMetadata.getTaskConfig();
                            TaskStage taskWorkStage = taskConfig.getTaskWorkStage();
                            if (taskWorkStage == TaskStage.ONGOING || taskWorkStage == TaskStage.START || taskWorkStage == TaskStage.COLLECTING) {
                                failedIds.add(taskId + "(任务已开始无法删除)");
                                return Mono.empty();
                            }
                            return Mono.zip(
                                    taskInfoMetadataRepository.deleteById(taskId),
                                    nodeTaskRepository.deleteByTaskId(taskId)
                            ).doOnSuccess(v -> deletedIds.add(taskId));
                        })
                        .switchIfEmpty(Mono.fromRunnable(() -> failedIds.add(taskId + "(任务不存在)"))))
                .then(Mono.fromSupplier(() -> {
                    if (failedIds.isEmpty()) {
                        return ApiResponse.success("成功删除" + deletedIds.size() + "个任务");
                    } else {
                        return ApiResponse.success("成功删除" + deletedIds.size() + "个任务，失败" + failedIds.size() + "个: " + String.join(", ", failedIds));
                    }
                }));
    }


    public Mono<Page<TaskInfoMetadata>> getAllTask(Pageable pageable) {
        return taskInfoMetadataService.findAll(pageable);
    }

    /**
     * 根据任务名称和任务类型分页查询
     */
    public Mono<Page<TaskInfoMetadata>> getAllTask(String taskName, String taskType, Pageable pageable) {
        return taskInfoMetadataService.findByFilters(taskName, taskType, pageable);
    }

    public Mono<ApiResponse<TaskDetailResponse>> getTaskDetails(String taskId) {
        return taskInfoMetadataRepository.findById(taskId)
                .flatMap(taskInfoMetadata -> {
                    TaskConfig mainTask = taskInfoMetadata.getTaskConfig();

                    TaskDetailResponse response = new TaskDetailResponse();
                    response.setSuccess(true);
                    response.setTaskId(taskId);
                    // todo 当前 hard code
                    String topic = mainTask.getTopic();
                    if (!StringUtil.isNullOrEmpty(topic)) {
                        mainTask.setTopic(String.format("%s/%s/%s/{num}", topic, taskInfoMetadata.getTaskId(), "xxxx"));
                    }
                    response.setMainTask(mainTask);
                    response.setBrokers(taskInfoMetadata.getBrokers());
                    response.setTaskName(taskInfoMetadata.getTaskName());
                    response.setGroup(taskInfoMetadata.getGroup());

                    return nodeTaskRepository.findAllByTaskId(taskId).collectList()
                            .map(nodeTasks -> {
                                Map<String, TaskConfig> subTasks = nodeTasks.stream()
                                        .collect(Collectors.toMap(
                                                NodeTask::getNodeId,
                                                NodeTask::getTaskConfig
                                        ));
                                response.setSubTasks(subTasks);
                                // 计算统计信息
                                calculateStatistics(response);
                                return ApiResponse.success(response);
                            });
                })
                .switchIfEmpty(Mono.just(ApiResponse.<TaskDetailResponse>error("任务不存在")));
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

    /**
     * 校验选中的 Broker 是否属于同一分组
     * 规则：
     * 1. 有分组的 Broker 必须属于同一分组
     * 2. 所有选中的 Broker 必须要么全有分组，要么全没有分组
     * @param brokerIds Broker ID 列表
     * @return 校验通过返回 true，否则返回 false
     */
    private Mono<Boolean> validateBrokersInSameGroup(List<String> brokerIds) {
        if (brokerIds == null || brokerIds.isEmpty()) {
            return Mono.just(true);
        }
        return mqttBrokerRepository.findAllById(brokerIds)
                .collectList()
                .map(brokers -> {
                    String commonGroup = null;
                    boolean hasGroup = false;
                    for (MqttBroker broker : brokers) {
                        String group = broker.getGroup();
                        if (group != null && !group.isEmpty()) {
                            if (!hasGroup) {
                                commonGroup = group;
                                hasGroup = true;
                            } else if (!commonGroup.equals(group)) {
                                return false; // 分组不一致
                            }
                        } else if (hasGroup) {
                            return false; // 不能混用有分组和无分组
                        }
                    }
                    return true;
                });
    }

}
