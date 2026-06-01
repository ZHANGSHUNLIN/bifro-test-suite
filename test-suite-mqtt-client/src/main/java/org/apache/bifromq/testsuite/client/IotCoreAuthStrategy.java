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

package org.apache.bifromq.testsuite.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;

public class IotCoreAuthStrategy implements AuthStrategy {

    private final String tenantId;
    private final String secretKey;
    private final String thingIdPrefix;


    public IotCoreAuthStrategy(String tenantId, String secretKey, String thingIdPrefix) {
        this.tenantId = tenantId;
        this.secretKey = secretKey;
        this.thingIdPrefix = thingIdPrefix == null ? "iotcore_" : thingIdPrefix;
    }

    @Override
    public AuthResult apply(MqttClientConfig.MqttClientConfigBuilder builder,
                            String clientId,
                            AtomicInteger subscribeCount) {

        String thingId = thingIdPrefix + subscribeCount.getAndIncrement();

        String username = String.format("iotcore|%s|%s", tenantId, thingId);

        String password = generatePassword(secretKey, clientId, thingId);

        builder.username(username)
            .password(password)
            .tenantId(tenantId);

        return new AuthResult(builder, thingId);
    }


    private String generatePassword(String secretKey, String clientId, String thingId) {
        try {
            String data = secretKey + clientId + thingId;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            HexFormat hexFormat = HexFormat.of();
            return hexFormat.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
