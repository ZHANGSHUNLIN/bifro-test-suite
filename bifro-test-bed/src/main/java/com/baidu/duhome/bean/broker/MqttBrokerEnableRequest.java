package com.baidu.duhome.bean.broker;

import lombok.Data;
import org.apache.commons.lang3.RandomStringUtils;

@Data
public class MqttBrokerEnableRequest {

    private Boolean enabled;
}