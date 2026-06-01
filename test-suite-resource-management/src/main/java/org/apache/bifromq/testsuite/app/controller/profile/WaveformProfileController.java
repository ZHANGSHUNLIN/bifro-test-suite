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

package org.apache.bifromq.testsuite.app.controller.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.controller.ApiController;
import org.apache.bifromq.testsuite.app.database.pojo.WaveformProfile;
import org.apache.bifromq.testsuite.app.database.service.WaveformProfileService;
import org.apache.bifromq.testsuite.audit.application.AuditLogService;
import org.apache.bifromq.testsuite.audit.domain.AuditAction;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.web.PageInfo;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Tag(name = "Traffic Profile Library", description = "Traffic profile import and management API")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class WaveformProfileController implements ApiController {

    private final WaveformProfileService profileService;
    private final AuditLogService auditLogService;

    @Operation(summary = "Get Profile List", description = "List all profiles, supports keyword filtering by name")
    @GetMapping
    public Mono<ApiResponse<PageInfo<WaveformProfile>>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String group,
        @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
        @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize) {
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        PageRequest pageable = PageRequest.of(
            safePageNum - 1,
            safePageSize,
            Sort.by(Sort.Direction.DESC, "createdAt"));
        return Mono.zip(
                profileService.list(keyword, group, pageable).collectList(),
                profileService.count(keyword, group))
            .map(tuple -> ApiResponse.pageSuccess(tuple.getT1(), tuple.getT2(), safePageNum, safePageSize));
    }

    @Operation(summary = "Get Profile Details", description = "Return full profile info including all dataPoints")
    @GetMapping("/{id}")
    public Mono<ApiResponse<WaveformProfile>> getById(@PathVariable String id) {
        return profileService.getById(id)
            .map(ApiResponse::success);
    }

    @Operation(summary = "Get Profile Preview Data", description = "Return downsampled dataPoints for frontend chart rendering")
    @GetMapping("/{id}/preview")
    public Mono<ApiResponse<List<long[]>>> getPreview(@PathVariable String id) {
        return profileService.getPreviewData(id)
            .map(ApiResponse::success);
    }

    @Operation(summary = "Create Profile (Manual Draw)", description = "Create traffic profile from control points exported by frontend editor")
    @PostMapping
    public Mono<ApiResponse<WaveformProfile>> create(@Valid @RequestBody CreateProfileRequest req,
                                                     ServerWebExchange exchange) {
        return profileService.createManual(
                req.getDataPoints(),
                req.getName().trim(),
                req.getDescription() != null ? req.getDescription().trim() : "",
                req.getGroup(),
                req.getMaxQps() != null ? req.getMaxQps() : 0,
                req.getTargetTotalCount(),
                "system")
            .map(ApiResponse::success)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.PROFILE_CREATE, "PROFILE",
                    response.getData() == null ? null : response.getData().getId(), response.isSuccess(),
                    "Create profile")
                .thenReturn(response));
    }

    @Operation(summary = "Import Profile", description = "Upload Grafana JSON or Prometheus CSV file and create profile")
    @PostMapping("/import")
    public Mono<ApiResponse<WaveformProfile>> importFromGrafana(
        @RequestPart("file") FilePart file,
        @RequestPart("name") String name,
        @RequestPart(value = "description", required = false) String description,
        ServerWebExchange exchange) {

        String filename = file.filename().toLowerCase();
        boolean isCsv = filename.endsWith(".csv");

        return file.content()
            .map(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            })
            .reduce("", String::concat)
            .flatMap(content -> isCsv
                ? profileService.importFromCsv(
                content,
                name.trim(),
                description != null ? description.trim() : "",
                "system")
                : profileService.importFromGrafana(
                content,
                name.trim(),
                description != null ? description.trim() : "",
                "system"))
            .map(ApiResponse::success)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.PROFILE_CREATE, "PROFILE",
                    response.getData() == null ? null : response.getData().getId(), response.isSuccess(),
                    "Import profile")
                .thenReturn(response));
    }

    @Operation(summary = "Delete Profile", description = "Delete traffic profile by ID")
    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> deleteById(@PathVariable String id, ServerWebExchange exchange) {
        return profileService.deleteById(id)
            .then(Mono.just(ApiResponse.<Void>success(null)))
            .flatMap(response -> auditLogService.record(exchange, AuditAction.PROFILE_DELETE, "PROFILE", id,
                    response.isSuccess(), "Delete profile")
                .thenReturn(response));
    }

    @Operation(summary = "Update Profile", description = "Update profile name, description and data points by ID")
    @PutMapping("/{id}")
    public Mono<ApiResponse<WaveformProfile>> update(
        @PathVariable String id,
        @Valid @RequestBody CreateProfileRequest req,
        ServerWebExchange exchange) {
        return profileService.updateManual(
                id,
                req.getDataPoints(),
                req.getName().trim(),
                req.getDescription() != null ? req.getDescription().trim() : "",
                req.getGroup(),
                req.getMaxQps() != null ? req.getMaxQps() : 0,
                req.getTargetTotalCount())
            .map(ApiResponse::success)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.PROFILE_UPDATE, "PROFILE", id,
                    response.isSuccess(), "Update profile")
                .thenReturn(response));
    }

    @Data
    public static class CreateProfileRequest {

        @NotEmpty(message = "{validation.profile.name.notEmpty}")
        @Size(max = 100, message = "{validation.profile.name.size}")
        private String name;

        private String description;

        @NotBlank(message = "{validation.profile.group.notBlank}")
        private String group;

        @NotNull(message = "{validation.profile.dataPoints.notNull}")
        @Size(min = 2, message = "{validation.profile.dataPoints.size}")
        private List<long[]> dataPoints;
        private long totalDurationMs;
        private Integer maxQps;
        private Long targetTotalCount;
    }
}
