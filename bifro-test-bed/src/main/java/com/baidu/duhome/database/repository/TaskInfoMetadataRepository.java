package com.baidu.duhome.database.repository;

import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.iot.test.suite.worker.TaskConfig;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface TaskInfoMetadataRepository extends ReactiveMongoRepository<TaskInfoMetadata, String> {

    @Query("{ '_id' : ?0 }")
    @Update(value = "{ $set: { 'taskConfig': ?1 } }")
    Mono<Void> updateTaskConfigById(String id, TaskConfig taskConfig);

    /**
     * 根据分组ID查询任务
     */
    Flux<TaskInfoMetadata> findByGroup(String groupId);
}