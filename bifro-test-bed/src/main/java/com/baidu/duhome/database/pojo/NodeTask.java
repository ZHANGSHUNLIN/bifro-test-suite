package com.baidu.duhome.database.pojo;

import com.baidu.iot.test.suite.worker.TaskConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "node_task")
public class NodeTask {

    @Id
    private String id;

    private String taskId;

    private String nodeId;

    private String nodeName;

    private TaskConfig taskConfig;
}
