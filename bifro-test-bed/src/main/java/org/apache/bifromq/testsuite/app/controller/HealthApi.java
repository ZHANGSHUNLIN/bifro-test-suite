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

package org.apache.bifromq.testsuite.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.bifromq.testsuite.app.local.LocalTaskCoordinator;
import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health Check", description = "System health status API")
@RestController
@ConditionalOnControlPlane
public class HealthApi {

    @Autowired(required = false)
    private BuildProperties buildProperties;

    @Autowired(required = false)
    private LocalTaskCoordinator localTaskCoordinator;

    @Operation(summary = "Health Check", description = "Check if the service is running normally")
    @ApiResponse(responseCode = "200", description = "Service healthy")
    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @Operation(summary = "Version Info", description = "Get current build version (from pom.xml)")
    @ApiResponse(responseCode = "200", description = "Version Info")
    @GetMapping("/api/version")
    public VersionInfo version() {
        String ver = buildProperties != null ? buildProperties.getVersion() : "unknown";
        String buildTime = buildProperties != null ? buildProperties.getTime().toString() : "";
        return new VersionInfo("be-" + ver, buildTime, "Bifro Test Suite");
    }

    @GetMapping("/running_tasks")
    public Object info() {
        if (localTaskCoordinator == null) {
            return Map.of();
        }
        return localTaskCoordinator.getRunningTaskMap();
    }

    @Data
    @AllArgsConstructor
    public static class VersionInfo {
        private String version;
        private String buildTime;
        private String description;
    }

}
