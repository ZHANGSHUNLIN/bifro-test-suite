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

package org.apache.bifromq.testsuite.certificate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.certificate.model.CertType;
import org.apache.bifromq.testsuite.certificate.model.TlsCertificate;
import org.apache.bifromq.testsuite.certificate.repository.TlsCertificateRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TlsCertificateServiceTest {

    @Mock
    private TlsCertificateRepository repository;

    @Mock
    private CertCipher cipher;

    @Mock
    private CertParser parser;

    private TlsCertificateService service;

    @BeforeEach
    void setUp() {
        service = new TlsCertificateService(repository, cipher, parser);
    }

    @Test
    void testCreate_caCertificate_success() {
        
        String encryptedCert = "encrypted-cert-content";
        String plainCert = "-----BEGIN CERTIFICATE-----\nplain-cert\n-----END CERTIFICATE-----";
        TlsCertificate cert = new TlsCertificate();
        cert.setName("Test CA Certificate");
        cert.setType(CertType.CA);
        cert.setCertContent(encryptedCert);

        when(cipher.decrypt(encryptedCert)).thenReturn(plainCert);
        when(parser.parseCertificate(anyString())).thenReturn(createMockCertInfo("abc123def456"));
        when(repository.existsByFingerprint(anyString())).thenReturn(Mono.just(false));
        when(repository.save(any(TlsCertificate.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        
        StepVerifier.create(service.create(cert))
            .assertNext(savedCert -> {
                assertThat(savedCert.getName()).isEqualTo("Test CA Certificate");
                assertThat(savedCert.getType()).isEqualTo(CertType.CA);
                assertThat(savedCert.getFingerprint()).isNotNull();
                assertThat(savedCert.getSubjectDN()).isNotNull();
                assertThat(savedCert.getIssuerDN()).isNotNull();
                assertThat(savedCert.getCreatedAt()).isNotNull();
                assertThat(savedCert.getUpdatedAt()).isNotNull();
            })
            .verifyComplete();

        
        verify(cipher).decrypt(encryptedCert);
        verify(parser).parseCertificate(plainCert);
        verify(repository).existsByFingerprint(anyString());
        verify(repository).save(any(TlsCertificate.class));
    }

    @Test
    void testCreate_caCertificate_withPrivateKey_throwsException() {
        
        TlsCertificate cert = new TlsCertificate();
        cert.setName("Test CA");
        cert.setType(CertType.CA);
        cert.setCertContent("encrypted-cert");
        cert.setKeyContent("encrypted-key");

        
        StepVerifier.create(service.create(cert))
            .expectErrorMessage("error.cert.caNoPrivateKey")
            .verify();

        verify(cipher, never()).decrypt(anyString());
        verify(parser, never()).parseCertificate(anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void testCreate_clientCertificate_success() {
        
        String encryptedCert = "encrypted-cert-content";
        String encryptedKey = "encrypted-key-content";
        String plainCert = "-----BEGIN CERTIFICATE-----\nclient-cert\n-----END CERTIFICATE-----";
        TlsCertificate cert = new TlsCertificate();
        cert.setName("Test Client Certificate");
        cert.setType(CertType.CLIENT);
        cert.setCertContent(encryptedCert);
        cert.setKeyContent(encryptedKey);

        when(cipher.decrypt(encryptedCert)).thenReturn(plainCert);
        when(cipher.decrypt(encryptedKey)).thenReturn("decrypted-key");
        when(parser.parseCertificate(anyString())).thenReturn(createMockCertInfo("client-fingerprint-123"));
        when(repository.existsByFingerprint(anyString())).thenReturn(Mono.just(false));
        when(repository.save(any(TlsCertificate.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        
        StepVerifier.create(service.create(cert))
            .assertNext(savedCert -> {
                assertThat(savedCert.getName()).isEqualTo("Test Client Certificate");
                assertThat(savedCert.getType()).isEqualTo(CertType.CLIENT);
                assertThat(savedCert.getFingerprint()).isEqualTo("client-fingerprint-123");
            })
            .verifyComplete();

        verify(cipher).decrypt(encryptedCert);
        verify(cipher).decrypt(encryptedKey);
        verify(parser).parseCertificate(plainCert);
        verify(repository).existsByFingerprint(anyString());
        verify(repository).save(any(TlsCertificate.class));
    }

    @Test
    void testCreate_clientCertificate_withoutPrivateKey_throwsException() {
        
        TlsCertificate cert = new TlsCertificate();
        cert.setName("Test Client");
        cert.setType(CertType.CLIENT);
        cert.setCertContent("encrypted-cert");
        cert.setKeyContent(null);

        
        StepVerifier.create(service.create(cert))
            .expectErrorMessage("error.cert.clientNeedsPrivateKey")
            .verify();

        verify(cipher, never()).decrypt(anyString());
        verify(parser, never()).parseCertificate(anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void testCreate_duplicateFingerprint_throwsException() {
        
        String encryptedCert = "encrypted-cert";
        String plainCert = "-----BEGIN CERTIFICATE-----\ncert\n-----END CERTIFICATE-----";
        TlsCertificate cert = new TlsCertificate();
        cert.setName("Test Certificate");
        cert.setType(CertType.CA);
        cert.setCertContent(encryptedCert);

        when(cipher.decrypt(encryptedCert)).thenReturn(plainCert);
        when(parser.parseCertificate(anyString())).thenReturn(createMockCertInfo("duplicate-fingerprint"));
        when(repository.existsByFingerprint("duplicate-fingerprint")).thenReturn(Mono.just(true));

        
        StepVerifier.create(service.create(cert))
            .expectErrorMessage("error.cert.fingerprintExists")
            .verify();

        verify(cipher).decrypt(encryptedCert);
        verify(parser).parseCertificate(plainCert);
        verify(repository).existsByFingerprint("duplicate-fingerprint");
        verify(repository, never()).save(any());
    }

    @Test
    void testCreate_invalidCertificate_throwsException() {
        
        String encryptedCert = "invalid-cert";
        TlsCertificate cert = new TlsCertificate();
        cert.setName("Invalid Certificate");
        cert.setType(CertType.CA);
        cert.setCertContent(encryptedCert);

        when(cipher.decrypt(encryptedCert)).thenThrow(new RuntimeException("Invalid certificate format"));

        
        StepVerifier.create(service.create(cert))
            .consumeErrorWith(e -> assertThat(e.getMessage()).contains("error.cert.invalidFormat"))
            .verify();

        verify(cipher).decrypt(encryptedCert);
        verify(parser, never()).parseCertificate(anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void testUpdate_success() {
        
        String id = "cert-id-123";
        String newName = "Updated Certificate Name";
        TlsCertificate existingCert = new TlsCertificate();
        existingCert.setId(id);
        existingCert.setName("Original Name");
        existingCert.setType(CertType.CA);
        existingCert.setCertContent("encrypted-cert");
        existingCert.setFingerprint("fingerprint-123");

        when(repository.findById(id)).thenReturn(Mono.just(existingCert));
        when(repository.existsByFingerprint("fingerprint-123")).thenReturn(Mono.just(true));
        when(repository.save(any(TlsCertificate.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        
        StepVerifier.create(service.update(id, newName))
            .assertNext(updatedCert -> {
                assertThat(updatedCert.getName()).isEqualTo(newName);
                assertThat(updatedCert.getUpdatedAt()).isNotNull();
                assertThat(updatedCert.getType()).isEqualTo(CertType.CA);
            })
            .verifyComplete();

        ArgumentCaptor<TlsCertificate> captor = ArgumentCaptor.forClass(TlsCertificate.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(newName);
    }

    @Test
    void testUpdate_certificateNotFound_throwsException() {
        
        String id = "non-existent-id";
        when(repository.findById(id)).thenReturn(Mono.empty());

        
        StepVerifier.create(service.update(id, "New Name"))
            .expectErrorMessage("error.cert.notFound")
            .verify();

        verify(repository).findById(id);
        verify(repository, never()).save(any());
    }

    @Test
    void testDelete_success() {
        
        String id = "cert-id-to-delete";
        TlsCertificate cert = new TlsCertificate();
        cert.setId(id);
        cert.setName("Certificate to Delete");
        cert.setType(CertType.CA);
        cert.setCertContent("encrypted-cert");

        when(repository.findById(id)).thenReturn(Mono.just(cert));
        when(repository.delete(any(TlsCertificate.class))).thenReturn(Mono.empty());

        
        StepVerifier.create(service.delete(id))
            .verifyComplete();

        verify(repository).findById(id);
        verify(repository).delete(cert);
    }

    @Test
    void testDelete_certificateNotFound_throwsException() {
        
        String id = "non-existent-cert";
        when(repository.findById(id)).thenReturn(Mono.empty());

        
        StepVerifier.create(service.delete(id))
            .expectErrorMessage("error.cert.notFound")
            .verify();

        verify(repository).findById(id);
        verify(repository, never()).delete(any());
    }

    @Test
    void testGetById_success() {
        
        String id = "cert-id-456";
        TlsCertificate cert = new TlsCertificate();
        cert.setId(id);
        cert.setName("Test Certificate");
        cert.setType(CertType.CA);
        cert.setCertContent("encrypted-cert");

        when(repository.findById(id)).thenReturn(Mono.just(cert));

        
        StepVerifier.create(service.getById(id))
            .expectNext(cert)
            .verifyComplete();

        verify(repository).findById(id);
    }

    @Test
    void testGetByType_success() {
        
        CertType type = CertType.CA;
        TlsCertificate cert1 = new TlsCertificate();
        cert1.setId("cert-1");
        cert1.setName("CA Cert 1");
        cert1.setType(CertType.CA);
        cert1.setCertContent("encrypted-1");

        TlsCertificate cert2 = new TlsCertificate();
        cert2.setId("cert-2");
        cert2.setName("CA Cert 2");
        cert2.setType(CertType.CA);
        cert2.setCertContent("encrypted-2");

        when(repository.findByType(type)).thenReturn(Flux.just(cert1, cert2));

        
        StepVerifier.create(service.getByType(type))
            .expectNext(cert1, cert2)
            .verifyComplete();

        verify(repository).findByType(type);
    }

    @Test
    void testGetAll_success() {
        
        TlsCertificate cert1 = new TlsCertificate();
        cert1.setId("cert-1");
        cert1.setName("Certificate 1");
        cert1.setType(CertType.CA);
        cert1.setCertContent("encrypted-1");

        TlsCertificate cert2 = new TlsCertificate();
        cert2.setId("cert-2");
        cert2.setName("Certificate 2");
        cert2.setType(CertType.CLIENT);
        cert2.setCertContent("encrypted-2");

        when(repository.findAll()).thenReturn(Flux.just(cert1, cert2));

        
        StepVerifier.create(service.getAll())
            .expectNext(cert1, cert2)
            .verifyComplete();

        verify(repository).findAll();
    }

    
    private CertInfo createMockCertInfo(String fingerprint) {
        CertInfo info = new CertInfo();
        info.setFingerprint(fingerprint);
        info.setSubjectDN("CN=test");
        info.setIssuerDN("CN=test-ca");
        info.setValidFrom(LocalDateTime.now());
        info.setValidTo(LocalDateTime.now().plusYears(1));
        return info;
    }
}
