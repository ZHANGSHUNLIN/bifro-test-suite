package com.baidu.duhome.controller.task;

import com.baidu.duhome.bean.ApiResponse;
import com.baidu.duhome.bean.NodeInfo;
import com.baidu.duhome.bean.PageInfo;
import com.baidu.duhome.bean.TaskDetailResponse;
import com.baidu.duhome.bean.dto.NodeTaskAllocationRequest;
import com.baidu.duhome.bean.vo.NodeTaskAllocationVO;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.bean.dto.TaskRequest;
import com.baidu.duhome.bean.vo.TaskListVO;
import com.baidu.duhome.cluster.TaskManager;
import com.baidu.duhome.cluster.ClusterDataManager;
import com.baidu.duhome.controller.ApiController;
import com.baidu.duhome.database.pojo.Report;
import com.baidu.duhome.database.service.ReportService;
import com.baidu.iot.test.suite.Constants;
import com.baidu.iot.test.suite.TaskSchedule;
import com.baidu.iot.test.suite.worker.TaskConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.vertx.core.Vertx;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;

import com.baidu.iot.test.suite.TaskTemplate;


@Slf4j
@Tag(name = "任务管理", description = "MQTT 测试任务管理接口")
@RestController
@RequestMapping("/api/task")
public class TaskController implements ApiController {

    @Resource
    private Vertx vertx;

    @Resource
    private TaskManager taskManager;

    @Resource
    private ClusterDataManager clusterDataManager;

    @Resource
    private ReportService reportService;

    /**
     * 添加测试任务
     */
    @Operation(summary = "创建测试任务", description = "创建新的 MQTT 压力测试任务")
    @PostMapping()
    public Mono<ApiResponse<TaskInfoMetadata>> addTask(@RequestBody @Parameter(description = "任务配置请求") TaskRequest taskRequest) {
        return taskManager.addTask(taskRequest)
                .map(ApiResponse::success);
    }

    /**
     * 获取所有任务列表（简略信息）
     */
    @Operation(summary = "获取任务列表", description = "分页查询任务列表，支持按任务名称、类型和分组筛选")
    @GetMapping("/list")
    public Mono<ApiResponse<PageInfo<TaskListVO>>> getAllTasks(
            @Parameter(description = "页码", example = "1") @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小", example = "20") @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize,
            @Parameter(description = "任务名称（模糊查询）") @RequestParam(name = "taskName", required = false) String taskName,
            @Parameter(description = "任务类型") @RequestParam(name = "taskType", required = false) String taskType,
            @Parameter(description = "分组ID") @RequestParam(name = "group", required = false) String group
    ) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Mono<Page<TaskInfoMetadata>> allTaskMono;
        if (taskName != null && !taskName.isEmpty() || taskType != null && !taskType.isEmpty() || group != null && !group.isEmpty()) {
            allTaskMono = taskManager.getAllTask(taskName, taskType, group, pageable);
        } else {
            allTaskMono = taskManager.getAllTask(pageable);
        }
        return ApiResponse.pageSuccessMono(allTaskMono, TaskListVO::fromTaskConfig);
    }

    /**
     * 通过taskId查询对应的集群任务和所有节点任务
     */
    @Operation(summary = "获取任务详情", description = "根据任务 ID 获取任务详细信息")
    @GetMapping("/{id}")
    public Mono<ApiResponse<TaskDetailResponse>> getTaskDetails(@PathVariable(name = "id") @Parameter(description = "任务ID") String id) {
        if (id == null || id.trim().isEmpty()) {
            return Mono.just(ApiResponse.error("任务ID不能为空"));
        }
        return taskManager.getTaskDetails(id);
    }

    /**
     * 修改测试任务
     */
    @Operation(summary = "修改测试任务", description = "更新任务配置")
    @PutMapping("/{id}")
    public Mono<ApiResponse<TaskInfoMetadata>> updateTask(
            @PathVariable(value = "id") @Parameter(description = "任务ID") String id,
            @RequestBody @Parameter(description = "任务配置请求") TaskRequest taskRequest) {
        return taskManager.modifyTask(id, taskRequest)
                .map(ApiResponse::success);
    }

    @Operation(summary = "停止任务", description = "手动停止正在执行的任务")
    @PostMapping("/stop/{id}")
    public Mono<ApiResponse<String>> stopTask(@PathVariable(value = "id") @Parameter(description = "任务ID") String id) {
        TaskSchedule taskSchedule =
                TaskSchedule.builder().op(TaskSchedule.Op.UN_REG).id(id).build();
        // 通知全部节点任务准备
        vertx.eventBus().publish(Constants.CLUSTER_TASK_MESSAGE, taskSchedule);
        return Mono.just(ApiResponse.success("已提交任务"));
    }

    @Operation(summary = "删除任务", description = "删除指定任务")
    @DeleteMapping("/{id}")
    public Mono<ApiResponse<TaskDetailResponse>> del(@PathVariable(value = "id") @Parameter(description = "任务ID") String id) {
        return taskManager.delTask(id);
    }

    @Operation(summary = "批量删除任务", description = "批量删除多个任务")
    @DeleteMapping("/batch")
    public Mono<ApiResponse<String>> batchDel(@RequestBody @Parameter(description = "任务ID列表") List<String> taskIds) {
        return taskManager.batchDelTask(taskIds);
    }

    @Operation(summary = "分配任务到节点", description = "将任务分配给指定的节点")
    @PostMapping("/assign/{id}")
    public Mono<ApiResponse<TaskConfig>> assign(@PathVariable(value = "id") @Parameter(description = "任务ID") String id,
                                                @RequestBody @Parameter(description = "节点任务分配请求") NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        return taskManager.assignTask(id, nodeTaskAllocationRequest)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(java.util.concurrent.TimeoutException.class,
                        e -> Mono.just(ApiResponse.error("任务分配超时，请稍后重试")));
    }

    @Operation(summary = "计算节点任务分配", description = "基于权重计算各节点的任务分配方案")
    @PostMapping("/calculate/{id}")
    public Mono<ApiResponse<NodeTaskAllocationVO>> calculateNodeTaskAllocation(@PathVariable(value = "id") @Parameter(description = "任务ID") String id) {
        return taskManager.calculateNodeTaskAllocation(id);
    }

    /**
     * 任务确认，将当前的配置应用到集群
     */
    @Operation(summary = "确认任务", description = "确认任务并通知所有节点开始执行")
    @PostMapping("/{id}/confirmTask")
    public Mono<ApiResponse<Void>> confirmTask(@PathVariable(value = "id") @Parameter(description = "任务ID") String id) {
        TaskSchedule taskSchedule =
                TaskSchedule.builder().op(TaskSchedule.Op.REG).id(id).build();

        // 通知全部节点任务准备
        vertx.eventBus().publish(Constants.CLUSTER_TASK_MESSAGE, taskSchedule);
        return Mono.just(ApiResponse.success());
    }

    @Async
    @GetMapping("/allNodes")
    public CompletableFuture<Map<String, NodeInfo>> allNodes() {
        return clusterDataManager.allNodes();
    }

    @GetMapping("/stop")
    public Boolean stop(@RequestParam(name = "id") Long id) {
        return vertx.cancelTimer(id);
    }

    @GetMapping("/taskReport")
    public Mono<ApiResponse<PageInfo<Report>>> taskReport(
            @RequestParam(name = "taskId") String taskId,
            @RequestParam(name = "nodeId") String nodeId,
            @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        return ApiResponse.pageSuccessMono(reportService.taskReport(taskId, nodeId, pageNum, pageSize));
    }

    /**
     * 获取所有可用的任务模板列表
     */
    @Operation(summary = "获取任务模板列表", description = "返回所有可用的任务模板类型")
    @GetMapping("/templates")
    public ApiResponse<List<Map<String, String>>> getTemplates() {
        List<Map<String, String>> templates = Arrays.stream(TaskTemplate.values())
                .map(t -> {
                    String type = t.name().startsWith("CONN") ? "CONN"
                            : t.name().startsWith("PUBSUB") ? "PUBSUB" : "OTHER";
                    return Map.of("value", t.name(), "label", t.getLabel(), "type", type);
                })
                .toList();
        return ApiResponse.success(templates);
    }

}