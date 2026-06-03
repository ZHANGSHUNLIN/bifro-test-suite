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

package org.apache.bifromq.testsuite.app.cluster;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.cluster.broadcast.EventBroadcaster;
import org.apache.bifromq.testsuite.app.cluster.member.MemberHealthMonitor;
import org.apache.bifromq.testsuite.app.cluster.member.MemberRegistry;
import org.apache.bifromq.testsuite.app.cluster.topology.ClusterTopology;
import org.apache.bifromq.testsuite.app.local.NodeTimeoutTaskReconciler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClusterManager {

    @Resource
    private Vertx vertx;

    @Resource
    private MemberRegistry memberRegistry;

    @Resource
    private MemberHealthMonitor healthMonitor;

    @Resource
    private ClusterConfig clusterConfig;

    @Resource
    private EventBroadcaster broadcaster;

    @Autowired(required = false)
    private NodeTimeoutTaskReconciler nodeTimeoutTaskReconciler;
    private volatile long timeoutCheckTimerId = -1;
    private volatile ClusterTopology currentTopology;
    @Getter
    private volatile boolean initialized;

    @PostConstruct
    public void initialize() {
        if (initialized) {
            log.warn("ClusterManager already initialized");
            return;
        }

        log.info("Initializing ClusterManager...");
        memberRegistry.registerLocalMember()
            .onComplete(ar -> {
                if (ar.succeeded()) {

                    healthMonitor.start();
                    startTimeoutChecker();
                    refreshTopology();

                    initialized = true;
                    log.info("ClusterManager initialized successfully");
                } else {
                    log.error("Failed to initialize ClusterManager", ar.cause());
                }
            });
    }

    private void startTimeoutChecker() {
        long checkInterval = clusterConfig.getHeartbeatIntervalMillis();
        timeoutCheckTimerId = vertx.setPeriodic(checkInterval, id -> checkTimeoutNodes());
        log.info("Timeout checker started, interval={}ms", checkInterval);
    }

    private void checkTimeoutNodes() {
        memberRegistry.getAllMembers()
            .onSuccess(members -> {
                String localMemberId = memberRegistry.getLocalMemberId();
                java.util.Set<String> aliveClusterMembers = memberRegistry.getAliveClusterMemberIds();

                for (var entry : members.entrySet()) {
                    String memberId = entry.getKey();
                    var memberInfo = entry.getValue();
                    if (localMemberId.equals(memberId)) {
                        continue;
                    }
                    if (aliveClusterMembers.contains(memberId)) {
                        continue;
                    }
                    if (!memberInfo.isAlive(clusterConfig.getHeartbeatTimeout())) {
                        log.warn("Detected timeout node (left cluster): memberId={}, lastHeartbeat={}",
                            memberId, memberInfo.getLastHeartbeat());

                        if (nodeTimeoutTaskReconciler != null) {
                            nodeTimeoutTaskReconciler.handleNodeTimeout(memberId);
                        }
                        memberRegistry.removeMember(memberId)
                            .onSuccess(
                                v -> log.info("Cleaned stale timeout member from registry: memberId={}", memberId))
                            .onFailure(e -> log.warn("Failed to clean stale timeout member from registry: memberId={}",
                                memberId, e));
                    }
                }
            })
            .onFailure(e -> log.warn("Failed to check timeout nodes", e));
    }

    @PreDestroy
    public void destroy() {
        log.info("Destroying ClusterManager...");
        if (timeoutCheckTimerId != -1) {
            vertx.cancelTimer(timeoutCheckTimerId);
            timeoutCheckTimerId = -1;
            log.debug("Timeout checker stopped");
        }

        healthMonitor.stop();
        memberRegistry.unregisterLocalMember()
            .onComplete(ar -> {
                if (ar.succeeded()) {
                    log.info("ClusterManager destroyed");
                } else {
                    log.error("Failed to destroy ClusterManager", ar.cause());
                }
            });
    }

    public ClusterTopology getCurrentTopology() {
        ClusterTopology topology = currentTopology;
        if (topology == null) {
            return new ClusterTopology(null, clusterConfig);
        }
        return topology;
    }

    public Future<ClusterTopology> refreshTopology() {
        return memberRegistry.getAllMembers()
            .map(members -> {
                ClusterTopology newTopology = new ClusterTopology(members, clusterConfig);
                ClusterTopology oldTopology = this.currentTopology;
                this.currentTopology = newTopology;

                if (oldTopology != null) {
                    broadcaster.broadcastTopologyChanged(oldTopology, newTopology);
                }

                log.debug("Topology refreshed: {} members", members.size());
                return newTopology;
            });
    }

    public String getLocalMemberId() {
        return memberRegistry.getLocalMemberId();
    }

}
