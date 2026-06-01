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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.app.bean.cert.KeyStoreData;
import org.apache.bifromq.testsuite.app.bean.cert.TlsCertificateResp;
import org.apache.bifromq.testsuite.app.certificate.CertificateManager;
import org.apache.bifromq.testsuite.certificate.model.CertType;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CertificateControllerTest {

    @Mock
    private CertificateManager certificateManager;

    @InjectMocks
    private CertificateController certificateController;

    @InjectMocks
    private WorkerCertificateController workerCertificateController;

    @Test
    void getAll_shouldReturnSafeCertificateResponses() {
        TlsCertificateResp response = new TlsCertificateResp();
        response.setId("cert-1");
        response.setName("cert-name");
        response.setType(CertType.CA);

        when(certificateManager.getAllByType(CertType.CA)).thenReturn(Flux.just(response));

        StepVerifier.create(certificateController.getAll(CertType.CA))
            .assertNext(item -> {
                assertThat(item.getId()).isEqualTo("cert-1");
                assertThat(item).isInstanceOf(TlsCertificateResp.class);
            })
            .verifyComplete();
    }

    @Test
    void workerKeyStoreEndpoint_shouldReturnKeyStoreData() {
        KeyStoreData keyStoreData = new KeyStoreData("base64", "changeit");

        when(certificateManager.getKeyStoreData("cert-1")).thenReturn(Mono.just(ApiResponse.success(keyStoreData)));

        StepVerifier.create(workerCertificateController.getKeyStore("cert-1"))
            .assertNext(response -> {
                assertThat(response.isSuccess()).isTrue();
                assertThat(response.getData().getKeyStore()).isEqualTo("base64");
                assertThat(response.getData().getPassword()).isEqualTo("changeit");
            })
            .verifyComplete();
    }
}
