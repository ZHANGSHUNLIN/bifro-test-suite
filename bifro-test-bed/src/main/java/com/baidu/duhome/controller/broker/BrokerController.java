package com.baidu.duhome.controller.broker;

import com.baidu.duhome.bean.ApiResponse;
import com.baidu.duhome.bean.PageInfo;
import com.baidu.duhome.bean.broker.BrokerListItem;
import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.duhome.bean.broker.MqttBrokerEnableRequest;
import com.baidu.duhome.bean.broker.MqttBrokerRequest;
import com.baidu.duhome.cluster.BrokerManager;
import com.baidu.duhome.controller.ApiController;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;


@Slf4j
@RestController
@RequestMapping("/api/broker")
public class BrokerController implements ApiController {

    @Resource
    private BrokerManager brokerManager;

    /**
     * 添加测试任务
     */
    @PostMapping("/add")
    public ApiResponse<MqttBroker> addTask(@RequestBody MqttBrokerRequest mqttBrokerRequest) {
        return brokerManager.addTask(mqttBrokerRequest);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<MqttBroker> del(@PathVariable(name = "id") String id) {
        return brokerManager.del(id);
    }


    @PutMapping("/{id}")
    public ApiResponse<MqttBroker> update(@PathVariable(name = "id") String id,
                                          @RequestBody MqttBrokerRequest mqttBrokerRequest) {
        return brokerManager.update(id,mqttBrokerRequest);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<MqttBroker> enable(@PathVariable(name = "id") String id, @RequestBody MqttBrokerEnableRequest enabled) {
        return brokerManager.enable(id, enabled.getEnabled());
    }

    /**
     * 获取所有任务列表（简略信息）
     */
    @GetMapping("/list")
    public ApiResponse<PageInfo<BrokerListItem>> list(
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        return brokerManager.list(enabled, pageNum, pageSize);
    }

    @GetMapping("/{id}")
    public ApiResponse<MqttBroker> get(@PathVariable(name = "id") String brokerId) {
        if (brokerId == null || brokerId.trim().isEmpty()) {
            throw new RuntimeException("brokerId不能为空");
        }
        return brokerManager.get(brokerId);
    }

//    @GetMapping("/{id}")
//    public ApiResponse<PageInfo<MqttBroker>> taskList(@PathVariable(name = "id") String brokerId,
//                                                      @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
//                                                      @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize
//    ) {
//        if (brokerId == null || brokerId.trim().isEmpty()) {
//            throw new RuntimeException("brokerId不能为空");
//        }
//        return brokerManager.taskList(brokerId, pageNum, pageSize);
//    }


}