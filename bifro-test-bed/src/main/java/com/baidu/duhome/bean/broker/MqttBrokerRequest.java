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

    private Boolean enabled;

    private String username;
    private String password;

    private Boolean sslEnabled = false;

    private Integer keepAliveSeconds = 60;

    private Integer connectionTimeoutSeconds = 30;

    private Integer maxConnections = 100;
}