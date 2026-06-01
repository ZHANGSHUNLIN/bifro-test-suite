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

package org.apache.bifromq.testsuite.app.controller.cluster;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.config.LocalPortModeProperties;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/cluster/config")
@Tag(name = "Cluster Config", description = "Cluster runtime configuration API")
public class ClusterConfigController {

    @Resource
    private LocalPortModeProperties localPortModeProperties;

    @Operation(summary = "Get local port mode config", description = "Get cluster runtime local port mode config")
    @GetMapping("/local-port-mode")
    public Mono<ApiResponse<LocalPortRangeConfig>> getLocalPortMode() {
        return Mono.fromCompletionStage(localPortModeProperties.getConfig())
            .map(ApiResponse::success)
            .onErrorResume(e -> {
                log.warn("Failed to get local port mode config", e);
                return Mono.just(ApiResponse.error(e.getMessage()));
            });
    }

    @Operation(summary = "Update local port mode config", description = "Update cluster runtime local port mode config")
    @PutMapping("/local-port-mode")
    public Mono<ApiResponse<LocalPortRangeConfig>> updateLocalPortMode(
        @RequestBody LocalPortRangeConfig request) {
        LocalPortRangeConfig normalized;
        try {
            normalized = (request == null ? new LocalPortRangeConfig() : request).normalized();
        } catch (IllegalArgumentException e) {
            return Mono.just(ApiResponse.error(e.getMessage()));
        }
        return Mono.fromCompletionStage(localPortModeProperties.updateConfig(normalized))
            .map(ApiResponse::success)
            .onErrorResume(e -> {
                log.warn("Failed to update local port mode config", e);
                return Mono.just(ApiResponse.error(e.getMessage()));
            });
    }
}
