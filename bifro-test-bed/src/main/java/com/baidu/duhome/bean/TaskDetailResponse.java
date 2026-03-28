package com.baidu.duhome.bean;
// ================== 响应对象定义 ==================

import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.iot.test.suite.worker.TaskConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDetailResponse {
    private boolean success;
    private String message;
    private String taskId;
    private String taskName;
    private String group;
    private TaskConfig mainTask;
    List<MqttBroker> brokers;
    private Map<String, TaskConfig> subTasks;
    private TaskStatistics statistics;
    private Long timestamp = System.currentTimeMillis();

    public static TaskDetailResponse error(String message) {
        TaskDetailResponse response = new TaskDetailResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}