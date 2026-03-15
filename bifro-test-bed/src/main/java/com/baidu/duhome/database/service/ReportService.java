package com.baidu.duhome.database.service;

import com.baidu.duhome.database.pojo.Report;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.repository.ReportRepository;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ReportService {

    @Resource
    private ReportRepository reportRepository;

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    public Page<Report> taskReport(String taskId, String nodeId, Integer pageNum, Integer pageSize) {
        Optional<TaskInfoMetadata> metadata = taskInfoMetadataRepository.findById(taskId);
        if (metadata.isEmpty()) {
            throw new RuntimeException("Task not found");
        }
        // Pageable 是从 0 开始计页的，所以前端传来的页码需要减 1
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return reportRepository.findByTaskIdAndNodeIdOrderByCreateTimeDesc(metadata.get().getTaskConfig().getTaskId(), nodeId, pageable);
    }
}
