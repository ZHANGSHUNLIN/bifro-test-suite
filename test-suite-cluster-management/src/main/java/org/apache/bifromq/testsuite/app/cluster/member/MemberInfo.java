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

package org.apache.bifromq.testsuite.app.cluster.member;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.bifromq.testsuite.app.bean.ClusterNodeInfo;
import org.apache.bifromq.testsuite.cluster.NodeRole;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String name;

    private String host;

    @Builder.Default
    private NodeRole role = NodeRole.UNKNOWN;

    private ClusterNodeInfo systemInfo;

    private volatile long lastHeartbeat;

    private Instant registeredAt;

    public boolean isAlive(Duration timeout) {
        if (lastHeartbeat <= 0) {
            return false;
        }
        return System.currentTimeMillis() - lastHeartbeat < timeout.toMillis();
    }

    public boolean isAlive() {
        return isAlive(Duration.ofSeconds(30));
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public double getMemoryUsagePercent() {
        if (systemInfo == null || systemInfo.getMemory() == null) {
            return 0.0;
        }
        ClusterNodeInfo.MemoryInfo memory = systemInfo.getMemory();
        if (memory.getTotal() == 0) {
            return 0.0;
        }
        return (double) memory.getUsed() / memory.getTotal() * 100.0;
    }

    public double getCpuLoadAverage() {
        if (systemInfo == null || systemInfo.getCpu() == null) {
            return 0.0;
        }
        return systemInfo.getCpu().getLoadAverage();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MemberInfo that = (MemberInfo) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
