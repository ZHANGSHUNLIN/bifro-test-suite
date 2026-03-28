package com.baidu.duhome.database.repository;

import com.baidu.duhome.database.pojo.Report;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;

@Repository
public interface ReportRepository extends ReactiveMongoRepository<Report, String> {

    Flux<Report> findByTaskIdAndNodeIdOrderByCreateTimeDesc(String taskId, String nodeId, Pageable pageable);

}