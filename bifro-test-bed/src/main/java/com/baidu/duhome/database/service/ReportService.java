package com.baidu.duhome.database.service;

import com.baidu.duhome.database.pojo.Report;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.repository.ReportRepository;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ReportService {

    @Resource
    private ReportRepository reportRepository;

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Resource
    private ReactiveMongoTemplate reactiveMongoTemplate;

    public Mono<Page<Report>> taskReport(String taskId, String nodeId, Integer pageNum, Integer pageSize) {
        Mono<TaskInfoMetadata> metadataMono = taskInfoMetadataRepository.findById(taskId);
        return metadataMono.flatMap(metadata -> {
            // Pageable 是从 0 开始计页的，所以前端传来的页码需要减 1
            Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
            Query query = new Query();
            query.addCriteria(Criteria.where("taskId").is(metadata.getTaskConfig().getTaskId()));
            query.addCriteria(Criteria.where("nodeId").is(nodeId));
            query.with(pageable);
            return reactiveMongoTemplate.find(query, Report.class)
                    .collectList()
                    .zipWith(reactiveMongoTemplate.count(Query.of(query).limit(-1).skip(-1), Report.class))
                    .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
        });
    }
}
