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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
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
    @PostMapping()
    public ApiResponse<?> addTask(@RequestBody TaskRequest taskRequest) {
        return ApiResponse.success(taskManager.addTask(taskRequest));
    }

    /**
     * 获取所有任务列表（简略信息）
     */
    @GetMapping("/list")
    public ApiResponse<PageInfo<TaskListVO>> getAllTasks(
            @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(name = "taskName", required = false) String taskName,
            @RequestParam(name = "taskType", required = false) String taskType
    ) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskInfoMetadata> allTask;
        if (taskName != null && !taskName.isEmpty() || taskType != null && !taskType.isEmpty()) {
            allTask = taskManager.getAllTask(taskName, taskType, pageable);
        } else {
            allTask = taskManager.getAllTask(pageable);
        }
        return ApiResponse.pageSuccess(allTask, TaskListVO::fromTaskConfig);
    }

    /**
     * 通过taskId查询对应的集群任务和所有节点任务,todo 需要改为mongo存储
     */
    @GetMapping("/{id}")
    public ApiResponse<TaskDetailResponse> getTaskDetails(@PathVariable(name = "id") String id) {
        if (id == null || id.trim().isEmpty()) {
            return ApiResponse.error("任务ID不能为空");
        }
        return taskManager.getTaskDetails(id);
    }

    /**
     * 修改测试任务
     */
    @PutMapping("/{id}")
    public ApiResponse<TaskInfoMetadata> updateTask(@PathVariable(value = "id") String id, @RequestBody TaskRequest taskRequest) {
        return ApiResponse.success(taskManager.modifyTask(id, taskRequest));
//        return ret(taskManager.modifyTask(id, taskRequest)
//                .thenApply(r -> r.orElseThrow(() -> new ApiException(id + "任务不存在")))
//                .thenApply(TaskInfoMetadata::getTaskConfig));
    }

    @PostMapping("/stop/{id}")
    public ApiResponse<String> stopTask(@PathVariable(value = "id") String id) {
        vertx.eventBus().publish(Constants.STOP_CLUSTER_TASK_ADDR, id);
        return ApiResponse.success("已提交任务");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<TaskDetailResponse> del(@PathVariable(value = "id") String id) {

        return taskManager.delTask(id);
    }

    @DeleteMapping("/batch")
    public ApiResponse<String> batchDel(@RequestBody List<String> taskIds) {
        return taskManager.batchDelTask(taskIds);
    }

    @PostMapping("/assign/{id}")
    public ApiResponse<TaskConfig> assign(@PathVariable(value = "id") String id,
                                          @RequestBody NodeTaskAllocationRequest nodeTaskAllocationRequest) {
        return taskManager.assignTask(id, nodeTaskAllocationRequest);
//        return ret(taskManager.assignTask(id));
    }

    @PostMapping("/calculate/{id}")
    public ApiResponse<NodeTaskAllocationVO> calculateNodeTaskAllocation(@PathVariable(value = "id") String id) {
        return taskManager.calculateNodeTaskAllocation(id);
    }

    /**
     * 任务确认，将当前的配置应用到集群
     */
    @PostMapping("/{id}/confirmTask")
    public ApiResponse<TaskDetailResponse> confirmTask(@PathVariable(value = "id") String id) {

        TaskSchedule taskSchedule =
                TaskSchedule.builder().op(TaskSchedule.Op.REG).id(id).build();

        // 通知全部节点任务准备
        vertx.eventBus().publish(Constants.CLUSTER_TASK_MESSAGE, taskSchedule);
        return ApiResponse.success();
    }

    @Async
    @GetMapping("/allNodes")
    public CompletableFuture<Map<String, NodeInfo>> allNodes() {
        return clusterDataManager.allNodes();
    }

    @Async
    @GetMapping("/something")
    public CompletableFuture<Map<Object, Object>> something(@RequestParam(name = "key") String key) {
        return clusterDataManager.something(key);
    }

    @GetMapping("/stop")
    public Boolean stop(@RequestParam(name = "id") Long id) {
        return vertx.cancelTimer(id);
    }

    @GetMapping("/taskReport")
    public ApiResponse<PageInfo<Report>> taskReport(
            @RequestParam(name = "taskId") String taskId,
            @RequestParam(name = "nodeId") String nodeId,
            @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        return ApiResponse.pageSuccess(reportService.taskReport(taskId, nodeId, pageNum, pageSize));
    }

}