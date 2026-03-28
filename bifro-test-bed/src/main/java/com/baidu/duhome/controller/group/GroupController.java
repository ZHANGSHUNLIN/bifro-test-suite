package com.baidu.duhome.controller.group;

import com.baidu.duhome.bean.ApiResponse;
import com.baidu.duhome.bean.PageInfo;
import com.baidu.duhome.bean.group.GroupListItem;
import com.baidu.duhome.bean.group.GroupRequest;
import com.baidu.duhome.cluster.GroupManager;
import com.baidu.duhome.controller.ApiController;
import com.baidu.duhome.database.pojo.MqttGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 统一分组管理接口（支持 Broker 分组和任务分组）
 */
@Slf4j
@Tag(name = "分组管理", description = "统一分组管理接口，支持 Broker 分组和任务分组")
@RestController
@RequestMapping("/api/groups")
public class GroupController implements ApiController {

    @Resource
    private GroupManager groupManager;

    /**
     * 添加分组
     */
    @Operation(summary = "添加分组", description = "添加新分组，通过 type 参数指定分组类型（BROKER/TASK）")
    @PostMapping
    public Mono<ApiResponse<MqttGroup>> add(
            @Valid @RequestBody @Parameter(description = "分组请求") GroupRequest request,
            @RequestParam(name = "type") @Parameter(description = "分组类型：BROKER 或 TASK") String type) {
        return groupManager.add(request, type);
    }

    /**
     * 获取所有分组（不分页，用于下拉选择）
     * 注意：必须放在 /{id} 之前，避免路由冲突
     */
    @Operation(summary = "获取所有分组", description = "获取所有分组列表，用于下拉选择，通过 type 参数指定分组类型")
    @GetMapping("/all")
    public Flux<MqttGroup> getAll(
            @Parameter(description = "分组类型：BROKER 或 TASK") @RequestParam(name = "type") String type) {
        return groupManager.getAllGroups(type);
    }

    /**
     * 获取分组详情
     */
    @Operation(summary = "获取分组详情", description = "根据 ID 获取分组详细信息")
    @GetMapping("/{id}")
    public Mono<ApiResponse<MqttGroup>> get(@PathVariable(name = "id") @Parameter(description = "分组ID") String id) {
        return groupManager.getDetail(id);
    }

    /**
     * 更新分组
     */
    @Operation(summary = "更新分组", description = "根据 ID 更新分组")
    @PutMapping("/{id}")
    public Mono<ApiResponse<MqttGroup>> update(
            @PathVariable(name = "id") @Parameter(description = "分组ID") String id,
            @Valid @RequestBody @Parameter(description = "分组请求") GroupRequest request) {
        return groupManager.update(id, request, null);
    }

    /**
     * 删除分组
     */
    @Operation(summary = "删除分组", description = "根据 ID 删除分组")
    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable(name = "id") @Parameter(description = "分组ID") String id) {
        return groupManager.delete(id, null);
    }

    /**
     * 获取分组列表（分页）
     */
    @Operation(summary = "获取分组列表", description = "分页查询分组列表，通过 type 参数指定分组类型")
    @GetMapping("/list")
    public Mono<ApiResponse<PageInfo<GroupListItem>>> list(
            @Parameter(description = "分组类型：BROKER 或 TASK") @RequestParam(name = "type") String type,
            @Parameter(description = "页码") @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize) {
        return groupManager.list(pageNum, pageSize, type);
    }
}
