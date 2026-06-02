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

package org.apache.bifromq.testsuite.configs;

import javax.net.ssl.SSLContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.bifromq.testsuite.WillConfig;
import org.apache.bifromq.testsuite.client.AuthType;
import org.apache.bifromq.testsuite.client.LocalPortRangeConfig;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MqttClientConfig {

    private String protocol;
    private String localAddress;
    private int localPort = 0;
    @Builder.Default
    private LocalPortRangeConfig localPortRangeConfig = new LocalPortRangeConfig();
    private String host;
    private int port;
    private int keepAliveInSec = 120;
    private String clientId;
    private boolean isEmptyClientId = false;
    private int ackTimeoutInSec = 120;
    @Builder.Default
    private int reconnectMaxAttempts = 2;
    private int reconnectIntervalInMs = 1000;
    private int connectTimeoutInMs = 30000;
    private int maxInflightQueue = 1000;
    private int thingIdStartAt = 0;
    private String username;
    private String password;
    private SSLContext sslContext;
    private boolean cleanSession;
    private long expiryIntervalInSec;
    private AuthType authType;


    private WillConfig willConfig;


    private String caCertPem;


    private String clientCertPem;


    private String clientKeyPem;

}
