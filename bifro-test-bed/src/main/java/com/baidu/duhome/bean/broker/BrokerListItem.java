package com.baidu.duhome.bean.broker;

import lombok.Data;

import java.time.Instant;

@Data
public class BrokerListItem {
    private String id;
    private String brokerId;
    private String name;
    private String host;
    private int port;
    private String description;
    private String group;
    private Boolean enabled;
    private BrokerStatus status;
    private Instant createdAt;
    private Instant updatedAt;

}
