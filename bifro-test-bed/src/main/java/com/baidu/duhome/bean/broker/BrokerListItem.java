package com.baidu.duhome.bean.broker;

import lombok.Data;

import java.time.Instant;

@Data
public class BrokerListItem {
    private String Id;
    private String brokerId;
    private String name;
    private String host;
    private int port;
    private String description;
    private boolean enabled;
    private BrokerStatus status;
    private boolean sslEnabled;
    private Instant lastHealthCheck;
    private Instant createdAt;
    private Instant updatedAt;

}