package com.baidu.duhome.database.service;

import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class TaskInfoMetadataService {

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;


    public TaskInfoMetadata insertTaskInfoMetadata(TaskInfoMetadata taskInfoMetadata) {
        return taskInfoMetadataRepository.insert(taskInfoMetadata);
    }


    public Page<TaskInfoMetadata> findAll(Pageable pageable) {
        return taskInfoMetadataRepository.findAll(pageable);
    }
}
