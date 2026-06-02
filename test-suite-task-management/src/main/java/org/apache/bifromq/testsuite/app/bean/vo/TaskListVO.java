/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.app.bean.vo;

import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.WillConfig;
import org.apache.bifromq.testsuite.client.AuthType;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import io.netty.handler.codec.mqtt.MqttQoS;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class TaskListVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    
    List<MqttBroker> brokers;
    private String id;
    private String taskName;
    private LocalDateTime createTime;
    private TaskStage taskWorkStage;
    
    private String taskId;
    private String nodeId;
    private String group;
    private TaskConfig.TaskType taskType;
    private String protocol;
    private int port;
    private String username;
    private boolean cleanSession;
    private int keepAliveInSec;
    private int connectTimeoutInMs;
    private int reconnectMaxAttempts;
    private int reconnectIntervalInMs;
    
    private int totalClientCount;
    private int fanOut;
    private int fanIn;
    
    private String topic;
    private MqttQoS qos;
    private boolean fixedTopic;
    private boolean isWildcard;
    private int messageSize;
    private double publishRate;
    private boolean retain;
    private boolean isMqtt5;
    
    private AuthType authType;
    private boolean isEmptyClientId;
    private long expiryIntervalInSec;
    
    private int connectRate;
    private int disconnectRate;
    private String[] lifecycleActions;
    private Map<String, Object> lifecycleActionsConfig;
    
    private WillConfig willConfig;
    
    private int stressDurationInSec;
    private int stageTimeoutInSec;
    private int delayAfterStageInSec;
    
    private int thingIdStartAt;

    public static TaskListVO fromTaskConfig(TaskInfoMetadata taskInfoMetadata) {
        TaskListVO vo = new TaskListVO();
        BeanUtils.copyProperties(taskInfoMetadata.getTaskConfig(), vo);
        vo.setBrokers(taskInfoMetadata.getBrokers());
        vo.setId(taskInfoMetadata.getTaskId());
        vo.setTaskName(taskInfoMetadata.getTaskName());
        
        
        LocalDateTime createTime = taskInfoMetadata.getCreateTime();
        if (createTime == null && taskInfoMetadata.getStartTime() != null) {
            createTime = taskInfoMetadata.getStartTime();
        }
        vo.setCreateTime(createTime);
        vo.setGroup(taskInfoMetadata.getGroup());
        return vo;
    }

    
    public Long getCreateTimeMs() {
        return createTime != null
            ? createTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            : null;
    }

    
    public String getStatus() {
        return taskWorkStage != null ? taskWorkStage.name() : null;
    }
}
