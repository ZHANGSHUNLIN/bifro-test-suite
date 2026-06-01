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

package org.apache.bifromq.testsuite.app.cluster.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import org.apache.bifromq.testsuite.app.bean.ClusterNodeInfo;
import org.apache.bifromq.testsuite.app.bean.NodeInfo;
import org.springframework.stereotype.Component;

@Component
public class DefaultWeightCalculation implements WeightCalculationStrategy {
    @Override
    public NodeWeight calculateWeights(Map<String, NodeInfo> nodeInfos, int cpuWeight, int memWeight) {
        cpuWeight = Math.max(cpuWeight, 1);
        memWeight = Math.max(memWeight, 1);

        long allCpu =
            nodeInfos.values().stream().mapToLong(entry -> entry.getClusterNodeInfo().getCpu().getProcessors())
                .sum();

        long allMemory =
            nodeInfos.values().stream().mapToLong(entry -> entry.getClusterNodeInfo().getMemory().getTotal())
                .sum();
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
