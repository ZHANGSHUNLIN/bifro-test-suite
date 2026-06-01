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

package org.apache.bifromq.testsuite.app.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyStore;
import java.time.LocalDateTime;
import java.util.Base64;
import org.apache.bifromq.testsuite.app.bean.cert.KeyStoreData;
import org.apache.bifromq.testsuite.app.bean.cert.TlsCertificateCreateReq;
import org.apache.bifromq.testsuite.app.bean.cert.TlsCertificateResp;
import org.apache.bifromq.testsuite.app.bean.cert.TlsCertificateUpdateReq;
import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.repository.MqttBrokerRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.certificate.model.CertType;
import org.apache.bifromq.testsuite.certificate.model.TlsCertificate;
import org.apache.bifromq.testsuite.certificate.service.CertCipher;
import org.apache.bifromq.testsuite.certificate.service.CertConverter;
import org.apache.bifromq.testsuite.certificate.service.TlsCertificateService;
import org.apache.bifromq.testsuite.web.ApiException;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CertificateManagerTest {

    @Mock
    private TlsCertificateService certificateService;

    @Mock
    private CertCipher cipher;

    @Mock
    private CertConverter converter;

    @Mock
    private MqttBrokerRepository brokerRepository;

    @Mock
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    @InjectMocks
    private CertificateManager certificateManager;

    @Test
    void create_clientCertificate_shouldEncryptContentBeforeDelegating() {
        TlsCertificateCreateReq request = new TlsCertificateCreateReq();
        request.setName("client-cert");
        request.setType(CertType.CLIENT);
        request.setCertContent("plain-cert");
        request.setKeyContent("plain-key");
        TlsCertificate saved = certificate("client-1", CertType.CLIENT);

        when(cipher.encrypt("plain-cert")).thenReturn("encrypted-cert");
        when(cipher.encrypt("plain-key")).thenReturn("encrypted-key");
        when(certificateService.create(any(TlsCertificate.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(certificateManager.create(request))
            .assertNext(response -> {
                assertThat(response.isSuccess()).isTrue();
                assertThat(response.getData().getId()).isEqualTo("client-1");
            })
            .verifyComplete();

        ArgumentCaptor<TlsCertificate> captor = ArgumentCaptor.forClass(TlsCertificate.class);
        verify(certificateService).create(captor.capture());
        assertThat(captor.getValue().getCertContent()).isEqualTo("encrypted-cert");
        assertThat(captor.getValue().getKeyContent()).isEqualTo("encrypted-key");
    }

    @Test
    void getAllByType_shouldReturnSafeResponsesWithoutSecretFields() {
        TlsCertificate certificate = certificate("cert-1", CertType.CLIENT);
        certificate.setCertContent("encrypted-cert");
        certificate.setKeyContent("encrypted-key");

        when(certificateService.getByType(CertType.CLIENT)).thenReturn(Flux.just(certificate));

        StepVerifier.create(certificateManager.getAllByType(CertType.CLIENT))
            .assertNext(response -> {
                assertThat(response.getId()).isEqualTo("cert-1");
                assertThat(response.getName()).isEqualTo("cert-1-name");
                assertThat(response).isInstanceOf(TlsCertificateResp.class);
            })
            .verifyComplete();
    }

    @Test
    void delete_caCertificateUsedByBroker_shouldReject() {
        TlsCertificate certificate = certificate("ca-1", CertType.CA);
        MqttBroker broker = MqttBroker.builder().id("broker-1").name("broker-a").caCertId("ca-1").build();

        when(certificateService.getById("ca-1")).thenReturn(Mono.just(certificate));
        when(brokerRepository.findByCaCertId("ca-1")).thenReturn(Flux.just(broker));

        StepVerifier.create(certificateManager.delete("ca-1"))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(ApiException.class);
                assertThat(error.getMessage()).isEqualTo("error.cert.usedByBrokers");
            })
            .verify();

        verify(certificateService, never()).delete("ca-1");
    }

    @Test
    void update_clientCertificateUsedByTask_shouldReject() {
        TlsCertificate certificate = certificate("client-1", CertType.CLIENT);
        TaskInfoMetadata task = TaskInfoMetadata.builder().taskId("task-1").taskName("task-a").build();
        TlsCertificateUpdateReq request = new TlsCertificateUpdateReq();
        request.setName("new-name");

        when(certificateService.getById("client-1")).thenReturn(Mono.just(certificate));
        when(taskInfoMetadataRepository.findByClientCertId("client-1")).thenReturn(Flux.just(task));

        StepVerifier.create(certificateManager.update("client-1", request))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(ApiException.class);
                assertThat(error.getMessage()).isEqualTo("error.cert.usedByTasks");
            })
            .verify();

        verify(certificateService, never()).update("client-1", "new-name");
    }

    @Test
    void delete_unusedCertificate_shouldDelete() {
        TlsCertificate certificate = certificate("ca-1", CertType.CA);

        when(certificateService.getById("ca-1")).thenReturn(Mono.just(certificate));
        when(brokerRepository.findByCaCertId("ca-1")).thenReturn(Flux.empty());
        when(certificateService.delete("ca-1")).thenReturn(Mono.empty());

        StepVerifier.create(certificateManager.delete("ca-1"))
            .assertNext(response -> assertThat(response.isSuccess()).isTrue())
            .verifyComplete();

        verify(certificateService).delete("ca-1");
    }

    @Test
    void getKeyStoreData_shouldReturnBase64StoreAndPassword() throws Exception {
        TlsCertificate certificate = certificate("ca-1", CertType.CA);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        when(certificateService.getById("ca-1")).thenReturn(Mono.just(certificate));
        when(converter.toKeyStore(certificate, cipher)).thenReturn(keyStore);
        when(converter.getKeyStorePassword()).thenReturn("changeit");

        StepVerifier.create(certificateManager.getKeyStoreData("ca-1"))
            .assertNext(response -> {
                KeyStoreData data = response.getData();
                assertThat(data.getPassword()).isEqualTo("changeit");
                assertThat(Base64.getDecoder().decode(data.getKeyStore())).isNotEmpty();
            })
            .verifyComplete();

        verify(converter).toKeyStore(certificate, cipher);
    }

    @Test
    void getKeyStoreData_certificateNotFound_shouldReject() {
        when(certificateService.getById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(certificateManager.getKeyStoreData("missing"))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(ApiException.class);
                assertThat(error.getMessage()).isEqualTo("error.cert.notFound");
            })
            .verify();
    }

    private TlsCertificate certificate(String id, CertType type) {
        TlsCertificate certificate = new TlsCertificate();
        certificate.setId(id);
        certificate.setName(id + "-name");
        certificate.setType(type);
        certificate.setFingerprint(id + "-fingerprint");
        certificate.setCreatedAt(LocalDateTime.now());
        certificate.setUpdatedAt(LocalDateTime.now());
        return certificate;
    }
}
