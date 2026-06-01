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

package org.apache.bifromq.testsuite.app.cluster.broadcast;

import io.vertx.core.eventbus.EventBus;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.cluster.event.MemberJoinedEvent;
import org.apache.bifromq.testsuite.app.cluster.event.MemberLeftEvent;
import org.apache.bifromq.testsuite.app.cluster.event.TopologyChangedEvent;
import org.apache.bifromq.testsuite.app.cluster.member.MemberInfo;
import org.apache.bifromq.testsuite.app.cluster.topology.ClusterTopology;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventBroadcaster {

    public static final String MEMBER_JOINED = "cluster.member.joined";
    public static final String MEMBER_LEFT = "cluster.member.left";
    public static final String MEMBER_HEALTH_CHANGED = "cluster.member.health.changed";
    public static final String TOPOLOGY_CHANGED = "cluster.topology.changed";

    @Resource
    private EventBus eventBus;

    public void broadcastMemberJoined(String memberId, MemberInfo memberInfo) {
        MemberJoinedEvent event = new MemberJoinedEvent(memberId, memberInfo);
        eventBus.publish(MEMBER_JOINED, event);
        log.debug("Broadcast member joined: {}", event);
    }

    public void broadcastMemberLeft(String memberId) {
        broadcastMemberLeft(memberId, "normal");
    }

    public void broadcastMemberLeft(String memberId, String reason) {
        MemberLeftEvent event = new MemberLeftEvent(memberId, reason);
        eventBus.publish(MEMBER_LEFT, event);
        log.debug("Broadcast member left: {}", event);
    }

    public void broadcastTopologyChanged(ClusterTopology oldTopology, ClusterTopology newTopology) {
        if (oldTopology == null || newTopology == null) {
            return;
        }

        boolean hasChanges = !oldTopology.getMembers().keySet().equals(newTopology.getMembers().keySet());
        if (!hasChanges) {
            return;
        }

        TopologyChangedEvent event = new TopologyChangedEvent(oldTopology, newTopology);
        eventBus.publish(TOPOLOGY_CHANGED, event);
        log.info("Broadcast topology changed: {} added, {} removed",
            event.getAddedMembers().size(), event.getRemovedMembers().size());
    }

    public void broadcastHealthChanged(String memberId, boolean isAlive) {
        eventBus.publish(MEMBER_HEALTH_CHANGED, new MemberHealthChangedEvent(memberId, isAlive));
    }

    public record MemberHealthChangedEvent(String memberId, boolean isAlive) {
    }
}