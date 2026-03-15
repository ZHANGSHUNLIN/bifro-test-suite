package com.baidu.duhome.database.repository;

import com.baidu.duhome.database.pojo.MqttBroker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MqttBrokerRepository extends MongoRepository<MqttBroker, String> {


    @Query("{ '_id' : ?0 }")
    @Update(value = "{ $set: { 'enabled': ?1 } }")
    void updateEnabledById(String id, Boolean enabled);

    MqttBroker findFirstById(String id);

    Page<MqttBroker> findAllByEnabled(Boolean enabled, Pageable pageable);
}