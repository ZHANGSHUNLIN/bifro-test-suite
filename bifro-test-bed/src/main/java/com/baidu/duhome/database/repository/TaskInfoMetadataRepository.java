package com.baidu.duhome.database.repository;

import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.iot.test.suite.worker.TaskConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskInfoMetadataRepository extends MongoRepository<TaskInfoMetadata, String> {

    @Query("{ '_id' : ?0 }")
    @Update(value = "{ $set: { 'taskConfig': ?1 } }")
    void updateTaskConfigById(String id, TaskConfig taskConfig);

}