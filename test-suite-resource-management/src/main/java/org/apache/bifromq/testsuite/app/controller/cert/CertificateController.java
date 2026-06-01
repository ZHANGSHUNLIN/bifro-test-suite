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

package org.apache.bifromq.testsuite.app.controller.cert;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.bean.cert.TlsCertificateCreateReq;
import org.apache.bifromq.testsuite.app.bean.cert.TlsCertificateResp;
import org.apache.bifromq.testsuite.app.bean.cert.TlsCertificateUpdateReq;
import org.apache.bifromq.testsuite.app.certificate.CertificateManager;
import org.apache.bifromq.testsuite.app.controller.ApiController;
import org.apache.bifromq.testsuite.audit.application.AuditLogService;
import org.apache.bifromq.testsuite.audit.domain.AuditAction;
import org.apache.bifromq.testsuite.certificate.model.CertType;
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
@Tag(name = "Certificate Management", description = "TLS certificate management API, supports CA and client certificates")
@RestController
@RequestMapping("/api/certificates")
public class CertificateController implements ApiController {

    @Resource
    private CertificateManager certificateManager;

    @Resource
    private AuditLogService auditLogService;

    @Operation(summary = "Create Certificate", description = "Create a new TLS certificate (CA or CLIENT)")
    @PostMapping
    public Mono<ApiResponse<TlsCertificateResp>> create(
        @Valid @RequestBody @Parameter(description = "Certificate create request") TlsCertificateCreateReq request,
        ServerWebExchange exchange) {
        return certificateManager.create(request)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.CERT_CREATE, "CERTIFICATE",
                    response.getData() == null ? null : response.getData().getId(), response.isSuccess(),
                    "Create certificate")
                .thenReturn(response));
    }

    @Operation(summary = "Update Certificate", description = "Update certificate name")
    @PutMapping("/{id}")
    public Mono<ApiResponse<TlsCertificateResp>> update(
        @PathVariable(name = "id") @Parameter(description = "Certificate ID") String id,
        @Valid @RequestBody @Parameter(description = "Certificate update request") TlsCertificateUpdateReq request,
        ServerWebExchange exchange) {
        return certificateManager.update(id, request)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.CERT_UPDATE, "CERTIFICATE", id,
                    response.isSuccess(), "Update certificate")
                .thenReturn(response));
    }

    @Operation(summary = "Delete Certificate", description = "Delete certificate by ID")
    @DeleteMapping("/{id}")
    public Mono<ApiResponse<Void>> delete(
        @PathVariable(name = "id") @Parameter(description = "Certificate ID") String id,
        ServerWebExchange exchange) {
        return certificateManager.delete(id)
            .flatMap(response -> auditLogService.record(exchange, AuditAction.CERT_DELETE, "CERTIFICATE", id,
                    response.isSuccess(), "Delete certificate")
                .thenReturn(response));
    }

    @Operation(summary = "Get Certificate List", description = "Paginated certificate list query")
    @GetMapping
    public Mono<ApiResponse<PageInfo<TlsCertificateResp>>> list(
        @Parameter(description = "Certificate type: CA or CLIENT") @RequestParam(name = "type", required = false)
        CertType type,
        @Parameter(description = "Keyword") @RequestParam(name = "keyword", required = false) String keyword,
        @Parameter(description = "Page number", example = "1") @RequestParam(name = "pageNum", defaultValue = "1")
        Integer pageNum,
        @Parameter(description = "Page size", example = "20") @RequestParam(name = "pageSize", defaultValue = "20")
        Integer pageSize) {
        return certificateManager.list(type, keyword, pageNum, pageSize);
    }

    @Operation(summary = "Get Certificate Details", description = "Get certificate details by ID")
    @GetMapping("/{id}")
    public Mono<ApiResponse<TlsCertificateResp>> get(
        @PathVariable(name = "id") @Parameter(description = "Certificate ID") String id) {
        return certificateManager.getById(id);
    }

    @Operation(summary = "Get All Certificates", description = "Get all certificates (no pagination, for dropdown)")
    @GetMapping("/all")
    public Flux<TlsCertificateResp> getAll(
        @Parameter(description = "Certificate type: CA or CLIENT") @RequestParam(name = "type") CertType type) {
        return certificateManager.getAllByType(type);
    }

}
