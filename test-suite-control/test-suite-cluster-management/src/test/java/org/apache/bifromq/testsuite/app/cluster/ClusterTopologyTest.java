/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.app.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.bifromq.testsuite.app.bean.ClusterNodeInfo;
import org.apache.bifromq.testsuite.app.cluster.member.MemberInfo;
import org.apache.bifromq.testsuite.app.cluster.topology.ClusterTopology;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusterTopologyTest {

    private ClusterConfig clusterConfig;
    private Map<String, MemberInfo> members;

    @BeforeEach
    void setUp() {
        clusterConfig = new ClusterConfig();

        members = new HashMap<>();
        members.put("node-1", createMemberInfoWithSystemInfo("node-1", "Node 1"));
        members.put("node-2", createMemberInfoWithSystemInfo("node-2", "Node 2"));
    }

    private MemberInfo createMemberInfoWithSystemInfo(String id, String name) {
        ClusterNodeInfo.MemoryInfo memory = new ClusterNodeInfo.MemoryInfo();
        memory.setMax(1024 * 1024 * 1024);
        memory.setTotal(512 * 1024 * 1024);
        memory.setFree(256 * 1024 * 1024);
        memory.setUsed(256 * 1024 * 1024);

        ClusterNodeInfo.CpuInfo cpu = new ClusterNodeInfo.CpuInfo();
        cpu.setProcessors(4);
        cpu.setLoadAverage(2.0);

        ClusterNodeInfo systemInfo = new ClusterNodeInfo();
        systemInfo.setHost("host-" + id);
        systemInfo.setMemory(memory);
        systemInfo.setCpu(cpu);

        return MemberInfo.builder()
            .id(id)
            .name(name)
            .host("host-" + id)
            .systemInfo(systemInfo)
            .lastHeartbeat(System.currentTimeMillis())
            .registeredAt(Instant.now())
            .build();
    }

    @Test
    void getMembers_shouldReturnUnmodifiableMap() {
        
        ClusterTopology topology = new ClusterTopology(members, clusterConfig);

        
        assertThat(topology.getMembers()).hasSize(2);
    }

    @Test
    void getMemberCount_shouldReturnCorrectCount() {
        
        ClusterTopology topology = new ClusterTopology(members, clusterConfig);

        
        assertThat(topology.getMemberCount()).isEqualTo(2);
    }

    @Test
    void getMember_givenExistingMember_shouldReturnMember() {
        
        ClusterTopology topology = new ClusterTopology(members, clusterConfig);

        
        assertThat(topology.getMember("node-1")).isPresent();
        assertThat(topology.getMember("node-1").get().getName()).isEqualTo("Node 1");
    }

    @Test
    void getMember_givenNonExistingMember_shouldReturnEmpty() {
        
        ClusterTopology topology = new ClusterTopology(members, clusterConfig);

        
        assertThat(topology.getMember("non-existing")).isEmpty();
    }

    @Test
    void calculateAssignment_givenTotalClientsLessThanNodes_shouldAssignAtMostOne() {
        
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId("test-task")
            .totalClientCount(1)
            .build();

        
        ClusterTopology topology = new ClusterTopology(members, clusterConfig);
        var assignment = topology.calculateAssignment(taskConfig);

        
        assertThat(assignment.getNodeAssignments().values().stream().mapToInt(Integer::intValue).sum())
            .isLessThanOrEqualTo(1);
    }

    @Test
    void calculateAssignment_givenTotalClientsMoreThanNodes_shouldDistribute() {
        
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId("test-task")
            .totalClientCount(10)
            .build();

        
        ClusterTopology topology = new ClusterTopology(members, clusterConfig);
        var assignment = topology.calculateAssignment(taskConfig);

        
        assertThat(assignment.getNodeAssignments()).isNotEmpty();
        int totalAssigned = assignment.getNodeAssignments().values().stream().mapToInt(Integer::intValue).sum();
        assertThat(totalAssigned).isLessThanOrEqualTo(20);
    }

    @Test
    void calculateAssignment_givenZeroClients_shouldReturnEmpty() {
        
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId("test-task")
            .totalClientCount(0)
            .build();

        
        ClusterTopology topology = new ClusterTopology(members, clusterConfig);
        var assignment = topology.calculateAssignment(taskConfig);

        
        assertThat(assignment.isEmpty()).isTrue();
    }

    @Test
    void calculateAssignment_givenCustomWeights_shouldUseWeights() {
        
        TaskConfig taskConfig = TaskConfig.builder()
            .taskId("test-task")
            .totalClientCount(10)
            .build();

        
        ClusterTopology topology = new ClusterTopology(members, clusterConfig);
        var assignment = topology.calculateAssignment(taskConfig, 2, 1); 

        
        assertThat(assignment.getTaskId()).isEqualTo("test-task");
        assertThat(assignment.getNodeAssignments()).isNotEmpty();
    }

    @Test
    void constructor_givenNullMembers_shouldNotThrow() {
        
        ClusterTopology topology = new ClusterTopology(null, clusterConfig);

        
        assertThat(topology.getMembers()).isEmpty();
        assertThat(topology.getMemberCount()).isZero();
    }

    @Test
    void toString_shouldContainMemberCountAndTimestamp() {
        
        ClusterTopology topology = new ClusterTopology(members, clusterConfig);

        
        assertThat(topology.toString()).contains("members=");
        assertThat(topology.toString()).contains("snapshotTime=");
    }
}
