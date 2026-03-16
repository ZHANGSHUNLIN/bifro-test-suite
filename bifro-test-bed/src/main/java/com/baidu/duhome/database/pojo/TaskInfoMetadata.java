package com.baidu.duhome.database.pojo;

import com.baidu.iot.test.suite.worker.TaskConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "task_info_metadata")
public class TaskInfoMetadata implements Serializable {

    @Id
    private String taskId;

    private String taskName;

    private String taskType;

    private TaskConfig taskConfig;

    private List<MqttBroker> brokers;

    private LocalDateTime createTime;

}
