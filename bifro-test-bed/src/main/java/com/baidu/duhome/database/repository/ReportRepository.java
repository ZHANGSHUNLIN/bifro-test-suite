package com.baidu.duhome.database.repository;

import com.baidu.duhome.database.pojo.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends MongoRepository<Report, String> {

    Page<Report> findByTaskIdAndNodeIdOrderByCreateTimeDesc(String taskId, String nodeId, Pageable pageable);

}