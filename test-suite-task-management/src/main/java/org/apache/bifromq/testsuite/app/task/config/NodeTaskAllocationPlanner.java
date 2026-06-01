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

package org.apache.bifromq.testsuite.app.task.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.type.PubSubClientCountPlanner;
import org.apache.bifromq.testsuite.worker.type.PubSubClientCountSpec;

public final class NodeTaskAllocationPlanner {

    private NodeTaskAllocationPlanner() {
    }

    public static Map<String, TaskConfig> toNodeTaskConfigs(
        TaskConfig mainTaskConfig, List<NodeTaskAssignment> assignments) {
        int totalClientCount = mainTaskConfig.getTotalClientCount();
        PubSubClientCountSpec clientCountSpec = PubSubClientCountSpec.fromTaskConfig(mainTaskConfig);
        int globalPubCount = PubSubClientCountPlanner.expectedPubCount(clientCountSpec);
        int globalSubCount = PubSubClientCountPlanner.expectedSubCount(clientCountSpec);
        List<NodePubSubCount> nodePubSubCounts = new ArrayList<>(assignments.size());
        int pubRemaining = globalPubCount;
        int subRemaining = globalSubCount;
        int nodesLeft = assignments.size();

        for (int nodeIndex = 0; nodeIndex < assignments.size(); nodeIndex++) {
            NodeTaskAssignment assignment = assignments.get(nodeIndex);
            int nodeClientCount = assignment.clientCount();
            nodesLeft--;
            NodePubSubCount nodePubSubCount = nextNodePubSubCount(
                nodeClientCount, totalClientCount, globalPubCount, globalSubCount,
                pubRemaining, subRemaining, nodesLeft);
            nodePubSubCounts.add(nodePubSubCount);
            pubRemaining -= nodePubSubCount.pubCount();
            subRemaining -= nodePubSubCount.subCount();
        }

        List<Double> nodeRatios = assignments.stream()
            .map(assignment -> assignment.clientCount() / (double) totalClientCount)
            .toList();
        List<Double> publishRatios = nodePubSubCounts.stream()
            .map(count -> globalPubCount > 0 ? count.pubCount() / (double) globalPubCount : 0D)
            .toList();
        NodeProfileDataPoints profileDataPoints = NodeProfileDataPoints.from(mainTaskConfig, nodeRatios, publishRatios);

        Map<String, TaskConfig> nodeTaskConfigs = new LinkedHashMap<>();
        int thingIdOffset = 0;

        for (int nodeIndex = 0; nodeIndex < assignments.size(); nodeIndex++) {
            NodeTaskAssignment assignment = assignments.get(nodeIndex);
            int nodeClientCount = assignment.clientCount();
            NodePubSubCount nodePubSubCount = nodePubSubCounts.get(nodeIndex);
            TaskConfig nodeTaskConfig = toNodeTaskConfig(
                mainTaskConfig, assignment.nodeId(), nodeClientCount, nodePubSubCount, profileDataPoints, nodeIndex);
            nodeTaskConfig.setThingIdStartAt(mainTaskConfig.getThingIdStartAt() + thingIdOffset);
            thingIdOffset += nodeClientCount;
            nodeTaskConfigs.put(assignment.nodeId(), nodeTaskConfig);
        }
        return nodeTaskConfigs;
    }

    public static Map<String, TaskConfig> toSingleClientNodeTaskConfigs(
        TaskConfig mainTaskConfig, List<String> nodeIds) {
        int totalClientCount = mainTaskConfig.getTotalClientCount();
        PubSubClientCountSpec clientCountSpec = PubSubClientCountSpec.fromTaskConfig(mainTaskConfig);
        int globalPubCount = PubSubClientCountPlanner.expectedPubCount(clientCountSpec);
        int globalSubCount = PubSubClientCountPlanner.expectedSubCount(clientCountSpec);
        Map<String, TaskConfig> nodeTaskConfigs = new LinkedHashMap<>();
        int thingIdOffset = 0;
        int pubRemaining = globalPubCount;
        int subRemaining = globalSubCount;
        int nodeCount = Math.min(totalClientCount, nodeIds.size());

        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            String nodeId = nodeIds.get(nodeIndex);
            int nodePubCount = pubRemaining > 0 ? 1 : 0;
            int nodeSubCount = nodePubCount == 0 && subRemaining > 0 ? 1 : 0;
            pubRemaining -= nodePubCount;
            subRemaining -= nodeSubCount;
            NodePubSubCount nodePubSubCount = new NodePubSubCount(nodePubCount, nodeSubCount);
            TaskConfig nodeTaskConfig = toNodeTaskConfig(
                mainTaskConfig, nodeId, 1, nodePubSubCount, NodeProfileDataPoints.empty(), nodeIndex);
            nodeTaskConfig.setThingIdStartAt(mainTaskConfig.getThingIdStartAt() + thingIdOffset);
            thingIdOffset++;
            nodeTaskConfigs.put(nodeId, nodeTaskConfig);
        }
        return nodeTaskConfigs;
    }

    private static NodePubSubCount nextNodePubSubCount(
        int nodeClientCount,
        int totalClientCount,
        int globalPubCount,
        int globalSubCount,
        int pubRemaining,
        int subRemaining,
        int nodesLeft) {
        if (nodesLeft == 0) {
            int nodePubCount = Math.min(nodeClientCount, pubRemaining);
            int nodeSubCount = Math.min(nodeClientCount - nodePubCount, subRemaining);
            return new NodePubSubCount(nodePubCount, nodeSubCount);
        }
        int nodePubCount = (int) Math.round((double) nodeClientCount * globalPubCount / totalClientCount);
        nodePubCount = Math.min(nodePubCount, Math.min(nodeClientCount, pubRemaining));
        int nodeSubCount = (int) Math.round((double) nodeClientCount * globalSubCount / totalClientCount);
        nodeSubCount = Math.min(nodeSubCount, Math.min(nodeClientCount - nodePubCount, subRemaining));
        return new NodePubSubCount(nodePubCount, nodeSubCount);
    }

    private static TaskConfig toNodeTaskConfig(
        TaskConfig mainTaskConfig,
        String nodeId,
        int nodeClientCount,
        NodePubSubCount nodePubSubCount,
        NodeProfileDataPoints profileDataPoints,
        int nodeIndex) {
        NodeExecutionConfig executionConfig = NodeExecutionConfig.builder()
            .nodeId(nodeId)
            .nodeClientCount(nodeClientCount)
            .nodePubCount(nodePubSubCount.pubCount())
            .nodeSubCount(nodePubSubCount.subCount())
            .mainTotalClientCount(mainTaskConfig.getTotalClientCount())
            .preScaledConnectDataPoints(profileDataPoints.connectDataPoints(nodeIndex))
            .preScaledDisconnectDataPoints(profileDataPoints.disconnectDataPoints(nodeIndex))
            .preScaledSubscribeDataPoints(profileDataPoints.subscribeDataPoints(nodeIndex))
            .preScaledPublishDataPoints(profileDataPoints.publishDataPoints(nodeIndex))
            .build();
        return NodeTaskConfigMapper.toNodeTaskConfig(mainTaskConfig, executionConfig);
    }

    private static List<List<long[]>> scaleProfileDataPointsConservative(List<long[]> dataPoints,
                                                                         List<Double> ratios) {
        if (dataPoints == null || dataPoints.isEmpty() || ratios == null || ratios.isEmpty()) {
            return List.of();
        }
        int n = ratios.size();
        List<List<long[]>> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new ArrayList<>(dataPoints.size()));
        }

        for (long[] pt : dataPoints) {
            long ts = pt[0];
            long originalQps = Math.max(0, pt[1]);
            if (originalQps == 0) {
                for (int i = 0; i < n; i++) {
                    out.get(i).add(new long[] {ts, 0});
                }
                continue;
            }

            double[] ideals = new double[n];
            long[] assigned = new long[n];
            List<ResidualPart> residuals = new ArrayList<>(n);
            long floorSum = 0;
            for (int i = 0; i < n; i++) {
                ideals[i] = Math.max(0d, originalQps * ratios.get(i));
                assigned[i] = (long) Math.floor(ideals[i]);
                floorSum += assigned[i];
                residuals.add(new ResidualPart(i, ideals[i] - assigned[i]));
            }

            long remainder = originalQps - floorSum;
            if (remainder > 0) {
                residuals.sort(Comparator.comparingDouble((ResidualPart r) -> r.residual).reversed()
                    .thenComparingInt(r -> r.index));
                for (int k = 0; k < remainder; k++) {
                    assigned[residuals.get(k % n).index]++;
                }
            } else if (remainder < 0) {
                residuals.sort(Comparator.comparingDouble((ResidualPart r) -> r.residual)
                    .thenComparingInt(r -> r.index));
                long needRemove = -remainder;
                int idx = 0;
                while (needRemove > 0 && idx < residuals.size()) {
                    int target = residuals.get(idx).index;
                    if (assigned[target] > 0) {
                        assigned[target]--;
                        needRemove--;
                    } else {
                        idx++;
                    }
                }
            }

            for (int i = 0; i < n; i++) {
                out.get(i).add(new long[] {ts, assigned[i]});
            }
        }
        return out;
    }

    private record ResidualPart(int index, double residual) {
    }

    private record NodePubSubCount(int pubCount, int subCount) {
    }

    private record NodeProfileDataPoints(
        List<List<long[]>> connectDataPoints,
        List<List<long[]>> disconnectDataPoints,
        List<List<long[]>> subscribeDataPoints,
        List<List<long[]>> publishDataPoints
    ) {
        private static NodeProfileDataPoints from(
            TaskConfig mainTaskConfig,
            List<Double> nodeRatios,
            List<Double> publishRatios) {
            return new NodeProfileDataPoints(
                scaleProfileDataPointsConservative(mainTaskConfig.getConnectProfileDataPoints(), nodeRatios),
                scaleProfileDataPointsConservative(mainTaskConfig.getDisconnectProfileDataPoints(), nodeRatios),
                scaleProfileDataPointsConservative(mainTaskConfig.getSubscribeProfileDataPoints(), nodeRatios),
                scaleProfileDataPointsConservative(
                    NodeTaskConfigMapper.publishProfileDataPoints(mainTaskConfig), publishRatios)
            );
        }

        private static NodeProfileDataPoints empty() {
            return new NodeProfileDataPoints(List.of(), List.of(), List.of(), List.of());
        }

        private List<long[]> connectDataPoints(int nodeIndex) {
            return dataPointsAt(connectDataPoints, nodeIndex);
        }

        private List<long[]> disconnectDataPoints(int nodeIndex) {
            return dataPointsAt(disconnectDataPoints, nodeIndex);
        }

        private List<long[]> subscribeDataPoints(int nodeIndex) {
            return dataPointsAt(subscribeDataPoints, nodeIndex);
        }

        private List<long[]> publishDataPoints(int nodeIndex) {
            return dataPointsAt(publishDataPoints, nodeIndex);
        }

        private static List<long[]> dataPointsAt(List<List<long[]>> dataPointsByNode, int nodeIndex) {
            return nodeIndex < dataPointsByNode.size() ? dataPointsByNode.get(nodeIndex) : null;
        }
    }
}
