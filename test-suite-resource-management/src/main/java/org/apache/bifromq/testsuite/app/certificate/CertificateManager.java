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

import jakarta.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.web.ApiException;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.web.PageInfo;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class CertificateManager {

    @Resource
    private TlsCertificateService certificateService;

    @Resource
    private CertCipher cipher;

    @Resource
    private CertConverter converter;

    @Resource
    private MqttBrokerRepository brokerRepository;

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    public Mono<ApiResponse<TlsCertificateResp>> create(TlsCertificateCreateReq request) {
        TlsCertificate certificate = new TlsCertificate();
        certificate.setName(request.getName());
        certificate.setType(request.getType());
        certificate.setCertContent(cipher.encrypt(request.getCertContent()));
        if (request.getType() == CertType.CLIENT) {
            certificate.setKeyContent(cipher.encrypt(request.getKeyContent()));
        }
        return certificateService.create(certificate)
            .map(this::toResponse)
            .map(ApiResponse::success);
    }

    public Mono<ApiResponse<TlsCertificateResp>> update(String id, TlsCertificateUpdateReq request) {
        return certificateService.getById(id)
            .switchIfEmpty(Mono.error(new ApiException(Messages.get("error.cert.notFound"))))
            .flatMap(this::rejectIfReferenced)
            .flatMap(certificate -> certificateService.update(id, request.getName()))
            .map(this::toResponse)
            .map(ApiResponse::success);
    }

    public Mono<ApiResponse<Void>> delete(String id) {
        return certificateService.getById(id)
            .switchIfEmpty(Mono.error(new ApiException(Messages.get("error.cert.notFound"))))
            .flatMap(this::rejectIfReferenced)
            .flatMap(certificate -> certificateService.delete(id))
            .thenReturn(ApiResponse.success());
    }

    public Flux<TlsCertificateResp> getAllByType(CertType type) {
        return certificateService.getByType(type).map(this::toResponse);
    }

    public Mono<ApiResponse<TlsCertificateResp>> getById(String id) {
        return certificateService.getById(id)
            .map(this::toResponse)
            .map(ApiResponse::success);
    }

    public Mono<ApiResponse<PageInfo<TlsCertificateResp>>> list(
        CertType type, Integer pageNum, Integer pageSize) {
        return list(type, null, pageNum, pageSize);
    }

    public Mono<ApiResponse<PageInfo<TlsCertificateResp>>> list(
        CertType type, String keyword, Integer pageNum, Integer pageSize) {
        Flux<TlsCertificate> flux = type != null
            ? certificateService.getByType(type)
            : certificateService.getAll();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.trim().toLowerCase();
            flux = flux.filter(cert -> containsIgnoreCase(cert.getName(), lowerKeyword)
                || containsIgnoreCase(cert.getSubjectDN(), lowerKeyword)
                || containsIgnoreCase(cert.getIssuerDN(), lowerKeyword)
                || containsIgnoreCase(cert.getFingerprint(), lowerKeyword));
        }

        return flux
            .collectList()
            .map(list -> paginate(list, pageNum, pageSize))
            .map(ApiResponse::success);
    }

    private boolean containsIgnoreCase(String value, String lowerKeyword) {
        return value != null && value.toLowerCase().contains(lowerKeyword);
    }

    public Mono<TlsCertificateKeyStore> getKeyStore(String id) {
        return certificateService.getById(id)
            .switchIfEmpty(Mono.error(new ApiException(Messages.get("error.cert.notFound"))))
            .map(cert -> {
                var keyStore = converter.toKeyStore(cert, cipher);
                return new TlsCertificateKeyStore(keyStore, converter.getKeyStorePassword());
            });
    }

    public Mono<ApiResponse<KeyStoreData>> getKeyStoreData(String id) {
        return getKeyStore(id)
            .map(keyStore -> {
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    keyStore.keyStore().store(out, keyStore.password().toCharArray());
                    return new KeyStoreData(Base64.getEncoder().encodeToString(out.toByteArray()),
                        keyStore.password());
                } catch (Exception e) {
                    throw new IllegalStateException(Messages.get("error.cert.keystoreConvertFailed"), e);
                }
            })
            .map(ApiResponse::success);
    }

    private Mono<TlsCertificate> rejectIfReferenced(TlsCertificate certificate) {
        Flux<String> references = certificate.getType() == CertType.CA
            ? brokerRepository.findByCaCertId(certificate.getId()).map(this::brokerRefName)
            : taskInfoMetadataRepository.findByClientCertId(certificate.getId()).map(this::taskRefName);

        return references.collectList()
            .flatMap(refs -> {
                if (refs.isEmpty()) {
                    return Mono.just(certificate);
                }
                String key = certificate.getType() == CertType.CA
                    ? "error.cert.usedByBrokers" : "error.cert.usedByTasks";
                return Mono.error(new ApiException(Messages.get(key, String.join(", ", refs))));
            });
    }

    private String brokerRefName(MqttBroker broker) {
        if (broker.getName() != null && !broker.getName().isBlank()) {
            return broker.getName();
        }
        return broker.getBrokerId() != null ? broker.getBrokerId() : broker.getId();
    }

    private String taskRefName(TaskInfoMetadata task) {
        if (task.getTaskName() != null && !task.getTaskName().isBlank()) {
            return task.getTaskName();
        }
        return task.getTaskId();
    }

    private TlsCertificateResp toResponse(TlsCertificate certificate) {
        TlsCertificateResp resp = new TlsCertificateResp();
        resp.setId(certificate.getId());
        resp.setName(certificate.getName());
        resp.setType(certificate.getType());
        resp.setValidFrom(certificate.getValidFrom());
        resp.setValidTo(certificate.getValidTo());
        resp.setSubjectDN(certificate.getSubjectDN());
        resp.setIssuerDN(certificate.getIssuerDN());
        resp.setFingerprint(certificate.getFingerprint());
        resp.setCreatedAt(certificate.getCreatedAt());
        resp.setUpdatedAt(certificate.getUpdatedAt());
        return resp;
    }

    private PageInfo<TlsCertificateResp> paginate(List<TlsCertificate> list, int pageNum, int pageSize) {
        int total = list.size();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<TlsCertificate> pageList = fromIndex < total ? list.subList(fromIndex, toIndex) : List.of();

        PageInfo<TlsCertificateResp> pageInfo = new PageInfo<>();
        pageInfo.setContent(pageList.stream().map(this::toResponse).toList());
        pageInfo.setTotalElements(total);
        pageInfo.setTotalPages(totalPages);
        pageInfo.setSize(pageSize);
        pageInfo.setNumber(pageNum - 1);
        pageInfo.setNumberOfElements(pageList.size());
        return pageInfo;
    }

    public record TlsCertificateKeyStore(java.security.KeyStore keyStore, String password) {
    }
}
