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

package org.apache.bifromq.testsuite.app.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.bifromq.testsuite.cluster.NodeRole;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NodeInfo {

    private static final long DEFAULT_HEARTBEAT_TIMEOUT_MS = 30_000L;
    private String nodeName;
    @Builder.Default
    private NodeRole role = NodeRole.UNKNOWN;
    private ClusterNodeInfo clusterNodeInfo;
    private Long nextPing;
    @Builder.Default
    private Boolean alive = true;

    public boolean isAlive() {
        if (nextPing == null) {
            return true;
        }
        long currentTime = System.currentTimeMillis();
        long timeSinceLastPing = currentTime - nextPing;
        return timeSinceLastPing <= DEFAULT_HEARTBEAT_TIMEOUT_MS;
    }

    public boolean isSchedulable() {
        return isAlive() && role != null && role.isSchedulable();
    }

}
