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

package org.apache.bifromq.testsuite.app.bean.validation;

import org.apache.bifromq.testsuite.certificate.model.CertType;
import org.apache.bifromq.testsuite.certificate.model.TlsCertificate;
import org.apache.bifromq.testsuite.certificate.repository.TlsCertificateRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

/**
 * Validator for {@link CertificateExists} annotation.
 * Validates that a certificate with the given ID exists in the database.
 */
@Component
public class CertificateExistsValidator implements ConstraintValidator<CertificateExists, String> {

    @Autowired
    private TlsCertificateRepository certificateRepository;

    private String type;

    @Override
    public void initialize(CertificateExists constraintAnnotation) {
        this.type = constraintAnnotation.type();
    }

    @Override
    public boolean isValid(String certId, ConstraintValidatorContext context) {
        if (certId == null || certId.isBlank()) {
            return true;
        }

        TlsCertificate cert = certificateRepository.findById(certId)
            .subscribeOn(Schedulers.boundedElastic())
            .block();
        if (cert == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Certificate not found: " + certId
            ).addConstraintViolation();
            return false;
        }

        if (!type.isEmpty() && !type.equals(cert.getType().name())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Certificate type mismatch: expected " + type + " but found " + cert.getType()
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
