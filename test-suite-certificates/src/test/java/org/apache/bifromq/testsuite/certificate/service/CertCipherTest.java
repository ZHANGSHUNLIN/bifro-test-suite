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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CertCipherTest {

    private final CertCipher cipher = new CertCipher("9tmCyhwsSnP87yH_zU7i06bNiOTSKSILX3cw3xkThkM");

    @Test
    void testEncryptDecrypt_success() {
        
        String plainText = "Hello, World! This is a test certificate content.";

        
        String encrypted = cipher.encrypt(plainText);
        String decrypted = cipher.decrypt(encrypted);

        
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    void testEncrypt_differentOutputsForSameInput() {
        
        String plainText = "Test certificate content";

        
        String encrypted1 = cipher.encrypt(plainText);
        String encrypted2 = cipher.encrypt(plainText);

        
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    void testDecrypt_success() {
        
        String plainText = "Another test string";
        String encrypted = cipher.encrypt(plainText);

        
        String decrypted = cipher.decrypt(encrypted);

        
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    void testEncryptDecrypt_emptyString() {
        
        String plainText = "";

        
        String encrypted = cipher.encrypt(plainText);
        String decrypted = cipher.decrypt(encrypted);

        
        assertThat(decrypted).isEmpty();
    }

    @Test
    void testEncryptDecrypt_longContent() {
        
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN CERTIFICATE-----\n");
        for (int i = 0; i < 100; i++) {
            sb.append("MIICxjCCAk+gAwIBAgIJAJx2w7ZfM3s5MA0GCSqGSIb3DQEBCwUAMDExCzAJBgNV\n");
        }
        sb.append("-----END CERTIFICATE-----\n");
        String plainText = sb.toString();

        
        String encrypted = cipher.encrypt(plainText);
        String decrypted = cipher.decrypt(encrypted);

        
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    void testEncryptDecrypt_specialCharacters() {
        
        String plainText = "-----BEGIN CERTIFICATE-----\n" +
            "Test with special chars: !@#$%^&*()_+-={}[]|\\:\";'<>,.?/~`\n" +
            "And Unicode: hello world 🌍\n" +
            "-----END CERTIFICATE-----";

        
        String encrypted = cipher.encrypt(plainText);
        String decrypted = cipher.decrypt(encrypted);

        
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    void testDecrypt_invalidData_throwsException() {
        
        String invalidData = "not-valid-base64-data";

        
        assertThatThrownBy(() -> cipher.decrypt(invalidData))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testDecrypt_shortData_throwsException() {
        
        String shortData = "aGVsbG8="; 

        
        assertThatThrownBy(() -> cipher.decrypt(shortData))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testEncrypt_outputIsBase64() {
        
        String plainText = "Test";

        
        String encrypted = cipher.encrypt(plainText);

        
        assertThat(encrypted).matches("^[A-Za-z0-9+/]+={0,2}$");
    }

    @Test
    void testEncryptDecrypt_multipleTimesConsistency() {
        
        String plainText = "Certificate content for multiple decryption test";

        
        String encrypted = cipher.encrypt(plainText);
        String decrypted1 = cipher.decrypt(encrypted);
        String decrypted2 = cipher.decrypt(encrypted);
        String decrypted3 = cipher.decrypt(encrypted);

        
        assertThat(decrypted1).isEqualTo(plainText);
        assertThat(decrypted2).isEqualTo(plainText);
        assertThat(decrypted3).isEqualTo(plainText);
    }
}
