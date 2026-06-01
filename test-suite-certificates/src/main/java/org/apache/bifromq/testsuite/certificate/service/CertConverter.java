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

import org.apache.bifromq.testsuite.i18n.Messages;

import org.apache.bifromq.testsuite.certificate.model.CertType;
import org.apache.bifromq.testsuite.certificate.model.TlsCertificate;
import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CertConverter {

    private static final String KEYSTORE_TYPE = "PKCS12";

    private static final String KEYSTORE_PASSWORD = "changeit";

    private static final String KEY_ALIAS = "client";

    
    public KeyStore toKeyStore(TlsCertificate cert, CertCipher cipher) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            keyStore.load(null, null);

            String certPem = cipher.decrypt(cert.getCertContent());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate certificate = cf.generateCertificate(new ByteArrayInputStream(certPem.getBytes()));

            if (cert.getType() == CertType.CLIENT) {
                String keyPem = cipher.decrypt(cert.getKeyContent());
                PrivateKey privateKey = parsePrivateKey(keyPem);
                keyStore.setEntry(KEY_ALIAS,
                    new KeyStore.PrivateKeyEntry(privateKey, new Certificate[] {certificate}),
                    new KeyStore.PasswordProtection(KEYSTORE_PASSWORD.toCharArray()));
            } else {
                keyStore.setCertificateEntry(KEY_ALIAS, certificate);
            }

            return keyStore;
        } catch (Exception e) {
            throw new IllegalStateException(Messages.get("error.cert.keystoreConvertFailed"), e);
        }
    }

    
    public String getKeyStorePassword() {
        return KEYSTORE_PASSWORD;
    }

    
    private PrivateKey parsePrivateKey(String keyPem) throws Exception {
        String normalized = keyPem.replace("\r\n", "\n").replace("\r", "\n");
        String beginMarker = "BEGIN PRIVATE KEY";
        String endMarker = "END PRIVATE KEY";

        if (!normalized.contains(beginMarker)) {
            beginMarker = "BEGIN RSA PRIVATE KEY";
            endMarker = "END RSA PRIVATE KEY";
        }

        int beginIndex = normalized.indexOf("-----" + beginMarker + "-----");
        int endIndex = normalized.indexOf("-----" + endMarker + "-----");

        if (beginIndex < 0 || endIndex < 0) {
            throw new IllegalArgumentException(Messages.get("error.cert.invalidPem"));
        }

        String content = normalized.substring(beginIndex + beginMarker.length() + 10, endIndex).trim();
        content = content.replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(content);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }
}