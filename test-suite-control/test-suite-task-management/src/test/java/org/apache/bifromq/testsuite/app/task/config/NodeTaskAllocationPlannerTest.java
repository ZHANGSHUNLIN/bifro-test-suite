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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.apache.bifromq.testsuite.qps.ProfileQpsSpec;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.junit.jupiter.api.Test;

class NodeTaskAllocationPlannerTest {

    @Test
    void toNodeTaskConfigsShouldAssignThingIdRangesAndPubSubCounts() {
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId("task-1")
            .thingIdStartAt(100)
            .totalClientCount(12)
            .fanOut(1)
            .fanIn(1)
            .build();
        List<NodeTaskAssignment> assignments = List.of(
            new NodeTaskAssignment("node-1", 5),
            new NodeTaskAssignment("node-2", 7));

        Map<String, TaskConfig> nodeTaskConfigs =
            NodeTaskAllocationPlanner.toNodeTaskConfigs(mainTaskConfig, assignments);

        assertThat(nodeTaskConfigs).containsOnlyKeys("node-1", "node-2");
        assertThat(nodeTaskConfigs.get("node-1").getThingIdStartAt()).isEqualTo(100);
        assertThat(nodeTaskConfigs.get("node-2").getThingIdStartAt()).isEqualTo(105);
        assertThat(nodeTaskConfigs.values()).extracting(TaskConfig::getTotalClientCount)
            .containsExactly(5, 7);
        assertThat(nodeTaskConfigs.values().stream().mapToInt(TaskConfig::getNodePubCount).sum()).isEqualTo(6);
        assertThat(nodeTaskConfigs.values().stream().mapToInt(TaskConfig::getNodeSubCount).sum()).isEqualTo(6);
    }

    @Test
    void toNodeTaskConfigsShouldSplitDynamicProfileConservatively() {
        TaskConfig.ProfileConfig profileConfig = TaskConfig.ProfileConfig.builder()
            .dataPoints(List.of(new long[] {0, 9}, new long[] {1000, 15}))
            .totalDurationMs(1000)
            .endBehavior(ProfileQpsSpec.EndBehavior.HOLD)
            .build();
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId("task-1")
            .totalClientCount(3)
            .qpsMode(TaskConfig.QpsMode.DYNAMIC)
            .profileConfig(profileConfig)
            .build();
        List<NodeTaskAssignment> assignments = List.of(
            new NodeTaskAssignment("node-1", 1),
            new NodeTaskAssignment("node-2", 1),
            new NodeTaskAssignment("node-3", 1));

        Map<String, TaskConfig> nodeTaskConfigs =
            NodeTaskAllocationPlanner.toNodeTaskConfigs(mainTaskConfig, assignments);

        for (int pointIndex = 0; pointIndex < profileConfig.getDataPoints().size(); pointIndex++) {
            int finalPointIndex = pointIndex;
            long splitSum = nodeTaskConfigs.values().stream()
                .map(TaskConfig::getPublishProfileDataPoints)
                .mapToLong(points -> points.get(finalPointIndex)[1])
                .sum();
            assertThat(splitSum).isEqualTo(profileConfig.getDataPoints().get(pointIndex)[1]);
        }
    }

    @Test
    void toNodeTaskConfigsShouldSplitPublishProfileByPublisherRatio() {
        TaskConfig.ProfileConfig profileConfig = TaskConfig.ProfileConfig.builder()
            .dataPoints(List.of(new long[] {0, 10}))
            .totalDurationMs(1000)
            .endBehavior(ProfileQpsSpec.EndBehavior.HOLD)
            .build();
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId("task-1")
            .totalClientCount(10)
            .fanIn(1)
            .fanOut(1)
            .qpsMode(TaskConfig.QpsMode.DYNAMIC)
            .profileConfig(profileConfig)
            .build();
        List<NodeTaskAssignment> assignments = List.of(
            new NodeTaskAssignment("node-1", 8),
            new NodeTaskAssignment("node-2", 2));

        Map<String, TaskConfig> nodeTaskConfigs =
            NodeTaskAllocationPlanner.toNodeTaskConfigs(mainTaskConfig, assignments);

        assertThat(nodeTaskConfigs.get("node-1").getNodePubCount()).isEqualTo(4);
        assertThat(nodeTaskConfigs.get("node-2").getNodePubCount()).isEqualTo(1);
        assertThat(valuesOf(nodeTaskConfigs.get("node-1").getPublishProfileDataPoints())).containsExactly(8L);
        assertThat(valuesOf(nodeTaskConfigs.get("node-2").getPublishProfileDataPoints())).containsExactly(2L);
    }

    @Test
    void toNodeTaskConfigsShouldSplitFixedPublishRateByPublisherRatio() {
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId("task-1")
            .totalClientCount(10)
            .fanIn(1)
            .fanOut(1)
            .publishRate(10.0)
            .build();
        List<NodeTaskAssignment> assignments = List.of(
            new NodeTaskAssignment("node-1", 8),
            new NodeTaskAssignment("node-2", 2));

        Map<String, TaskConfig> nodeTaskConfigs =
            NodeTaskAllocationPlanner.toNodeTaskConfigs(mainTaskConfig, assignments);

        assertThat(nodeTaskConfigs.get("node-1").getPublishRate()).isEqualTo(8.0);
        assertThat(nodeTaskConfigs.get("node-2").getPublishRate()).isEqualTo(2.0);
    }

    @Test
    void toSingleClientNodeTaskConfigsShouldLimitNodesToTotalClientCount() {
        TaskConfig mainTaskConfig = TaskConfig.builder()
            .taskId("task-1")
            .thingIdStartAt(10)
            .totalClientCount(2)
            .build();

        Map<String, TaskConfig> nodeTaskConfigs =
            NodeTaskAllocationPlanner.toSingleClientNodeTaskConfigs(
                mainTaskConfig, List.of("node-1", "node-2", "node-3"));

        assertThat(nodeTaskConfigs).containsOnlyKeys("node-1", "node-2");
        assertThat(nodeTaskConfigs.values()).extracting(TaskConfig::getTotalClientCount)
            .containsExactly(1, 1);
        assertThat(nodeTaskConfigs.values()).extracting(TaskConfig::getThingIdStartAt)
            .containsExactly(10, 11);
    }

    private List<Long> valuesOf(List<long[]> dataPoints) {
        return dataPoints.stream().map(point -> point[1]).toList();
    }
}
