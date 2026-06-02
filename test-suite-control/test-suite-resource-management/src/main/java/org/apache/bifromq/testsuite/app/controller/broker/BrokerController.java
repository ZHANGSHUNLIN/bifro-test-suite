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

package org.apache.bifromq.testsuite.app.controller.broker;

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.bean.broker.BrokerListItem;
import org.apache.bifromq.testsuite.app.bean.broker.MqttBrokerEnableRequest;
import org.apache.bifromq.testsuite.app.bean.broker.MqttBrokerRequest;
import org.apache.bifromq.testsuite.app.broker.BrokerManager;
import org.apache.bifromq.testsuite.app.controller.ApiController;
import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.audit.application.AuditLogService;
import org.apache.bifromq.testsuite.audit.domain.AuditAction;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.web.PageInfo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Tag(name = "Broker Management", description = "MQTT Broker configuration management API")
@RestController
@RequestMapping("/api/broker")
@ConditionalOnControlPlane
public class BrokerController implements ApiController {

    @Resource
    private BrokerManager brokerManager;

    @Resource
    private AuditLogService auditLogService;

    @Operation(summary = "Add Broker", description = "Add a new MQTT Broker configuration")
    @PostMapping("/add")
    public Mono<ApiResponse<MqttBroker>> addTask(
        @Valid @RequestBody @Parameter(description = "Broker configuration request")
        MqttBrokerRequest mqttBrokerRequest,
        ServerWebExchange exchange) {
        return brokerManager.addTask(mqttBrokerRequest)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.BROKER_CREATE, "BROKER",
                    response.getData() == null ? null : response.getData().getId(), response.isSuccess(),
                    "Create broker")
                .thenReturn(response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Broker", description = "Delete MQTT Broker configuration by ID")
    public Mono<ApiResponse<MqttBroker>> del(@PathVariable(name = "id") String id, ServerWebExchange exchange) {
        return brokerManager.del(id)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.BROKER_DELETE, "BROKER", id,
                    response.isSuccess(), "Delete broker")
                .thenReturn(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Broker", description = "Update MQTT Broker configuration")
    public Mono<ApiResponse<MqttBroker>> update(@PathVariable(name = "id") String id,
                                                @Valid @RequestBody
                                                @Parameter(description = "Broker configuration request")
                                                MqttBrokerRequest mqttBrokerRequest,
                                                ServerWebExchange exchange) {
        return brokerManager.update(id, mqttBrokerRequest)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.BROKER_UPDATE, "BROKER", id,
                    response.isSuccess(), "Update broker")
                .thenReturn(response));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable/Disable Broker", description = "Enable or disable MQTT Broker")
    public Mono<ApiResponse<MqttBroker>> enable(@PathVariable(name = "id") String id,
                                                @RequestBody @Parameter(description = "Enable/disable request")
                                                MqttBrokerEnableRequest enabled,
                                                ServerWebExchange exchange) {
        return brokerManager.enable(id, enabled.getEnabled())
            .flatMap(response -> auditLogService.record(exchange, AuditAction.BROKER_UPDATE, "BROKER", id,
                    response.isSuccess(), "Update broker status")
                .thenReturn(response));
    }

    @Operation(summary = "Get Broker List", description = "Paginated Broker list query")
    @GetMapping("/list")
    public Mono<ApiResponse<PageInfo<BrokerListItem>>> list(
        @Parameter(description = "Is enabled") @RequestParam(name = "enabled", required = false) Boolean enabled,
        @Parameter(description = "groupID") @RequestParam(name = "group", required = false) String group,
        @Parameter(description = "Page number", example = "1") @RequestParam(name = "pageNum", defaultValue = "1")
        Integer pageNum,
        @Parameter(description = "Page size", example = "20") @RequestParam(name = "pageSize", defaultValue = "20")
        Integer pageSize
    ) {
        return brokerManager.list(enabled, group, pageNum, pageSize);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Broker Details", description = "Get MQTT Broker details by ID")
    public Mono<ApiResponse<MqttBroker>> get(@PathVariable(name = "id") String brokerId) {
        if (brokerId == null || brokerId.trim().isEmpty()) {
            return Mono.error(new RuntimeException(Messages.get("error.broker.notFound")));
        }
        return brokerManager.get(brokerId);
    }
}
