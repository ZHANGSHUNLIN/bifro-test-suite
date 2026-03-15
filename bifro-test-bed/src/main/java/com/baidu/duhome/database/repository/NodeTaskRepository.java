package com.baidu.duhome.database.repository;

import com.baidu.duhome.database.pojo.NodeTask;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.iot.test.suite.worker.TaskConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NodeTaskRepository extends MongoRepository<NodeTask, String> {


    List<NodeTask> searchAllByTaskId(String taskId);

    NodeTask searchByTaskIdAndNodeId(String taskId, String nodeId);

    NodeTask searchById(String id);

    void deleteByTaskId(String taskId);
}