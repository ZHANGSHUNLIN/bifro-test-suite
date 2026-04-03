package com.baidu.duhome.cluster;


import com.baidu.duhome.bean.ApiResponse;
import com.baidu.duhome.bean.PageInfo;
import com.baidu.duhome.bean.broker.BrokerListItem;
import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.duhome.bean.broker.MqttBrokerRequest;
import com.baidu.duhome.database.repository.MqttBrokerRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Broker 管理器，提供 MQTT Broker 配置的增删改查功能。
 */
@Component
@Slf4j
public class BrokerManager {

    @Resource
    private MqttBrokerRepository mqttBrokerRepository;

    public Mono<ApiResponse<MqttBroker>> addTask(MqttBrokerRequest mqttBrokerRequest) {
        MqttBroker broker = new MqttBroker();
        BeanUtils.copyProperties(mqttBrokerRequest, broker);
        return mqttBrokerRepository.save(broker)
                .map(ApiResponse::success);
    }

    public Mono<ApiResponse<PageInfo<BrokerListItem>>> list(Boolean enabled, String group, Integer pageNum, Integer pageSize) {
        // 注意：响应式 MongoDB 不支持原生分页，这里使用全量查询后在内存中分页
        // TODO: 后续可以考虑使用 MongoDB 响应式分页或滚动查询优化大数据量场景
        Flux<MqttBroker> allBrokers = mqttBrokerRepository.findAll();

        // 按条件过滤
        if (enabled != null) {
            allBrokers = allBrokers.filter(b -> Objects.equals(b.getEnabled(), enabled));
        }
        if (group != null && !group.isEmpty()) {
            allBrokers = allBrokers.filter(b -> group.equals(b.getGroup()));
        }

        return allBrokers.collectList()
                .map(brokerList -> {
                    // 内存分页
                    int total = brokerList.size();
                    int fromIndex = (pageNum - 1) * pageSize;
                    int toIndex = Math.min(fromIndex + pageSize, total);
                    List<MqttBroker> pageList = total > fromIndex
                            ? brokerList.subList(fromIndex, toIndex)
                            : new ArrayList<>();

                    List<BrokerListItem> resultList = pageList.stream()
                            .map(broker -> {
                                BrokerListItem item = new BrokerListItem();
                                BeanUtils.copyProperties(broker, item);
                                return item;
                            })
                            .collect(Collectors.toList());

                    return ApiResponse.pageSuccess(resultList, total, pageNum, pageSize);
                });
    }

    public Mono<ApiResponse<MqttBroker>> get(String brokerId) {
        return mqttBrokerRepository.findFirstById(brokerId)
                .map(ApiResponse::success)
                .defaultIfEmpty(ApiResponse.error("Broker不存在"));
    }

    public Mono<ApiResponse<MqttBroker>> del(String brokerId) {
        return mqttBrokerRepository.deleteById(brokerId)
                .then(Mono.just(ApiResponse.success()));
    }

    public Mono<ApiResponse<MqttBroker>> update(String id, MqttBrokerRequest mqttBrokerRequest) {
        return mqttBrokerRepository.findById(id)
                .flatMap(existing -> {
                    BeanUtils.copyProperties(mqttBrokerRequest, existing);
                    return mqttBrokerRepository.save(existing);
                })
                .map(ApiResponse::success)
                .defaultIfEmpty(ApiResponse.error("Broker不存在"));
    }

    public Mono<ApiResponse<MqttBroker>> enable(String id, Boolean enabled) {
        return mqttBrokerRepository.findById(id)
                .flatMap(broker -> {
                    broker.setEnabled(enabled);
                    return mqttBrokerRepository.save(broker);
                })
                .map(ApiResponse::success)
                .defaultIfEmpty(ApiResponse.error("Broker不存在"));
    }
}
