package com.baidu.iot.test.suite;

import lombok.Getter;

public enum ShareDataAddr {

    CLUSTER_TASK_CONFIGS("cluster-task-configs"),
    NODE_TASK_CONFIGS("node-task-configs"),
    FINISH_NODE_TASKS("finish-node-tasks"),
    CLUSTER_NODE_INFO("cluster-node-info"),
    BROKER_MAP_NAME("broker-map"),
    TASK_METADATA("task-metadata"),
    BROKER_TASK_MAPPING("broker-task-mapping");

    @Getter
    private final String addr;

    ShareDataAddr(String addr) {
        this.addr = addr;
    }
}
