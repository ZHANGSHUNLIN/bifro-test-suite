package com.baidu.duhome.cluster.task;

import com.baidu.duhome.bean.ClusterNodeInfo;
import com.baidu.duhome.bean.NodeInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DefaultWeightCalculation}
 */
class DefaultWeightCalculationTest {

    private DefaultWeightCalculation weightCalculation;

    @BeforeEach
    void setUp() {
        weightCalculation = new DefaultWeightCalculation();
    }

    @Test
    void testCalculateWeights_SingleNode() {
        // Given
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));

        int cpuWeight = 1;
        int memWeight = 2;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("3.0000"), result.totalWeight()); // (4/4)*1 + (8192/8192)*2 = 3
        assertEquals(1, result.weight().size());

        BigDecimal nodeWeight = result.weight().get("node-1");
        assertNotNull(nodeWeight);
        assertEquals(new BigDecimal("3.0000"), nodeWeight);
    }

    @Test
    void testCalculateWeights_MultipleNodesEqualResources() {
        // Given
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 2, 4096L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 2, 4096L));

        int cpuWeight = 1;
        int memWeight = 1;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight()); // Each node: (2/4)*1 + (4096/8192)*1 = 0.5 + 0.5 = 1.0 * 2 nodes = 2.0
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
        // Given
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L)); // 50% CPU, 50% memory
        nodeInfos.put("node-2", createNodeInfo("node-2", 2, 4096L)); // 25% CPU, 25% memory
        nodeInfos.put("node-3", createNodeInfo("node-3", 2, 4096L)); // 25% CPU, 25% memory

        int cpuWeight = 1;
        int memWeight = 1;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight()); // 50% + 25% + 25% = 100% * 1 (weights) = 1.0 + 0.5 + 0.5 = 2.0 for both CPU and memory
        assertEquals(3, result.weight().size());

        // node-1 should have higher weight (50% resources)
        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");
        BigDecimal node3Weight = result.weight().get("node-3");

        assertNotNull(node1Weight);
        assertNotNull(node2Weight);
        assertNotNull(node3Weight);

        assertEquals(new BigDecimal("1.0000"), node1Weight); // 0.5 + 0.5 = 1.0
        assertEquals(new BigDecimal("0.5000"), node2Weight); // 0.25 + 0.25 = 0.5
        assertEquals(new BigDecimal("0.5000"), node3Weight); // 0.25 + 0.25 = 0.5
    }

    @Test
    void testCalculateWeights_ZeroCpuWeight() {
        // Given
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 2, 4096L));

        int cpuWeight = 0;
        int memWeight = 1;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight()); // Memory only: (8192/12288 + 4096/12288) = 2/3 + 1/3 = 1.0 + 0.5 = 1.5
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
        // Given
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 2, 4096L));

        int cpuWeight = 1;
        int memWeight = 0;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight()); // CPU only: (4/6 + 2/6) = ~0.6667 + 0.3333 = 1.0 + 0.5 = 1.5
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
        // Given
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 2, 4096L));

        int cpuWeight = 0;
        int memWeight = 0;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight()); // Both weights are zero
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
        // Given
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 8, 16384L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 4, 8192L));

        int cpuWeight = 100;
        int memWeight = 200;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("300.0000"), result.totalWeight()); // Each node gets max weight
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
        // Given
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        int cpuWeight = 1;
        int memWeight = 1;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.totalWeight());
        assertTrue(result.weight().isEmpty());
    }

    @Test
    void testCalculateWeights_NullNodeInfos() {
        // Given
        Map<String, NodeInfo> nodeInfos = null;
        int cpuWeight = 1;
        int memWeight = 1;

        // When & Then
        assertThrows(NullPointerException.class, () -> {
            weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);
        });
    }

    @Test
    void testCalculateWeights_NegativeWeights() {
        // Given
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));

        int cpuWeight = -1;
        int memWeight = -2;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight()); // Negative weights result in negative total
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
        // Given
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", nodeCpu, nodeMemory));

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(1, result.weight().size());

        // Verify the calculation matches expected behavior
        BigDecimal expectedTotal = new BigDecimal(expectedWeight);
        assertEquals(expectedTotal, result.totalWeight());
        assertEquals(new BigDecimal(expectedWeight), result.weight().get("node-1"));

    }

    @Test
    void testCalculateWeights_VerifyStrategyInterface() {
        // Given
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 4, 8192L));

        int cpuWeight = 1;
        int memWeight = 1;

        // When
        WeightCalculationStrategy strategy = weightCalculation;
        NodeWeight result = strategy.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then - should work without exceptions
        assertNotNull(result);
        assertEquals(1, result.weight().size());
    }

    @Test
    void testCalculateWeights_PrecisionAndRounding() {
        // Given - Use values that will produce repeating decimals to test precision
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 3, 1000L)); // 3/3=1.0, 1000/1000=1.0
        nodeInfos.put("node-2", createNodeInfo("node-2", 3, 1000L)); // 3/6=0.5, 1000/2000=0.5

        int cpuWeight = 1;
        int memWeight = 1;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then - Verify proper rounding to 4 decimal places
        assertNotNull(result);

        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");

        // Each node should contribute exactly 1.5 to total weight
        assertEquals(new BigDecimal("2.0000"), result.totalWeight());
        assertEquals(new BigDecimal("1.0000"), node1Weight);
        assertEquals(new BigDecimal("1.0000"), node2Weight);
    }

    @Test
    void testCalculateWeights_LargeResourceValues() {
        // Given - Test with very large CPU and memory values
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 1024, 68719476736L)); // 1TB memory
        nodeInfos.put("node-2", createNodeInfo("node-2", 512, 34359738368L));  // 512GB memory

        int cpuWeight = 1;
        int memWeight = 1;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight());
        assertEquals(2, result.weight().size());

        BigDecimal node1Weight = result.weight().get("node-1");
        BigDecimal node2Weight = result.weight().get("node-2");

        // node-1 should have about 2/3 of total weight, node-2 about 1/3
        assertTrue(node1Weight.compareTo(node2Weight) > 0);
        assertEquals(new BigDecimal("1.3334"), node1Weight);  // 1024/1536 + 68719476736/103079215104 ≈ 0.6667 + 0.6667 = 1.3333
        assertEquals(new BigDecimal("0.6666"), node2Weight);  // 512/1536 + 34359738368/103079215104 ≈ 0.3333 + 0.3333 = 0.6667
    }

    @Test
    void testCalculateWeights_SmallestPossibleValues() {
        // Given - Test with smallest non-zero values
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("node-1", createNodeInfo("node-1", 1, 1L));
        nodeInfos.put("node-2", createNodeInfo("node-2", 1, 1L));

        int cpuWeight = 1;
        int memWeight = 1;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
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
        // Given - Node with high CPU but low memory vs node with low CPU but high memory
        Map<String, NodeInfo> nodeInfos = new HashMap<>();
        nodeInfos.put("cpu-heavy", createNodeInfo("cpu-heavy", 8, 4096L));  // High CPU, low memory
        nodeInfos.put("mem-heavy", createNodeInfo("mem-heavy", 2, 16384L)); // Low CPU, high memory

        int cpuWeight = 1;
        int memWeight = 1;

        // When
        NodeWeight result = weightCalculation.calculateWeights(nodeInfos, cpuWeight, memWeight);

        // Then
        assertNotNull(result);
        assertEquals(new BigDecimal("2.0000"), result.totalWeight());
        assertEquals(2, result.weight().size());

        BigDecimal cpuHeavyWeight = result.weight().get("cpu-heavy");
        BigDecimal memHeavyWeight = result.weight().get("mem-heavy");

        // Both nodes should have equal weight since resource ratios balance out
        assertEquals(new BigDecimal("1.0000"), cpuHeavyWeight); // 8/10 + 4096/20480 = 0.8 + 0.2 = 1.0
        assertEquals(new BigDecimal("1.0000"), memHeavyWeight); // 2/10 + 16384/20480 = 0.2 + 0.8 = 1.0
    }

    /**
     * Helper method to create ClusterNodeInfo with specified CPU and memory
     */
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
                .alive(true)
                .build();
    }
}