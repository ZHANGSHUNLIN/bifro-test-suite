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

package org.apache.bifromq.testsuite.app.cluster.topology;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.ToString;
import org.apache.bifromq.testsuite.app.cluster.ClusterConfig;
import org.apache.bifromq.testsuite.app.cluster.member.MemberInfo;
import org.apache.bifromq.testsuite.app.cluster.scheduling.TaskAssignment;
import org.apache.bifromq.testsuite.worker.TaskConfig;

@ToString
@Getter
public class ClusterTopology {

    private final Map<String, MemberInfo> members;

    private final Instant snapshotTime;

    private final ClusterConfig config;

    public ClusterTopology(Map<String, MemberInfo> members, ClusterConfig config) {
        this.members = members != null ? Collections.unmodifiableMap(new ConcurrentHashMap<>(members))
            : Collections.emptyMap();
        this.snapshotTime = Instant.now();
        this.config = config;
    }

    public int getMemberCount() {
        return members.size();
    }

    public Optional<MemberInfo> getMember(String memberId) {
        return Optional.ofNullable(members.get(memberId));
    }

    public boolean hasAliveMembers() {
        return members.values().stream().anyMatch(m -> m.isAlive(config.getHeartbeatTimeout()));
    }

    public Map<String, MemberInfo> getAliveMembers() {
        return members.entrySet().stream()
            .filter(e -> e.getValue().isAlive(config.getHeartbeatTimeout()))
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public TaskAssignment calculateAssignment(TaskConfig taskConfig) {
        return calculateAssignment(taskConfig, config.getCpuWeight(), config.getMemoryWeight());
    }

    public TaskAssignment calculateAssignment(TaskConfig taskConfig, int cpuWeight, int memWeight) {
        Map<String, MemberInfo> aliveMembers = getAliveMembers();
        int totalClientCount = taskConfig.getTotalClientCount();
        int nodeCount = aliveMembers.size();

        if (totalClientCount <= 0) {
            return new TaskAssignment(taskConfig.getTaskId(), 0, Collections.emptyMap());
        }

        if (nodeCount == 0) {
            return new TaskAssignment(taskConfig.getTaskId(), 0, Collections.emptyMap());
        }

        Map<String, Integer> nodeAssignments = new ConcurrentHashMap<>();

        double totalCpu = aliveMembers.values().stream()
            .mapToDouble(m -> m.getSystemInfo() != null ? m.getSystemInfo().getCpu().getLoadAverage() : 0)
            .sum();
        double totalMemory = aliveMembers.values().stream()
            .mapToDouble(m -> m.getSystemInfo() != null ? m.getSystemInfo().getMemory().getTotal() : 0)
            .sum();

        if (totalClientCount < nodeCount) {

            int idx = 0;
            for (String nodeId : aliveMembers.keySet()) {
                if (idx >= totalClientCount) {
                    break;
                }
                nodeAssignments.put(nodeId, 1);
                idx++;
            }
        } else {

            double baseWeight = 1.0 / nodeCount;
            for (Map.Entry<String, MemberInfo> entry : aliveMembers.entrySet()) {
                String nodeId = entry.getKey();
                MemberInfo member = entry.getValue();

                double cpuRatio = totalCpu > 0 && member.getSystemInfo() != null
                    ? member.getSystemInfo().getCpu().getLoadAverage() / totalCpu
                    : baseWeight;
                double memRatio = totalMemory > 0 && member.getSystemInfo() != null
                    ? member.getSystemInfo().getMemory().getTotal() / totalMemory
                    : baseWeight;

                double weight = cpuRatio * cpuWeight + memRatio * memWeight;

                double normalizedWeight = weight / (cpuWeight + memWeight);
                int assigned = (int) (totalClientCount * normalizedWeight);
                nodeAssignments.put(nodeId, Math.max(assigned, 0));
            }

            int allocated = nodeAssignments.values().stream().mapToInt(Integer::intValue).sum();
            int remainder = totalClientCount - allocated;

            if (remainder > 0) {
                int idx = 0;
                for (String nodeId : nodeAssignments.keySet()) {
                    if (idx >= remainder) {
                        break;
                    }
                    nodeAssignments.merge(nodeId, 1, Integer::sum);
                    idx++;
                }
            }
        }

        return new TaskAssignment(taskConfig.getTaskId(), totalClientCount, nodeAssignments);
    }
}