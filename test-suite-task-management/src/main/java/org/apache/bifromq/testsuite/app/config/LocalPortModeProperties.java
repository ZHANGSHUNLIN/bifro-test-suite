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

package org.apache.bifromq.testsuite.app.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.concurrent.CompletableFuture;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.cluster.shared.HazelcastDataManager;
import org.apache.bifromq.testsuite.app.cluster.shared.ShareDataAddr;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.springframework.stereotype.Component;

@Data
@Slf4j
@Component
public class LocalPortModeProperties {

    private static final String CONFIG_KEY = "local-port-mode";
    private static final LocalPortRangeConfig DEFAULT_CONFIG = LocalPortRangeConfig.builder()
        .enabled(false)
        .startPort(10000)
        .endPort(65535)
        .build();

    @Resource
    private HazelcastDataManager hazelcastDataManager;

    private static LocalPortRangeConfig defaultConfig() {
        return DEFAULT_CONFIG.normalized();
    }

    @PostConstruct
    public void logEffectiveConfig() {
        LocalPortRangeConfig config = toConfig();
        int rangeSize = config.getEndPort() - config.getStartPort() + 1;
        log.info("Local port mode runtime config loaded: enabled={}, startPort={}, endPort={}, rangeSize={}",
            config.isEnabled(), config.getStartPort(), config.getEndPort(), rangeSize);
    }

    public LocalPortRangeConfig toConfig() {
        return getConfig().join();
    }

    public boolean isEnabled() {
        return toConfig().isEnabled();
    }

    public CompletableFuture<LocalPortRangeConfig> getConfig() {
        if (hazelcastDataManager == null) {
            return CompletableFuture.completedFuture(defaultConfig());
        }
        return hazelcastDataManager.<String, LocalPortRangeConfig>map(ShareDataAddr.CLUSTER_RUNTIME_CONFIG)
            .key(CONFIG_KEY)
            .future()
            .thenApply(config -> config == null ? defaultConfig() : config.normalized());
    }

    public CompletableFuture<LocalPortRangeConfig> updateConfig(LocalPortRangeConfig config) {
        LocalPortRangeConfig normalized = (config == null ? defaultConfig() : config).normalized();
        return hazelcastDataManager.<String, LocalPortRangeConfig>map(ShareDataAddr.CLUSTER_RUNTIME_CONFIG)
            .key(CONFIG_KEY)
            .atomicCompute(old -> normalized)
            .future()
            .thenApply(updated -> updated == null ? normalized : updated.normalized())
            .thenApply(updated -> {
                int rangeSize = updated.getEndPort() - updated.getStartPort() + 1;
                log.info("Local port mode runtime config updated: enabled={}, startPort={}, endPort={}, rangeSize={}",
                    updated.isEnabled(), updated.getStartPort(), updated.getEndPort(), rangeSize);
                return updated;
            });
    }
}
