package com.baidu.duhome.cluster.task;

import com.baidu.duhome.bean.ClusterNodeInfo;
import com.baidu.duhome.bean.NodeInfo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;



@Component
public class DefaultWeightCalculation implements WeightCalculationStrategy{


    /**
     * 计算节点权重，按照给定的基础权重和cpu以及内存进行统筹计算
     * @param nodeInfos 节点信息
     * @param cpuWeight 给定的cpu基础权重
     * @param memWeight 给定的内存基础权重
     * @return 计算后的每个节点权重值
     */
    @Override
    public NodeWeight calculateWeights(Map<String, NodeInfo> nodeInfos, int cpuWeight, int memWeight) {
        cpuWeight = Math.max(cpuWeight, 1);
        memWeight = Math.max(memWeight, 1);

        long allCpu = nodeInfos.values().stream().mapToLong(entry -> entry.getClusterNodeInfo().getCpu().getProcessors())
                .sum();

        long allMemory = nodeInfos.values().stream().mapToLong(entry -> entry.getClusterNodeInfo().getMemory().getTotal())
                .sum();

        // 1. 计算每个节点的权重
        Map<String, BigDecimal> nodeWeights = new HashMap<>();
        BigDecimal totalWeight = new BigDecimal(0);

        for (Map.Entry<String, NodeInfo> entry : nodeInfos.entrySet()) {
            String nodeId = entry.getKey();
            ClusterNodeInfo nodeInfo = entry.getValue().getClusterNodeInfo();

            long nodeMemory = nodeInfo.getMemory().getTotal();
            int nodeCpu = nodeInfo.getCpu().getProcessors();

            BigDecimal m = BigDecimal.valueOf(nodeMemory)
                    .divide(BigDecimal.valueOf(allMemory), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(memWeight));

            BigDecimal c = BigDecimal.valueOf(nodeCpu)
                    .divide(BigDecimal.valueOf(allCpu), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(cpuWeight));


            nodeWeights.put(nodeId, m.add(c));
            totalWeight = totalWeight.add(m.add(c));
        }
        return new NodeWeight(totalWeight, nodeWeights);
    }

}
