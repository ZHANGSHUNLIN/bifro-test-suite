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

package org.apache.bifromq.testsuite.app.config.vertx;

import com.hazelcast.config.Config;
import com.hazelcast.config.InterfacesConfig;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.HazelcastInstance;
import io.micrometer.core.instrument.Metrics;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.config.vertx.codec.VertxCodecManager;
import org.apache.bifromq.testsuite.client.PinnedLocalPortTransport;
import org.apache.bifromq.testsuite.config.node.NodeIdentityProperties;
import org.apache.bifromq.testsuite.diagnostics.AsyncDiagnosticContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class VertxConfig {

    @Bean
    public HazelcastClusterManager hazelcastClusterManager(VertxProperties vertxProperties,
                                                           NodeIdentityProperties nodeIdentityProperties) {
        Config hazelcastConfig = new Config();
        hazelcastConfig.setClusterName(vertxProperties.getEnv());
        hazelcastConfig.getMemberAttributeConfig()
            .setAttribute(NodeIdentityProperties.NODE_ID_MEMBER_ATTRIBUTE, nodeIdentityProperties.getNodeId());

        NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig()
            .setInterfaces(new InterfacesConfig().addInterface(vertxProperties.getHost()))
            .setPortAutoIncrement(true)
            .setReuseAddress(true);
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getMulticastConfig().setEnabled(vertxProperties.isMulticast());
        joinConfig.getTcpIpConfig()
            .setEnabled(true)
            .addMember(vertxProperties.getMembers());
        VertxCodecManager.registerStreamSerializerAll(hazelcastConfig);

        return new HazelcastClusterManager(hazelcastConfig);
    }

    @Bean
    public Vertx vertx(VertxProperties vertxProperties, HazelcastClusterManager hazelcastClusterManager) {
        MicrometerMetricsOptions metricsOptions = new MicrometerMetricsOptions()
            .setEnabled(true)
            .setJvmMetricsEnabled(false)
            .setNettyMetricsEnabled(true);
        BackendRegistries.setupBackend(metricsOptions, Metrics.globalRegistry);
        return Vertx.builder()
            .with(new VertxOptions(vertxProperties.getVertxOptions())
                .setMetricsOptions(metricsOptions)
                .setPreferNativeTransport(true))
            .withTransport(new PinnedLocalPortTransport())
            .withClusterManager(hazelcastClusterManager)
            .buildClustered()
            .toCompletionStage()
            .toCompletableFuture()
            .thenApply(vertx -> {
                vertx.exceptionHandler(this::logUnhandledVertxError);
                VertxCodecManager.registerCodecAll(vertx);
                return vertx;
            })
            .whenComplete((vertx, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to start Test Suite server", throwable);
                    return;
                }
                Runtime.getRuntime().addShutdownHook(
                    new Thread(() ->
                        uninterrupted(() -> vertx.close().toCompletionStage().toCompletableFuture().join())
                    )
                );
            })
            .join();

    }

    @Bean
    public HazelcastInstance hazelcastInstance(Vertx vertx, HazelcastClusterManager hazelcastClusterManager) {
        HazelcastInstance hazelcastInstance = hazelcastClusterManager.getHazelcastInstance();
        if (hazelcastInstance == null) {
            throw new IllegalStateException("Hazelcast instance is not available after Vert.x cluster startup");
        }
        return hazelcastInstance;
    }

    @Bean
    public EventBus eventBus(Vertx vertx) {
        return vertx.eventBus();
    }

    private void uninterrupted(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            log.error("Error during shutdown", e);
        }
    }

    private void logUnhandledVertxError(Throwable error) {
        AsyncDiagnosticContext.Snapshot context = AsyncDiagnosticContext.current();
        if (context == null) {
            log.error("EventBus error: taskId=, nodeId=, stage=, clientId=", error);
            return;
        }
        log.error("EventBus error: taskId={}, nodeId={}, stage={}, clientId={}",
            safe(context.taskId()), safe(context.nodeId()), safe(context.stage()), safe(context.clientId()), error);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

}
