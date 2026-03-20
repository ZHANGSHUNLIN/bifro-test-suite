package com.baidu.iot.test.suite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShareDataAddrTest {

    @Test
    void testEnumValues() {
        ShareDataAddr[] values = ShareDataAddr.values();

        assertThat(values).hasSize(7);
    }

    @Test
    void testClusterTaskConfigs() {
        assertThat(ShareDataAddr.CLUSTER_TASK_CONFIGS.getAddr())
                .isEqualTo("cluster-task-configs");
    }

    @Test
    void testNodeTaskConfigs() {
        assertThat(ShareDataAddr.NODE_TASK_CONFIGS.getAddr())
                .isEqualTo("node-task-configs");
    }

    @Test
    void testFinishNodeTasks() {
        assertThat(ShareDataAddr.FINISH_NODE_TASKS.getAddr())
                .isEqualTo("finish-node-tasks");
    }

    @Test
    void testClusterNodeInfo() {
        assertThat(ShareDataAddr.CLUSTER_NODE_INFO.getAddr())
                .isEqualTo("cluster-node-info");
    }

    @Test
    void testBrokerMapName() {
        assertThat(ShareDataAddr.BROKER_MAP_NAME.getAddr())
                .isEqualTo("broker-map");
    }

    @Test
    void testTaskMetadata() {
        assertThat(ShareDataAddr.TASK_METADATA.getAddr())
                .isEqualTo("task-metadata");
    }

    @Test
    void testBrokerTaskMapping() {
        assertThat(ShareDataAddr.BROKER_TASK_MAPPING.getAddr())
                .isEqualTo("broker-task-mapping");
    }

    @Test
    void testValueOf() {
        assertThat(ShareDataAddr.valueOf("CLUSTER_TASK_CONFIGS"))
                .isEqualTo(ShareDataAddr.CLUSTER_TASK_CONFIGS);
        assertThat(ShareDataAddr.valueOf("NODE_TASK_CONFIGS"))
                .isEqualTo(ShareDataAddr.NODE_TASK_CONFIGS);
        assertThat(ShareDataAddr.valueOf("BROKER_MAP_NAME"))
                .isEqualTo(ShareDataAddr.BROKER_MAP_NAME);
    }

    @Test
    void testToString() {
        assertThat(ShareDataAddr.CLUSTER_TASK_CONFIGS.toString())
                .isEqualTo("CLUSTER_TASK_CONFIGS");
    }
}
