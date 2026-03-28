package com.baidu.duhome.database.repository;

import com.baidu.duhome.database.pojo.NodeTask;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface NodeTaskRepository extends ReactiveMongoRepository<NodeTask, String> {

    Flux<NodeTask> findAllByTaskId(String taskId);

    Mono<NodeTask> findFirstByTaskId(String taskId);

    Mono<NodeTask> findByTaskIdAndNodeId(String taskId, String nodeId);

    Mono<Void> deleteByTaskId(String taskId);
}

