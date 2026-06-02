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

package org.apache.bifromq.testsuite.app.cluster.event;

import lombok.Getter;
import org.apache.bifromq.testsuite.app.cluster.topology.ClusterTopology;

@Getter
public class TopologyChangedEvent extends ClusterEvent {


    private final ClusterTopology oldTopology;
    private final ClusterTopology newTopology;
    private final java.util.List<String> addedMembers;
    private final java.util.List<String> removedMembers;

    public TopologyChangedEvent(ClusterTopology oldTopology, ClusterTopology newTopology) {
        super(EventType.TOPOLOGY_CHANGED);
        this.oldTopology = oldTopology;
        this.newTopology = newTopology;
        this.addedMembers = detectAddedMembers(oldTopology, newTopology);
        this.removedMembers = detectRemovedMembers(oldTopology, newTopology);
    }

    private java.util.List<String> detectAddedMembers(ClusterTopology oldTopo, ClusterTopology newTopo) {
        if (oldTopo == null || oldTopo.getMembers().isEmpty()) {
            return new java.util.ArrayList<>(newTopo.getMembers().keySet());
        }
        return newTopo.getMembers().keySet().stream()
            .filter(id -> !oldTopo.getMembers().containsKey(id))
            .collect(java.util.stream.Collectors.toList());
    }

    private java.util.List<String> detectRemovedMembers(ClusterTopology oldTopo, ClusterTopology newTopo) {
        if (oldTopo == null) {
            return java.util.Collections.emptyList();
        }
        return oldTopo.getMembers().keySet().stream()
            .filter(id -> !newTopo.getMembers().containsKey(id))
            .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public String toString() {
        return "TopologyChangedEvent{" +
            "addedMembers=" + addedMembers +
            ", removedMembers=" + removedMembers +
            ", timestamp=" + getTimestamp() +
            '}';
    }
}