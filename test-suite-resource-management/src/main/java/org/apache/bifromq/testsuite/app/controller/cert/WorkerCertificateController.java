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
import org.apache.bifromq.testsuite.app.bean.cert.KeyStoreData;
import org.apache.bifromq.testsuite.app.certificate.CertificateManager;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "Worker Certificate", description = "Internal TLS certificate API for workers")
@RestController
@RequestMapping("/api/worker/certificates")
public class WorkerCertificateController {

    @Resource
    private CertificateManager certificateManager;

    @Operation(summary = "Get Worker Certificate KeyStore", description = "Internal API for workers to fetch KeyStore")
    @GetMapping("/{id}")
    public Mono<ApiResponse<KeyStoreData>> getKeyStore(
        @PathVariable(name = "id") @Parameter(description = "Certificate ID") String id) {
        return certificateManager.getKeyStoreData(id);
    }
}
