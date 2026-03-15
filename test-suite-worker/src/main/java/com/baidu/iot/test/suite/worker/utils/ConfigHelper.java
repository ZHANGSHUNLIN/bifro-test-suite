/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.worker.utils;

import com.baidu.iot.test.suite.configs.ClientTaskConfig;
import com.baidu.iot.test.suite.configs.MqttClientConfig;
import com.baidu.iot.test.suite.worker.TaskConfig;

/**
 * Created by mafei01 in 5/7/21 2:47 PM
 */
public class ConfigHelper {

    public static void fillCommonTaskConfig(ClientTaskConfig clientTaskConfig, TaskConfig taskConfig) {
        clientTaskConfig.setTaskId(taskConfig.getTaskId());
        clientTaskConfig.setMessageQos(taskConfig.getQos());
        clientTaskConfig.setMessageSize(taskConfig.getMessageSize());
        clientTaskConfig.setPubIntervalInMs(taskConfig.getPubIntervalInMs());
        clientTaskConfig.setStressDurationInSec(taskConfig.getStressDurationInSec());
        clientTaskConfig.setMqtt5(taskConfig.isMqtt5());
        clientTaskConfig.setAuthType(taskConfig.getAuthType());
        clientTaskConfig.setEmptyClientId(taskConfig.isEmptyClientId());
        clientTaskConfig.setRetain(taskConfig.isRetain());
    }

    public static void fillCommonMqttConfig(MqttClientConfig mqttClientConfig, TaskConfig taskConfig) {
        mqttClientConfig.setKeepAliveInSec(taskConfig.getKeepAliveInSec());
        mqttClientConfig.setAckTimeoutInSec(taskConfig.getAckTimeoutInSec());
        mqttClientConfig.setReconnectMaxAttempts(taskConfig.getReconnectMaxAttempts());
        mqttClientConfig.setReconnectIntervalInMs(taskConfig.getReconnectIntervalInMs());
        mqttClientConfig.setMaxInflightQueue(taskConfig.getMaxInflightQueue());
        mqttClientConfig.setUsername(taskConfig.getUsername());
        mqttClientConfig.setPassword(taskConfig.getPassword());
        mqttClientConfig.setTenantId(taskConfig.getTenantId());
        mqttClientConfig.setThingIdStartAt(taskConfig.getThingIdStartAt());
        mqttClientConfig.setEmptyClientId(taskConfig.isEmptyClientId());
        mqttClientConfig.setAuthType(taskConfig.getAuthType());
        mqttClientConfig.setThingIdPrefix(taskConfig.getThingIdPrefix());
        mqttClientConfig.setCleanSession(taskConfig.isCleanSession());
        mqttClientConfig.setExpiryIntervalInSec(taskConfig.getExpiryIntervalInSec());
        mqttClientConfig.setLocalAddress(taskConfig.nextLocalAddress());
        mqttClientConfig.setProtocol(taskConfig.getProtocol());
        mqttClientConfig.setWillConfig(taskConfig.getWillConfig());
        mqttClientConfig.setExceptionEnds(taskConfig.isExceptionEnds());
    }
}
