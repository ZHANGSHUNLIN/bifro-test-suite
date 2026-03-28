package com.baidu.duhome.database.repository;

import com.baidu.duhome.database.pojo.MqttGroup;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MQTT 分组 Repository（支持 Broker 分组和任务分组）
 */
@Repository
public interface MqttGroupRepository extends ReactiveMongoRepository<MqttGroup, String> {

    /**
     * 根据名称查找分组
     */
    default Mono<MqttGroup> findByName(String name) {
        return findAll().filter(g -> name.equals(g.getName())).next();
    }

    /**
     * 根据名称和类型查找分组
     */
    default Mono<MqttGroup> findByNameAndType(String name, String type) {
        return findAll().filter(g -> name.equals(g.getName()) && type.equals(g.getType())).next();
    }

    /**
     * 根据类型查找分组
     */
    default Flux<MqttGroup> findByType(String type) {
        return findAll().filter(g -> type.equals(g.getType()));
    }

    /**
     * 按名称升序查找所有分组
     */
    default Flux<MqttGroup> findAllByOrder() {
        return findAll().sort((g1, g2) -> g1.getName().compareTo(g2.getName()));
    }
}
