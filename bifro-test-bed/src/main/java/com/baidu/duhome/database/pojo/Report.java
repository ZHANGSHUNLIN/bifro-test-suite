package com.baidu.duhome.database.pojo;

import com.baidu.iot.test.suite.worker.TaskConfig;
import com.baidu.iot.test.suite.worker.pojo.EventReport;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "event_report")
public class Report extends EventReport {

    @Id
    private String id;

    private String taskId;

    private String nodeId;

    private TaskConfig.TaskType taskType;

    private LocalDateTime createTime;

}
