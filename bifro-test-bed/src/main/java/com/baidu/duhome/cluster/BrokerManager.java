package com.baidu.duhome.cluster;


import com.baidu.duhome.bean.ApiResponse;
import com.baidu.duhome.bean.PageInfo;
import com.baidu.duhome.bean.broker.BrokerListItem;
import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.duhome.bean.broker.MqttBrokerRequest;
import com.baidu.duhome.database.repository.MqttBrokerRepository;
import com.baidu.duhome.service.BrokerService;
import com.baidu.iot.test.suite.ShareDataManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * 所有的任务均爆粗你在vertx的分布式数据结构中存储和通信。暂无引入持久化机制。
 * 任务分为两类，一类是集群任务，另一类是本地任务。
 * 集群任务为本次的测试的最终目标，本地任务为按照分配策略将集群任务分配给具体节点的子任务分片。
 */
@Component
@Slf4j
public class BrokerManager implements BrokerService {

    @Resource
    private ShareDataManager shareDataManager;


    @Resource
    private MqttBrokerRepository mqttBrokerRepository;

    public ApiResponse<MqttBroker> addTask(MqttBrokerRequest mqttBrokerRequest) {
        MqttBroker broker = new MqttBroker();
        BeanUtils.copyProperties(mqttBrokerRequest, broker);
        return ApiResponse.success(mqttBrokerRepository.save(broker));
//        return shareDataManager.<String, MqttBroker>getMap(ShareDataAddr.BROKER_MAP_NAME)
//                .putIfAbsent(mqttBrokerRequest.getBrokerId(), broker)
//                .oldVal()
//                .thenApply(r -> broker);
    }

    public ApiResponse<PageInfo<BrokerListItem>> list(Boolean enabled, Integer pageNum, Integer pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));

        if (enabled == null) {
            return ApiResponse.pageSuccess(mqttBrokerRepository.findAll(pageable), broker -> {
                BrokerListItem brokerListItem = new BrokerListItem();
                BeanUtils.copyProperties(broker, brokerListItem);
                return brokerListItem;
            });
        }

        return ApiResponse.pageSuccess(mqttBrokerRepository.findAllByEnabled(enabled, pageable), broker -> {
            BrokerListItem brokerListItem = new BrokerListItem();
            BeanUtils.copyProperties(broker, brokerListItem);
            return brokerListItem;
        });
//        return shareDataManager.<String, MqttBroker>getMap(ShareDataAddr.BROKER_MAP_NAME)
//                .values()
//                .thenApply(r -> r.stream()
//                        .filter(mqttBroker -> enabled == null || Objects.equals(enabled, mqttBroker.getEnabled()))
//                        .map(broker -> {
//                            BrokerListItem brokerListItem = new BrokerListItem();
//                            BeanUtils.copyProperties(broker, brokerListItem);
//                            return brokerListItem;
//                        })
//                        .toList());
    }

    public ApiResponse<MqttBroker> get(String brokerId) {
        return ApiResponse.success(mqttBrokerRepository.findFirstById((brokerId)));

//        return shareDataManager.<String, MqttBroker>getMap(ShareDataAddr.BROKER_MAP_NAME)
//                .get(brokerId)
//                .oldVal()
//                .thenApply(r -> r.orElseGet(() -> null));
    }

    public ApiResponse<MqttBroker> del(String brokerId) {
        mqttBrokerRepository.deleteById(brokerId);
        return ApiResponse.success();
//        return shareDataManager.<String, MqttBroker>getMap(ShareDataAddr.BROKER_MAP_NAME)
//                .remove(brokerId)
//                .oldVal()
//                .thenApply(r->r.orElseGet(()->null));

    }

    public ApiResponse<MqttBroker> update(String id, MqttBrokerRequest mqttBrokerRequest) {
        MqttBroker broker = new MqttBroker();
        BeanUtils.copyProperties(mqttBrokerRequest, broker);
        broker.setId(id);
        return ApiResponse.success(mqttBrokerRepository.save(broker));
//        return shareDataManager.<String, MqttBroker>getMap(ShareDataAddr.BROKER_MAP_NAME)
//                .replace(mqttBrokerRequest.getBrokerId(), broker)
//                .oldVal()
//                .thenApply(r -> broker);
    }

    public ApiResponse<MqttBroker> enable(String id, Boolean enabled) {
        mqttBrokerRepository.updateEnabledById(id, enabled);
        return ApiResponse.success();
//        return shareDataManager.<String, MqttBroker>getMap(ShareDataAddr.BROKER_MAP_NAME)
//                .get(brokerId)
//                .thenAcceptOnValue((kv, broker) -> {
//                    broker.setEnabled(enabled);
//                    kv.replace(brokerId, broker);
//                })
//                .oldVal()
//                .thenApply(r->r.orElseGet(()->null));
    }
}
