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

package org.apache.bifromq.testsuite.app.controller.group;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.bean.group.GroupListItem;
import org.apache.bifromq.testsuite.app.bean.group.GroupRequest;
import org.apache.bifromq.testsuite.app.controller.ApiController;
import org.apache.bifromq.testsuite.app.database.pojo.MqttGroup;
import org.apache.bifromq.testsuite.app.group.GroupManager;
import org.apache.bifromq.testsuite.audit.application.AuditLogService;
import org.apache.bifromq.testsuite.audit.domain.AuditAction;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.web.PageInfo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Tag(name = "Group Management", description = "Unified group management API, supports Broker and task groups")
@RestController
@RequestMapping("/api/groups")
public class GroupController implements ApiController {

    @Resource
    private GroupManager groupManager;

    @Resource
    private AuditLogService auditLogService;

    @Operation(summary = "Add Group", description = "Add a new group, specify type (BROKER/TASK) via type parameter")
    @PostMapping
    public Mono<ApiResponse<MqttGroup>> add(
        @Valid @RequestBody @Parameter(description = "Group request") GroupRequest request,
        @RequestParam(name = "type") @Parameter(description = "Group type: BROKER or TASK") String type,
        ServerWebExchange exchange) {
        return groupManager.add(request, type)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.GROUP_CREATE, "GROUP",
                    response.getData() == null ? null : response.getData().getId(), response.isSuccess(),
                    "Create group")
                .thenReturn(response));
    }

    @Operation(summary = "Get All Groups", description = "Get all group list for dropdown, specify type via type parameter")
    @GetMapping("/all")
    public Flux<MqttGroup> getAll(
        @Parameter(description = "Group type: BROKER or TASK") @RequestParam(name = "type") String type) {
        return groupManager.getAllGroups(type);
    }

    @Operation(summary = "Get Group Details", description = "Get group details by ID")
    @GetMapping("/{id}")
    public Mono<ApiResponse<MqttGroup>> get(@PathVariable(name = "id") @Parameter(description = "groupID") String id) {
        return groupManager.getDetail(id);
    }

    @Operation(summary = "Update Group", description = "Update group by ID")
    @PutMapping("/{id}")
    public Mono<ApiResponse<MqttGroup>> update(
        @PathVariable(name = "id") @Parameter(description = "groupID") String id,
        @Valid @RequestBody @Parameter(description = "Group request") GroupRequest request,
        ServerWebExchange exchange) {
        return groupManager.update(id, request, null)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.GROUP_UPDATE, "GROUP", id,
                    response.isSuccess(), "Update group")
                .thenReturn(response));
    }

    @Operation(summary = "Delete Group", description = "Delete group by ID")
    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(
        @PathVariable(name = "id") @Parameter(description = "groupID") String id,
        @Parameter(description = "Group type: BROKER or TASK") @RequestParam(name = "type") String type,
        ServerWebExchange exchange) {
        return groupManager.delete(id, type)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.GROUP_DELETE, "GROUP", id,
                    response.isSuccess(), "Delete group")
                .thenReturn(response));
    }

    @Operation(summary = "Get Group List", description = "Paginated group list query, specify type via type parameter")
    @GetMapping("/list")
    public Mono<ApiResponse<PageInfo<GroupListItem>>> list(
        @Parameter(description = "Group type: BROKER or TASK") @RequestParam(name = "type") String type,
        @Parameter(description = "group name (fuzzy match)") @RequestParam(name = "name", required = false) String name,
        @Parameter(description = "Page number") @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
        @Parameter(description = "Page size") @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize) {
        return groupManager.list(pageNum, pageSize, type, name);
    }
}
