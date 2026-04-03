package com.baidu.iot.test.suite.client;

/*
 * Copyright (c) 2025. The BifroMQ Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import lombok.Data;

public class MqttCloudHelper {

    @Data
    public static class UsernamePassword {
        private String username;
        private String password;
        private String clientId;
    }

    public static UsernamePassword generateUsername(String tenantId, String thingId, String psk, String clientId) {
        UsernamePassword usernamePassword = new UsernamePassword();
        long timestamp = System.currentTimeMillis();
        String username = tenantId + "/" + thingId + "/" + timestamp;
        String signingString = String.format("%s/%s/%s%s", tenantId, thingId, timestamp, clientId);
        HashCode code = Hashing.hmacMd5(psk.getBytes(StandardCharsets.UTF_8))
            .hashBytes(signingString.getBytes(StandardCharsets.UTF_8));
        usernamePassword.setUsername(username);
        usernamePassword.setPassword(code.toString());
        usernamePassword.setClientId(clientId);
        return usernamePassword;
    }

    public static void main(String[] args) {
        UsernamePassword usernamePassword =
            generateUsername("tffffffff", "demo_1", "cFF5T1VXTVRacUx4Rmpvcw==", "client_001");
        System.out.println(usernamePassword.getUsername());
        System.out.println(usernamePassword.getPassword());
    }

}
