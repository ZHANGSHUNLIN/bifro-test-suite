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

import java.time.Instant;
import org.apache.bifromq.testsuite.app.bean.ClusterNodeInfo;
import org.apache.bifromq.testsuite.app.bean.vo.NodeListVO;
import org.apache.bifromq.testsuite.app.cluster.member.MemberInfo;
import org.apache.bifromq.testsuite.app.cluster.member.MemberInfoView;
import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemberInfoTest {

    private MemberInfo memberInfo;

    @BeforeEach
    void setUp() {
        memberInfo = MemberInfo.builder()
            .id("test-node-1")
            .name("Test Node")
            .host("test-host")
            .role(NodeRole.WORKER)
            .registeredAt(Instant.now())
            .build();
    }

    @Test
    void equals_givenSameId_shouldBeEqual() {
        
        MemberInfo other = MemberInfo.builder()
            .id("test-node-1")
            .name("Other Name")
            .build();

        
        assertThat(memberInfo).isEqualTo(other);
    }

    @Test
    void equals_givenDifferentId_shouldNotBeEqual() {
        
        MemberInfo other = MemberInfo.builder()
            .id("different-id")
            .name("Test Node")
            .build();

        
        assertThat(memberInfo).isNotEqualTo(other);
    }

    @Test
    void hashCode_givenSameId_shouldBeEqual() {
        
        MemberInfo other = MemberInfo.builder()
            .id("test-node-1")
            .build();

        
        assertThat(memberInfo.hashCode()).isEqualTo(other.hashCode());
    }

    

    @Test
    void fromMemberInfo_shouldConvertAllFieldsCorrectly() {
        
        ClusterNodeInfo.MemoryInfo memoryInfo = new ClusterNodeInfo.MemoryInfo();
        memoryInfo.setTotal(1024 * 1024 * 1024);
        memoryInfo.setUsed(512 * 1024 * 1024);
        memoryInfo.setFree(512 * 1024 * 1024);

        ClusterNodeInfo.CpuInfo cpuInfo = new ClusterNodeInfo.CpuInfo();
        cpuInfo.setProcessors(4);
        cpuInfo.setLoadAverage(0.75);

        ClusterNodeInfo systemInfo = new ClusterNodeInfo();
        systemInfo.setMemory(memoryInfo);
        systemInfo.setCpu(cpuInfo);
        memberInfo.setSystemInfo(systemInfo);

        String memberId = "test-member-id";

        
        NodeListVO vo = MemberInfoView.toNodeListVO(memberId, memberInfo);

        
        assertThat(vo.getNodeId()).isEqualTo(memberId);
        assertThat(vo.getNodeName()).isEqualTo(memberInfo.getName());
        assertThat(vo.getHost()).isEqualTo(memberInfo.getHost());
        assertThat(vo.getRole()).isEqualTo(NodeRole.WORKER);
        assertThat(vo.isSchedulable()).isFalse();
        assertThat(vo.getMemory()).isEqualTo(memoryInfo);
        assertThat(vo.getCpu()).isEqualTo(cpuInfo);
    }

    @Test
    void fromMemberInfo_givenAliveWorker_shouldBeSchedulable() {
        memberInfo.setRole(NodeRole.WORKER);
        memberInfo.updateHeartbeat();

        NodeListVO vo = MemberInfoView.toNodeListVO("test-member-id", memberInfo);

        assertThat(vo.getRole()).isEqualTo(NodeRole.WORKER);
        assertThat(vo.isSchedulable()).isTrue();
    }

    @Test
    void fromMemberInfo_givenAliveControlNode_shouldNotBeSchedulable() {
        memberInfo.setRole(NodeRole.CONTROL);
        memberInfo.updateHeartbeat();

        NodeListVO vo = MemberInfoView.toNodeListVO("test-member-id", memberInfo);

        assertThat(vo.getRole()).isEqualTo(NodeRole.CONTROL);
        assertThat(vo.isSchedulable()).isFalse();
    }

    @Test
    void fromMemberInfo_givenNullSystemInfo_shouldHandleGracefully() {
        
        memberInfo.setSystemInfo(null);
        String memberId = "test-member-id";

        
        NodeListVO vo = MemberInfoView.toNodeListVO(memberId, memberInfo);

        
        assertThat(vo.getNodeId()).isEqualTo(memberId);
        assertThat(vo.getNodeName()).isEqualTo(memberInfo.getName());
        assertThat(vo.getHost()).isEqualTo(memberInfo.getHost());
        assertThat(vo.getMemory()).isNull();
        assertThat(vo.getCpu()).isNull();
    }

    @Test
    void fromMemberInfo_givenNoSystemInfo_shouldSetBasicFields() {
        
        String memberId = "test-member-id";

        
        NodeListVO vo = MemberInfoView.toNodeListVO(memberId, memberInfo);

        
        assertThat(vo.getNodeId()).isEqualTo(memberId);
    }

    @Test
    void getMemoryUsagePercent_givenNullSystemInfo_shouldReturnZero() {
        
        memberInfo.setSystemInfo(null);

        
        double usagePercent = memberInfo.getMemoryUsagePercent();

        
        assertThat(usagePercent).isEqualTo(0.0);
    }

    @Test
    void getMemoryUsagePercent_givenValidMemory_shouldCalculateCorrectly() {
        
        ClusterNodeInfo.MemoryInfo memoryInfo = new ClusterNodeInfo.MemoryInfo();
        memoryInfo.setTotal(1024);
        memoryInfo.setUsed(512);
        memoryInfo.setFree(512);

        ClusterNodeInfo systemInfo = new ClusterNodeInfo();
        systemInfo.setMemory(memoryInfo);
        memberInfo.setSystemInfo(systemInfo);

        
        double usagePercent = memberInfo.getMemoryUsagePercent();

        
        assertThat(usagePercent).isEqualTo(50.0);
    }

    @Test
    void getCpuLoadAverage_givenNullSystemInfo_shouldReturnZero() {
        
        memberInfo.setSystemInfo(null);

        
        double loadAverage = memberInfo.getCpuLoadAverage();

        
        assertThat(loadAverage).isEqualTo(0.0);
    }

    @Test
    void getCpuLoadAverage_givenValidCpu_shouldReturnCorrectValue() {
        
        ClusterNodeInfo.CpuInfo cpuInfo = new ClusterNodeInfo.CpuInfo();
        cpuInfo.setLoadAverage(1.5);
        cpuInfo.setProcessors(8);

        ClusterNodeInfo systemInfo = new ClusterNodeInfo();
        systemInfo.setCpu(cpuInfo);
        memberInfo.setSystemInfo(systemInfo);

        
        double loadAverage = memberInfo.getCpuLoadAverage();

        
        assertThat(loadAverage).isEqualTo(1.5);
    }
}
