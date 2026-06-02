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

package org.apache.bifromq.testsuite.app.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.bifromq.testsuite.app.bean.ClusterNodeInfo;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.apache.bifromq.testsuite.app.cluster.core.DefaultWeightCalculation;
import org.apache.bifromq.testsuite.app.cluster.core.NodeWeight;
import org.apache.bifromq.testsuite.app.cluster.core.WeightCalculationStrategy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DefaultWeightCalculationTest {

    private DefaultWeightCalculation weightCalculation;

    @BeforeEach
    void setUp() {
        weightCalculation = new DefaultWeightCalculation();
    }

    @Test
    void testCalculateWeights_SingleNode() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));

        int cpuWeight = 1;
        int memWeight = 2;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(new BigDecimal("3.0000"), result.totalWeight()); 
        assertEquals(1, result.weight().size());

        BigDecimal nodeWeight = result.weight().get("node-1");
        assertNotNull(nodeWeight);
        assertEquals(new BigDecimal("3.0000"), nodeWeight);
    }

    @Test
    void testCalculateWeights_MultipleNodesEqualResources() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 2, 4096L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 2, 4096L));

        int cpuWeight = 1;
        int memWeight = 1;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"),
            result.totalWeight()); 
        assertEquals(2, result.weight().size());

        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");

        assertNotNull(node1Weight);
        assertNotNull(node2Weight);
        assertEquals(new BigDecimal("1.0000"), node1Weight);
        assertEquals(new BigDecimal("1.0000"), node2Weight);
    }

    @Test
    void testCalculateWeights_MultipleNodesDifferentResources() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L)); 
        nodeInfos.put("node-2", createNodeInfo("node-2", 2, 4096L)); 
        nodeInfos.put("node-3", createNodeInfo("node-3", 2, 4096L)); 

        int cpuWeight = 1;
        int memWeight = 1;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"),
            result.totalWeight()); 
        assertEquals(3, result.weight().size());

        
        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");
        BigDecimal node3Weight = result.weight().get("node-3");

        assertNotNull(node1Weight);
        assertNotNull(node2Weight);
        assertNotNull(node3Weight);

        assertEquals(new BigDecimal("1.0000"), node1Weight); 
        assertEquals(new BigDecimal("0.5000"), node2Weight); 
        assertEquals(new BigDecimal("0.5000"), node3Weight); 
    }

    @Test
    void testCalculateWeights_ZeroCpuWeight() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 2, 4096L));

        int cpuWeight = 0;
        int memWeight = 1;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"),
            result.totalWeight()); 
        assertEquals(2, result.weight().size());

        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");

        assertNotNull(node1Weight);
        assertNotNull(node2Weight);
        assertEquals(new BigDecimal("1.3334"), node1Weight);
        assertEquals(new BigDecimal("0.6666"), node2Weight);
    }

    @Test
    void testCalculateWeights_ZeroMemWeight() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 2, 4096L));

        int cpuWeight = 1;
        int memWeight = 0;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"),
            result.totalWeight()); 
        assertEquals(2, result.weight().size());

        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");

        assertNotNull(node1Weight);
        assertNotNull(node2Weight);
        assertEquals(new BigDecimal("1.3334"), node1Weight);
        assertEquals(new BigDecimal("0.6666"), node2Weight);
    }

    @Test
    void testCalculateWeights_ZeroWeights() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 2, 4096L));

        int cpuWeight = 0;
        int memWeight = 0;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight()); 
        assertEquals(2, result.weight().size());

        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");

        assertNotNull(node1Weight);
        assertNotNull(node2Weight);
        assertEquals(new BigDecimal("1.3334"), node1Weight);
        assertEquals(new BigDecimal("0.6666"), node2Weight);
    }

    @Test
    void testCalculateWeights_HighWeightValues() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 8, 16384L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 4, 8192L));

        int cpuWeight = 100;
        int memWeight = 200;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(new BigDecimal("300.0000"), result.totalWeight()); 
        assertEquals(2, result.weight().size());

        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");

        assertNotNull(node1Weight);
        assertNotNull(node2Weight);

        assertEquals(new BigDecimal("200.0100"), node1Weight);
        assertEquals(new BigDecimal("99.9900"), node2Weight);
    }

    @Test
    void testCalculateWeights_EmptyNodeInfos() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        int cpuWeight = 1;
        int memWeight = 1;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.totalWeight());
        assertTrue(result.weight().isEmpty());
    }

    @Test
    void testCalculateWeights_NullNodeInfos() {
        
        Map<String, NodeInfo> nodeInfos = null;
        int cpuWeight = 1;
        int memWeight = 1;

        
        assertThrows(NullPointerException.class, () -> {
            weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);
        });
    }

    @Test
    void testCalculateWeights_NegativeWeights() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));

        int cpuWeight = -1;
        int memWeight = -2;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight()); 
        assertEquals(1, result.weight().size());

        BigDecimal nodeWeight = result.weight().get("node-1");
        assertNotNull(nodeWeight);
        BigDecimal node1Weight = result.weight().get("node-1");
        assertEquals(new BigDecimal("2.0000"), node1Weight);
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1, 4, 8192 , 2.0000",
        "2, 1, 8, 16384 , 3.0000",
        "1, 2, 4, 8192 , 3.0000",
        "0, 1, 4, 8192 , 2.0000 ",
        "1, 0, 4, 8192 , 2.0000 "
    })
    void testCalculateWeights_DifferentWeightCombinations(int cpuWeight, int memWeight,
                                                          int nodeCpu, long nodeMemory,
                                                          String expectedWeight) {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", nodeCpu, nodeMemory));

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(1, result.weight().size());

        
        BigDecimal expectedTotal = new BigDecimal(expectedWeight);
        assertEquals(expectedTotal, result.totalWeight());
        assertEquals(new BigDecimal(expectedWeight), result.weight().get("node-1"));

    }

    @Test
    void testCalculateWeights_VerifyStrategyInterface() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));

        int cpuWeight = 1;
        int memWeight = 1;

        
        WeightCalculationStrategy strategy = weightCalculation;
        NodeWeight result = strategy.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(1, result.weight().size());
    }

    @Test
    void testCalculateWeights_PrecisionAndRounding() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 3, 1000L)); 
        nodeInfos.put("node-2", createNodeInfo("node-2", 3, 1000L)); 

        int cpuWeight = 1;
        int memWeight = 1;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);

        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");

        
        assertEquals(new BigDecimal("2.0000"), result.totalWeight());
        assertEquals(new BigDecimal("1.0000"), node1Weight);
        assertEquals(new BigDecimal("1.0000"), node2Weight);
    }

    @Test
    void testCalculateWeights_LargeResourceValues() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 1024, 68719476736L)); 
        nodeInfos.put("node-2", createNodeInfo("node-2", 512, 34359738368L));  

        int cpuWeight = 1;
        int memWeight = 1;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight());
        assertEquals(2, result.weight().size());

        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");

        
        assertTrue(node1Weight.compareTo(node2Weight) > 0);
        assertEquals(new BigDecimal("1.3334"),
            node1Weight);  
        assertEquals(new BigDecimal("0.6666"),
            node2Weight);  
    }

    @Test
    void testCalculateWeights_SmallestPossibleValues() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 1, 1L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 1, 1L));

        int cpuWeight = 1;
        int memWeight = 1;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight());
        assertEquals(2, result.weight().size());

        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");

        assertEquals(new BigDecimal("1.0000"), node1Weight);
        assertEquals(new BigDecimal("1.0000"), node2Weight);
    }

    @Test
    void testCalculateWeights_MixedResourceRatios() {
        
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("cpu-heavy", createNodeInfo("cpu-heavy", 8, 4096L));  
        nodeInfos.put("mem-heavy", createNodeInfo("mem-heavy", 2, 16384L)); 

        int cpuWeight = 1;
        int memWeight = 1;

        
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight());
        assertEquals(2, result.weight().size());

        BigDecimal cpuHeavyWeight = result.weight().get("cpu-heavy");
        BigDecimal memHeavyWeight = result.weight().get("mem-heavy");

        
        assertEquals(new BigDecimal("1.0000"), cpuHeavyWeight); 
        assertEquals(new BigDecimal("1.0000"), memHeavyWeight); 
    }

    
    private NodeInfo createNodeInfo(String nodeId, int processors, long memoryTotal) {
        ClusterNodeInfo.CpuInfo cpuInfo = ClusterNodeInfo.CpuInfo.builder()
            .processors(processors)
            .loadAverage(0.5)
            .build();

        ClusterNodeInfo.MemoryInfo memoryInfo = ClusterNodeInfo.MemoryInfo.builder()
            .total(memoryTotal)
            .max(memoryTotal * 2)
            .used(memoryTotal / 2)
            .free(memoryTotal / 2)
            .build();

        return NodeInfo.builder()
            .clusterNodeInfo(ClusterNodeInfo.builder()
                .nodeId(nodeId)
                .cpu(cpuInfo)
                .memory(memoryInfo)
                .host("localhost")
                .timestamp(System.currentTimeMillis())
                .build())
            .build();
    }
}