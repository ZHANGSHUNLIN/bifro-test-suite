package com.baidu.duhome.database.repository;

import com.baidu.duhome.database.pojo.MqttBroker;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MQTT Broker 数据访问接口 - 响应式版本
 */
@Repository
public interface MqttBrokerRepository extends ReactiveMongoRepository<MqttBroker, String> {

    /**
     * 根据 ID 查找第一个匹配的 Broker
     */
    default Mono<MqttBroker> findFirstById(String id) {
        return findById(id);
    }

    /**
     * 根据分组ID查询 Broker 列表
     */
    default Flux<MqttBroker> findByGroup(String groupId) {
        return findAll().filter(b -> b.getGroup() != null && b.getGroup().equals(groupId));
    }
}
