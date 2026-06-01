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

package org.apache.bifromq.testsuite.security.api;

import lombok.RequiredArgsConstructor;
import org.apache.bifromq.testsuite.audit.application.AuditLogService;
import org.apache.bifromq.testsuite.audit.domain.AuditAction;
import org.apache.bifromq.testsuite.security.application.SystemUserService;
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
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class SystemUserController {

    private final SystemUserService systemUserService;
    private final AuditLogService auditLogService;

    @GetMapping
    public Mono<ApiResponse<PageInfo<SystemUserService.SystemUserResponse>>> list(
        @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
        @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize) {
        return systemUserService.list(pageNum, pageSize)
            .map(ApiResponse::success);
    }

    @PostMapping
    public Mono<ApiResponse<SystemUserService.SystemUserResponse>> create(
        @RequestBody SystemUserService.CreateUserRequest request,
        ServerWebExchange exchange) {
        return systemUserService.create(request)
            .flatMap(user -> auditLogService.record(exchange, AuditAction.USER_CREATE, "USER", user.id(), true,
                    "Create user")
                .thenReturn(ApiResponse.success(user)));
    }

    @PutMapping("/{id}")
    public Mono<ApiResponse<SystemUserService.SystemUserResponse>> update(
        @PathVariable String id,
        @RequestBody SystemUserService.UpdateUserRequest request,
        ServerWebExchange exchange) {
        return systemUserService.update(id, request)
            .flatMap(user -> auditLogService.record(exchange, AuditAction.USER_UPDATE, "USER", id, true,
                    "Update user")
                .thenReturn(ApiResponse.success(user)));
    }

    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(@PathVariable String id, ServerWebExchange exchange) {
        return systemUserService.delete(id)
            .then(auditLogService.record(exchange, AuditAction.USER_DELETE, "USER", id, true, "Delete user"))
            .thenReturn(ApiResponse.success());
    }

    @PostMapping("/{id}/reset-password")
    public Mono<ApiResponse<Void>> resetPassword(
        @PathVariable String id,
        @RequestBody SystemUserService.ResetPasswordRequest request,
        ServerWebExchange exchange) {
        return systemUserService.resetPassword(id, request)
            .then(auditLogService.record(exchange, AuditAction.USER_RESET_PASSWORD, "USER", id, true,
                "Reset user password"))
            .thenReturn(ApiResponse.success());
    }
}
