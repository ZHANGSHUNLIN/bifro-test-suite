package com.baidu.duhome.database.service;

import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class TaskInfoMetadataService {

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Resource
    private ReactiveMongoTemplate reactiveMongoTemplate;


    public Mono<TaskInfoMetadata> insertTaskInfoMetadata(TaskInfoMetadata taskInfoMetadata) {
        return taskInfoMetadataRepository.insert(taskInfoMetadata);
    }


    public Mono<Page<TaskInfoMetadata>> findAll(Pageable pageable) {
        Query query = new Query();
        query.with(pageable);
        return reactiveMongoTemplate.find(query, TaskInfoMetadata.class)
                .collectList()
                .zipWith(reactiveMongoTemplate.count(Query.of(query).limit(-1).skip(-1), TaskInfoMetadata.class))
                .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
    }

    /**
     * 根据任务名称、任务类型和分组分页查询
     */
    public Mono<Page<TaskInfoMetadata>> findByFilters(String taskName, String taskType, String group, Pageable pageable) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        // 任务名称模糊匹配
        if (taskName != null && !taskName.isEmpty()) {
            criteriaList.add(Criteria.where("taskName").regex(taskName, "i"));
        }

        // 任务类型精确匹配
        if (taskType != null && !taskType.isEmpty()) {
            criteriaList.add(Criteria.where("taskConfig.taskType").is(taskType));
        }

        // 分组精确匹配
        if (group != null && !group.isEmpty()) {
            criteriaList.add(Criteria.where("group").is(group));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        query.with(pageable);
        return reactiveMongoTemplate.find(query, TaskInfoMetadata.class)
                .collectList()
                .zipWith(reactiveMongoTemplate.count(Query.of(query).limit(-1).skip(-1), TaskInfoMetadata.class))
                .map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
    }
}
