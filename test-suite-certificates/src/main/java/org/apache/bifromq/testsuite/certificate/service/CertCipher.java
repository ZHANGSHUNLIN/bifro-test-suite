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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CertCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private static final int GCM_IV_LENGTH = 12;

    private static final int GCM_TAG_LENGTH = 128;

    private static final int KEY_BYTES = 32;

    private static final Path DEFAULT_KEY_FILE = Path.of("conf", "certificate-cipher-key");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec keySpec;

    private final SecureRandom secureRandom;

    public CertCipher(@Value("${bifro.certificates.cipher-key:}") String configuredKey) {
        this.keySpec = new SecretKeySpec(resolveKey(configuredKey), "AES");
        this.secureRandom = SECURE_RANDOM;
    }

    
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new IllegalStateException(Messages.get("error.cert.encryptFailed"), e);
        }
    }

    
    public String decrypt(String ciphertext) {
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(Messages.get("error.cert.decryptFailed"), e);
        }
    }

    private byte[] resolveKey(String configuredKey) {
        String key = configuredKey == null || configuredKey.isBlank()
            ? localKey()
            : configuredKey.trim();
        byte[] decoded = Base64.getUrlDecoder().decode(key);
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException("Certificate cipher key must be a 256-bit Base64 URL encoded value");
        }
        return decoded;
    }

    private String localKey() {
        try {
            if (Files.exists(DEFAULT_KEY_FILE)) {
                String existingKey = Files.readString(DEFAULT_KEY_FILE, StandardCharsets.UTF_8).trim();
                if (!existingKey.isBlank()) {
                    restrictOwnerAccess(DEFAULT_KEY_FILE);
                    return existingKey;
                }
            }
            Files.createDirectories(DEFAULT_KEY_FILE.getParent());
            String key = generateKey();
            Files.writeString(DEFAULT_KEY_FILE, key + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            restrictOwnerAccess(DEFAULT_KEY_FILE);
            log.info("Generated certificate cipher key at {}", DEFAULT_KEY_FILE);
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize certificate cipher key file: " + DEFAULT_KEY_FILE, e);
        }
    }

    private String generateKey() {
        byte[] bytes = new byte[KEY_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void restrictOwnerAccess(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            log.debug("POSIX file permissions are not supported for {}", path);
        }
    }
}
