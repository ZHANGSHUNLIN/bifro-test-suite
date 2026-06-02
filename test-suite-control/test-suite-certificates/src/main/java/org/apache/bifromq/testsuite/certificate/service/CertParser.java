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

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.time.ZoneId;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnControlPlane
public class CertParser {

    
    public CertInfo parseCertificate(String certPem) {
        try {
            byte[] certBytes = extractPemContent(certPem, "BEGIN CERTIFICATE", "END CERTIFICATE");
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));

            CertInfo info = new CertInfo();
            info.setValidFrom(cert.getNotBefore().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            info.setValidTo(cert.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            info.setSubjectDN(cert.getSubjectX500Principal().getName());
            info.setIssuerDN(cert.getIssuerX500Principal().getName());
            info.setFingerprint(computeFingerprint(cert));
            return info;
        } catch (Exception e) {
            throw new IllegalArgumentException(Messages.get("error.cert.invalidCertFormat", e.getMessage()), e);
        }
    }

    
    public byte[] parsePrivateKey(String keyPem) {
        try {
            
            String beginMarker = "BEGIN PRIVATE KEY";
            String endMarker = "END PRIVATE KEY";

            if (keyPem.contains(beginMarker)) {
                return extractPemContent(keyPem, beginMarker, endMarker);
            }

            
            beginMarker = "BEGIN RSA PRIVATE KEY";
            endMarker = "END RSA PRIVATE KEY";
            if (keyPem.contains(beginMarker)) {
                byte[] pkcs1Bytes = extractPemContent(keyPem, beginMarker, endMarker);
                
                return convertPkcs1ToPkcs8(pkcs1Bytes);
            }

            throw new IllegalArgumentException(Messages.get("error.cert.unsupportedKeyFormat"));
        } catch (Exception e) {
            throw new IllegalArgumentException(Messages.get("error.cert.invalidPrivateKey", e.getMessage()), e);
        }
    }

    
    private byte[] extractPemContent(String pem, String beginMarker, String endMarker) {
        String normalized = pem.replace("\r\n", "\n").replace("\r", "\n");
        int beginIndex = normalized.indexOf("-----" + beginMarker + "-----");
        int endIndex = normalized.indexOf("-----" + endMarker + "-----");

        if (beginIndex < 0 || endIndex < 0) {
            throw new IllegalArgumentException(Messages.get("error.cert.invalidPem"));
        }

        String content = normalized.substring(beginIndex + beginMarker.length() + 10, endIndex).trim();
        content = content.replaceAll("\\s+", "");
        return Base64.getDecoder().decode(content);
    }

    
    private String computeFingerprint(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(cert.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString().toLowerCase();
    }

    
    private byte[] convertPkcs1ToPkcs8(byte[] pkcs1Bytes) throws Exception {
        
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey pkcs1Key =
            (RSAPrivateCrtKey) keyFactory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(pkcs1Bytes));

        
        java.security.spec.PKCS8EncodedKeySpec pkcs8Spec =
            new java.security.spec.PKCS8EncodedKeySpec(pkcs1Key.getEncoded());
        return pkcs8Spec.getEncoded();
    }
}
