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

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.certificate.model.CertType;
import org.apache.bifromq.testsuite.certificate.model.TlsCertificate;
import org.apache.bifromq.testsuite.certificate.repository.TlsCertificateRepository;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TlsCertificateService {

    private final TlsCertificateRepository repository;

    private final CertCipher cipher;

    private final CertParser parser;


    public Mono<TlsCertificate> create(TlsCertificate certificate) {
        return Mono.just(certificate)
            .flatMap(cert -> {

                if (cert.getType() == CertType.CA) {

                    if (cert.getKeyContent() != null && !cert.getKeyContent().isBlank()) {
                        return Mono.error(new IllegalArgumentException(Messages.get("error.cert.caNoPrivateKey")));
                    }
                } else if (cert.getType() == CertType.CLIENT) {

                    if (cert.getKeyContent() == null || cert.getKeyContent().isBlank()) {
                        return Mono.error(
                            new IllegalArgumentException(Messages.get("error.cert.clientNeedsPrivateKey")));
                    }
                }
                return Mono.just(cert);
            })
            .flatMap(cert -> {
                try {
                    String certPem = cipher.decrypt(cert.getCertContent());
                    CertInfo info = parser.parseCertificate(certPem);
                    cert.setValidFrom(info.getValidFrom());
                    cert.setValidTo(info.getValidTo());
                    cert.setSubjectDN(info.getSubjectDN());
                    cert.setIssuerDN(info.getIssuerDN());
                    cert.setFingerprint(info.getFingerprint());

                    if (cert.getType() == CertType.CLIENT) {
                        cipher.decrypt(cert.getKeyContent());
                    }
                    return Mono.just(cert);
                } catch (Exception e) {
                    return Mono.error(
                        new IllegalArgumentException(Messages.get("error.cert.invalidFormat", e.getMessage())));
                }
            })
            .flatMap(cert -> repository.existsByFingerprint(cert.getFingerprint())
                .flatMap(exists -> exists
                    ? Mono.error(new IllegalStateException(Messages.get("error.cert.fingerprintExists")))
                    : Mono.just(cert)))
            .doOnNext(cert -> {
                cert.setCreatedAt(LocalDateTime.now());
                cert.setUpdatedAt(LocalDateTime.now());
            })
            .flatMap(repository::save);
    }


    public Mono<TlsCertificate> update(String id, String name) {
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException(Messages.get("error.cert.notFound"))))
            .flatMap(cert -> repository.existsByFingerprint(cert.getFingerprint())
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new IllegalStateException(Messages.get("error.cert.fingerprintMismatch")));
                    }
                    cert.setName(name);
                    cert.setUpdatedAt(LocalDateTime.now());
                    return repository.save(cert);
                }));
    }


    public Mono<Void> delete(String id) {
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException(Messages.get("error.cert.notFound"))))
            .flatMap(repository::delete);
    }


    public Mono<TlsCertificate> getById(String id) {
        return repository.findById(id);
    }


    public Flux<TlsCertificate> getByType(CertType type) {
        return repository.findByType(type);
    }


    public Flux<TlsCertificate> getAll() {
        return repository.findAll();
    }
}