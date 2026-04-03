
package com.baidu.iot.test.suite.models;

import io.netty.handler.codec.mqtt.MqttQoS;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 */
@Data
@AllArgsConstructor
public class TopicFilter {

    private String name;
    private MqttQoS qos;
}
