/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.models;

import io.netty.handler.codec.mqtt.MqttQoS;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created by mafei01 in 3/10/21 3:25 PM
 */
@Data
@AllArgsConstructor
public class TopicFilter {

    private String name;
    private MqttQoS qos;
}
