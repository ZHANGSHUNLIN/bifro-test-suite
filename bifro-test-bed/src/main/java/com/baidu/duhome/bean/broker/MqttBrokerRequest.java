package com.baidu.duhome.bean.broker;

import lombok.Data;
import org.apache.commons.lang3.RandomStringUtils;

@Data
public class MqttBrokerRequest {

    private String brokerId = RandomStringUtils.secure().next(8, true, true);

    private String name;

    private String host;

    private int port;

    private String description;

    private String group; // 分组/项目名称

    private Boolean enabled;
}
