package com.baidu.duhome.database.service;

import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TaskInfoMetadataService {

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @Resource
    private MongoTemplate mongoTemplate;


    public TaskInfoMetadata insertTaskInfoMetadata(TaskInfoMetadata taskInfoMetadata) {
        return taskInfoMetadataRepository.insert(taskInfoMetadata);
    }


    public Page<TaskInfoMetadata> findAll(Pageable pageable) {
        return taskInfoMetadataRepository.findAll(pageable);
    }

    /**
     * 根据任务名称和任务类型分页查询
     */
    public Page<TaskInfoMetadata> findByFilters(String taskName, String taskType, Pageable pageable) {
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

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        query.with(pageable);
        List<TaskInfoMetadata> list = mongoTemplate.find(query, TaskInfoMetadata.class);
        return PageableExecutionUtils.getPage(list, pageable,
            () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), TaskInfoMetadata.class));
    }
}
