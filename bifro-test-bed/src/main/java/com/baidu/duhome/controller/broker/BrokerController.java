package com.baidu.duhome.controller.broker;

import com.baidu.duhome.bean.ApiResponse;
import com.baidu.duhome.bean.PageInfo;
import com.baidu.duhome.bean.broker.BrokerListItem;
import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.duhome.bean.broker.MqttBrokerEnableRequest;
import com.baidu.duhome.bean.broker.MqttBrokerRequest;
import com.baidu.duhome.cluster.BrokerManager;
import com.baidu.duhome.controller.ApiController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;


@Slf4j
@Tag(name = "Broker 管理", description = "MQTT Broker 配置管理接口")
@RestController
@RequestMapping("/api/broker")
public class BrokerController implements ApiController {

    @Resource
    private BrokerManager brokerManager;

    /**
     * 添加测试任务
     */
    @Operation(summary = "添加 Broker", description = "添加新的 MQTT Broker 配置")
    @PostMapping("/add")
    public Mono<ApiResponse<MqttBroker>> addTask(@Valid @RequestBody @Parameter(description = "Broker 配置请求") MqttBrokerRequest mqttBrokerRequest) {
        return brokerManager.addTask(mqttBrokerRequest);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 Broker", description = "根据 ID 删除 MQTT Broker 配置")
    public Mono<ApiResponse<MqttBroker>> del(@PathVariable(name = "id") String id) {
        return brokerManager.del(id);
    }


    @PutMapping("/{id}")
    @Operation(summary = "更新 Broker", description = "更新 MQTT Broker 配置")
    public Mono<ApiResponse<MqttBroker>> update(@PathVariable(name = "id") String id,
                                          @Valid @RequestBody @Parameter(description = "Broker 配置请求") MqttBrokerRequest mqttBrokerRequest) {
        return brokerManager.update(id, mqttBrokerRequest);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "启用/禁用 Broker", description = "启用或禁用 MQTT Broker")
    public Mono<ApiResponse<MqttBroker>> enable(@PathVariable(name = "id") String id, @RequestBody @Parameter(description = "启用/禁用请求") MqttBrokerEnableRequest enabled) {
        return brokerManager.enable(id, enabled.getEnabled());
    }

    /**
     * 获取所有任务列表（简略信息）
     */
    @Operation(summary = "获取 Broker 列表", description = "分页查询 Broker 列表")
    @GetMapping("/list")
    public Mono<ApiResponse<PageInfo<BrokerListItem>>> list(
            @Parameter(description = "是否启用") @RequestParam(name = "enabled", required = false) Boolean enabled,
            @Parameter(description = "分组ID") @RequestParam(name = "group", required = false) String group,
            @Parameter(description = "页码", example = "1") @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小", example = "20") @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        return brokerManager.list(enabled, group, pageNum, pageSize);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取 Broker 详情", description = "根据 ID 获取 MQTT Broker 详细信息")
    public Mono<ApiResponse<MqttBroker>> get(@PathVariable(name = "id") String brokerId) {
        if (brokerId == null || brokerId.trim().isEmpty()) {
            return Mono.error(new RuntimeException("brokerId不能为空"));
        }
        return brokerManager.get(brokerId);
    }


}