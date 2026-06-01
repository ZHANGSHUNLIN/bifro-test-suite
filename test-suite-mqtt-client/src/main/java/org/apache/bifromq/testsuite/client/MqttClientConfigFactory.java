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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.WillConfig;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class MqttClientConfigFactory {

    private static final int CLIENT_INDEX_PADDING = 7;

    private final String taskId;
    private final String nodeId;
    private final String nodeIdPrefix;
    private final List<TaskBrokerAddress> brokers;
    private final String username;
    private final String password;
    private final String tenantId;
    private final String thingIdPrefix;
    private final int thingIdStartAt;
    private final boolean cleanSession;
    private final int keepAliveInSec;
    private final int ackTimeoutInSec;
    private final int reconnectMaxAttempts;
    private final int reconnectIntervalInMs;
    private final int connectTimeoutInMs;
    private final int maxInflightQueue;
    private final long expiryIntervalInSec;
    private final String protocol;
    private final boolean isEmptyClientId;
    private final AuthType authType;
    private final WillConfig willConfig;
    private final LocalPortRangeConfig localPortRangeConfig;
    private final LocalAddressProvider localAddressProvider;
    private final AuthStrategy authStrategy;

    public MqttClientConfigFactory(Builder builder) {
        this.taskId = builder.taskId;
        this.nodeId = builder.nodeId == null ? "" : builder.nodeId;
        this.nodeIdPrefix = extractNodeIdPrefix(builder.nodeId);
        this.brokers = builder.brokers;
        this.username = builder.username;
        this.password = builder.password;
        this.tenantId = builder.tenantId;
        this.thingIdPrefix = builder.thingIdPrefix;
        this.thingIdStartAt = builder.thingIdStartAt;
        this.cleanSession = builder.cleanSession;
        this.keepAliveInSec = builder.keepAliveInSec;
        this.ackTimeoutInSec = builder.ackTimeoutInSec;
        this.reconnectMaxAttempts = builder.reconnectMaxAttempts;
        this.reconnectIntervalInMs = builder.reconnectIntervalInMs;
        this.connectTimeoutInMs = builder.connectTimeoutInMs;
        this.maxInflightQueue = builder.maxInflightQueue;
        this.expiryIntervalInSec = builder.expiryIntervalInSec;
        this.protocol = builder.protocol;
        this.isEmptyClientId = builder.isEmptyClientId;
        this.authType = builder.authType;
        this.willConfig = builder.willConfig;
        this.localPortRangeConfig = builder.localPortRangeConfig == null
            ? new LocalPortRangeConfig()
            : builder.localPortRangeConfig.normalized();
        this.localAddressProvider = builder.localAddressProvider;
        this.authStrategy = builder.authStrategy;
    }

    private static String extractNodeIdPrefix(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            return "node";
        }

        String prefix = nodeId.length() >= 4 ? nodeId.substring(0, 4) : String.format("%-4s", nodeId).replace(' ', '0');
        return prefix.toLowerCase().replace("-", "").replace("_", "");
    }

    public static Builder builder() {
        return new Builder();
    }


    private String generateDeterministicClientId(int clientIndex) {
        return String.format("%s_%s_%0" + CLIENT_INDEX_PADDING + "d", taskId, nodeIdPrefix, clientIndex);
    }


    private String generateDeterministicClientId(String typePrefix, int clientIndex) {
        return String.format("%s_%s_%s_%0" + CLIENT_INDEX_PADDING + "d", taskId, nodeIdPrefix, typePrefix, clientIndex);
    }


    public int getLocalAddressCount() {
        return localAddressProvider.getAddresses().size();
    }


    public MqttClientConfig create(int clientIndex, AtomicInteger subscribeCount) {
        return create(clientIndex, clientIndex, subscribeCount);
    }

    public MqttClientConfig create(int clientIndex, int localPortIndex, AtomicInteger subscribeCount) {
        if (brokers == null || brokers.isEmpty()) {
            throw new IllegalStateException(Messages.get("error.worker.brokerListEmpty"));
        }
        TaskBrokerAddress broker = brokers.get(ThreadLocalRandom.current().nextInt(brokers.size()));

        String clientId = generateDeterministicClientId(clientIndex);

        AuthStrategy.AuthResult authResult = authStrategy.apply(baseBuilder(), clientId, subscribeCount);

        return authResult.builder
            .host(broker.getHost())
            .port(broker.getPort())
            .clientId(clientId)
            .localPort(resolveLocalPort(localPortIndex))
            .willConfig(resolveWillConfig(authResult.thingId, clientId))
            .build();
    }


    public MqttClientConfig create(String typePrefix, int clientIndex, AtomicInteger subscribeCount) {
        return create(typePrefix, clientIndex, clientIndex, subscribeCount);
    }

    public MqttClientConfig create(String typePrefix, int clientIndex, int localPortIndex,
                                   AtomicInteger subscribeCount) {
        if (brokers == null || brokers.isEmpty()) {
            throw new IllegalStateException(Messages.get("error.worker.brokerListEmpty"));
        }
        TaskBrokerAddress broker = brokers.get(ThreadLocalRandom.current().nextInt(brokers.size()));
        String clientId = generateDeterministicClientId(typePrefix, clientIndex);

        AuthStrategy.AuthResult authResult = authStrategy.apply(baseBuilder(), clientId, subscribeCount);

        return authResult.builder
            .host(broker.getHost())
            .port(broker.getPort())
            .clientId(clientId)
            .localPort(resolveLocalPort(localPortIndex))
            .willConfig(resolveWillConfig(authResult.thingId, clientId))
            .build();
    }


    public ClientTaskConfig.ClientTaskConfigBuilder clientTaskConfigBuilder(
        int messageSize, double publishRate, int stressDurationInSec,
        boolean isMqtt5, boolean retain,
        io.netty.handler.codec.mqtt.MqttQoS qos) {
        return ClientTaskConfig.builder()
            .taskId(taskId)
            .nodeId(nodeId)
            .messageQos(qos)
            .messageSize(messageSize)
            .publishRate(publishRate)
            .stressDurationInSec(stressDurationInSec)
            .isMqtt5(isMqtt5)
            .authType(authType)
            .isEmptyClientId(isEmptyClientId)
            .retain(retain);
    }

    private MqttClientConfig.MqttClientConfigBuilder baseBuilder() {
        return MqttClientConfig.builder()
            .keepAliveInSec(keepAliveInSec)
            .ackTimeoutInSec(ackTimeoutInSec)
            .reconnectMaxAttempts(reconnectMaxAttempts)
            .reconnectIntervalInMs(reconnectIntervalInMs)
            .connectTimeoutInMs(connectTimeoutInMs)
            .maxInflightQueue(maxInflightQueue)
            .username(username)
            .password(password)
            .tenantId(tenantId)
            .thingIdStartAt(thingIdStartAt)
            .isEmptyClientId(isEmptyClientId)
            .authType(authType)
            .thingIdPrefix(thingIdPrefix)
            .cleanSession(cleanSession)
            .expiryIntervalInSec(expiryIntervalInSec)
            .localAddress(localAddressProvider.next())
            .localPortRangeConfig(localPortRangeConfig)
            .protocol(protocol)
            .willConfig(willConfig);
    }

    private int resolveLocalPort(int clientIndex) {
        return LocalPortAllocator.allocate(clientIndex, getLocalAddressCount(), localPortRangeConfig);
    }


    private WillConfig resolveWillConfig(String thingId, String clientId) {
        if (!willConfig.getWillFlag()) {
            return new WillConfig();
        }
        WillConfig resolved = new WillConfig();
        resolved.setWillFlag(true);
        resolved.setWillMessage(willConfig.getWillMessage());
        resolved.setWillQos(willConfig.getWillQos());
        resolved.setWillRetain(willConfig.getWillRetain());
        resolved.setWillMessageLen(willConfig.getWillMessageLen());

        String willTopic = willConfig.getWillTopic();
        if (StringUtils.isNotBlank(willTopic)) {
            willTopic = willTopic
                .replace("{thingId}", thingId)
                .replace("{clientId}", clientId.substring(Math.max(0, clientId.length() - 8)))
                .replace("{tenantId}", tenantId == null ? "" : tenantId);
            log.trace("Generated will topic: {}", willTopic);
        }
        resolved.setWillTopic(willTopic);
        return resolved;
    }


    public static class TaskBrokerAddress {
        private final String host;
        private final int port;

        public TaskBrokerAddress(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }
    }

    public static class Builder {
        private String taskId;
        private String nodeId;
        private List<TaskBrokerAddress> brokers;
        private String username;
        private String password;
        private String tenantId;
        private String thingIdPrefix;
        private int thingIdStartAt;
        private boolean cleanSession;
        private int keepAliveInSec = 120;
        private int ackTimeoutInSec = 120;
        private int reconnectMaxAttempts = 2;
        private int reconnectIntervalInMs = 5000;
        private int connectTimeoutInMs = 30000;
        private int maxInflightQueue = 200;
        private long expiryIntervalInSec = 120;
        private String protocol;
        private boolean isEmptyClientId;
        private AuthType authType = AuthType.NONE;
        private WillConfig willConfig = new WillConfig();
        private LocalPortRangeConfig localPortRangeConfig = new LocalPortRangeConfig();
        private LocalAddressProvider localAddressProvider = LocalAddressProvider.disabled();
        private AuthStrategy authStrategy = new NormalAuthStrategy();

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder brokers(List<TaskBrokerAddress> brokers) {
            this.brokers = brokers;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder thingIdPrefix(String thingIdPrefix) {
            this.thingIdPrefix = thingIdPrefix;
            return this;
        }

        public Builder thingIdStartAt(int thingIdStartAt) {
            this.thingIdStartAt = thingIdStartAt;
            return this;
        }

        public Builder cleanSession(boolean cleanSession) {
            this.cleanSession = cleanSession;
            return this;
        }

        public Builder keepAliveInSec(int keepAliveInSec) {
            this.keepAliveInSec = keepAliveInSec;
            return this;
        }

        public Builder ackTimeoutInSec(int ackTimeoutInSec) {
            this.ackTimeoutInSec = ackTimeoutInSec;
            return this;
        }

        public Builder reconnectMaxAttempts(int reconnectMaxAttempts) {
            this.reconnectMaxAttempts = reconnectMaxAttempts;
            return this;
        }

        public Builder reconnectIntervalInMs(int reconnectIntervalInMs) {
            this.reconnectIntervalInMs = reconnectIntervalInMs;
            return this;
        }

        public Builder connectTimeoutInMs(int connectTimeoutInMs) {
            this.connectTimeoutInMs = connectTimeoutInMs;
            return this;
        }

        public Builder maxInflightQueue(int maxInflightQueue) {
            this.maxInflightQueue = maxInflightQueue;
            return this;
        }

        public Builder expiryIntervalInSec(long expiryIntervalInSec) {
            this.expiryIntervalInSec = expiryIntervalInSec;
            return this;
        }

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder isEmptyClientId(boolean isEmptyClientId) {
            this.isEmptyClientId = isEmptyClientId;
            return this;
        }

        public Builder authType(AuthType authType) {
            this.authType = authType;
            return this;
        }

        public Builder willConfig(WillConfig willConfig) {
            this.willConfig = willConfig;
            return this;
        }

        public Builder localPortRangeConfig(LocalPortRangeConfig localPortRangeConfig) {
            this.localPortRangeConfig = localPortRangeConfig;
            return this;
        }

        public Builder localAddressProvider(LocalAddressProvider localAddressProvider) {
            this.localAddressProvider = localAddressProvider;
            return this;
        }

        public Builder authStrategy(AuthStrategy authStrategy) {
            this.authStrategy = authStrategy;
            return this;
        }

        public MqttClientConfigFactory build() {
            return new MqttClientConfigFactory(this);
        }
    }
}
