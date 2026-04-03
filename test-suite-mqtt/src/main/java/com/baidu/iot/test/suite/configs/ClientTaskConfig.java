
package com.baidu.iot.test.suite.configs;

import io.netty.handler.codec.mqtt.MqttQoS;

import lombok.Data;

import java.util.Set;

import com.baidu.iot.test.suite.constants.ClientTaskType;
import com.baidu.iot.test.suite.models.TopicFilter;

/**
 */
@Data
public class ClientTaskConfig {
    private String taskId;
    private String authType;
    private ClientTaskType type;
    private String pubTopic;
    private MqttQoS messageQos;
    private int messageSize;
    private Set<TopicFilter> topicFilters;
    private int pubIntervalInMs;
    private int stressDurationInSec;
    private int stageTimeoutInSec;
    private boolean retain;
    private boolean isMqtt5;
    private boolean isEmptyClientId = false;
    private boolean sendLatencyEvent = false;
    private boolean randomPublishing = false;
}
