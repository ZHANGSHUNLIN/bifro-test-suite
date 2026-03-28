package com.baidu.duhome.bean.vo;

import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.WillConfig;
import com.baidu.iot.test.suite.worker.TaskConfig;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务列表视图对象（返回给前端）
 */
@Data
public class TaskListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private String id;

    private String taskName;
    private LocalDateTime createTime;
    private TaskStage taskWorkStage;

    // 基础标识
    private String taskId;
    private String nodeId;
    private String group;
    private TaskConfig.TaskType taskType;
    private String protocol;

    // 连接配置
    List<MqttBroker> brokers;
    private int port;
    private String username;
    // 注意：password 通常不返回给前端，出于安全考虑
    private String tenantId;
    private boolean cleanSession;
    private int keepAliveInSec;
    private int connectTimeoutInMs;
    private int reconnectMaxAttempts;
    private int reconnectIntervalInMs;

    // 客户端规模
    private int totalClientCount;
    private int fanOut;
    private int fanIn;

    // 消息配置
    private String topic;
    private MqttQoS qos;
    private boolean fixedTopic;
    private boolean isWildcard;
    private int messageSize;
    private int pubIntervalInMs;
    private boolean retain;
    private boolean isMqtt5;

    // 认证方式
    private String authType;
    private boolean isEmptyClientId;
    private long expiryIntervalInSec;

    // 行为控制
    private boolean pubOnly;
    private boolean subOnly;
    private int connectRate;
    private int disconnectRate;
    private String[] lifecycleActions;
    private Map<String, Object> lifecycleActionsConfig;

    // 遗嘱消息（Will Message）
    private WillConfig willConfig;

    // 任务时长与超时
    private int stressDurationInSec;
    private int stageTimeoutInSec;
    private int delayAfterReadyInSec;
    private int skipStatsPeriod;

    // thingId 相关
    private int thingIdStartAt;
    private String thingIdPrefix;

    // 是否在异常时结束会话
    private boolean exceptionEnds;


    public static TaskListVO fromTaskConfig(TaskInfoMetadata taskInfoMetadata) {
        TaskListVO vo = new TaskListVO();
        BeanUtils.copyProperties(taskInfoMetadata.getTaskConfig(), vo);
        vo.setBrokers(taskInfoMetadata.getBrokers());
        vo.setId(taskInfoMetadata.getTaskId());
        vo.setTaskName(taskInfoMetadata.getTaskName());
        vo.setCreateTime(taskInfoMetadata.getCreateTime());
        vo.setGroup(taskInfoMetadata.getGroup());
        return vo;
    }
}