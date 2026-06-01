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

package org.apache.bifromq.testsuite.worker.type;

import java.util.List;
import org.apache.bifromq.testsuite.client.LocalAddressProvider;
import org.apache.bifromq.testsuite.client.MqttClientConfigFactory;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskSpec;

public final class TaskMqttClientConfigFactoryMapper {

    private TaskMqttClientConfigFactoryMapper() {
    }

    public static MqttClientConfigFactory fromTaskConfig(TaskConfig config) {
        LocalAddressProvider addressProvider;
        if (config.isEnableAutoMultiAddress()) {
            addressProvider = LocalAddressProvider.of(config.getLocalAddresses());
        } else if (config.getLocalPortRangeConfig() != null && config.getLocalPortRangeConfig().isEnabled()) {
            addressProvider = LocalAddressProvider.primary();
        } else {
            addressProvider = LocalAddressProvider.disabled();
        }

        List<MqttClientConfigFactory.TaskBrokerAddress> brokerAddresses =
            config.getBrokers().stream()
                .map(b -> new MqttClientConfigFactory.TaskBrokerAddress(b.getHost(), b.getPort()))
                .toList();

        return MqttClientConfigFactory.builder()
            .taskId(config.getTaskId())
            .nodeId(config.getNodeId())
            .brokers(brokerAddresses)
            .username(config.getUsername())
            .password(config.getPassword())
            .tenantId(config.getTenantId())
            .thingIdPrefix(config.getThingIdPrefix())
            .thingIdStartAt(config.getThingIdStartAt())
            .cleanSession(config.isCleanSession())
            .keepAliveInSec(config.getKeepAliveInSec())
            .ackTimeoutInSec(config.getAckTimeoutInSec())
            .reconnectMaxAttempts(config.getReconnectMaxAttempts())
            .reconnectIntervalInMs(config.getReconnectIntervalInMs())
            .connectTimeoutInMs(config.getConnectTimeoutInMs())
            .maxInflightQueue(config.getMaxInflightQueue())
            .expiryIntervalInSec(config.getExpiryIntervalInSec())
            .protocol(config.getProtocol())
            .isEmptyClientId(config.isEmptyClientId())
            .authType(config.getAuthType())
            .willConfig(config.getWillConfig())
            .localPortRangeConfig(config.getLocalPortRangeConfig())
            .localAddressProvider(addressProvider)
            .authStrategy(AuthStrategyMapper.fromTaskConfig(config))
            .build();
    }

    public static MqttClientConfigFactory fromWorkerTaskSpec(WorkerTaskSpec spec) {
        LocalAddressProvider addressProvider;
        if (spec.isEnableAutoMultiAddress()) {
            addressProvider = LocalAddressProvider.of(spec.getLocalAddresses());
        } else if (spec.getLocalPortRangeConfig() != null && spec.getLocalPortRangeConfig().isEnabled()) {
            addressProvider = LocalAddressProvider.primary();
        } else {
            addressProvider = LocalAddressProvider.disabled();
        }

        List<MqttClientConfigFactory.TaskBrokerAddress> brokerAddresses =
            spec.getBrokers().stream()
                .map(b -> new MqttClientConfigFactory.TaskBrokerAddress(b.getHost(), b.getPort()))
                .toList();

        return MqttClientConfigFactory.builder()
            .taskId(spec.getTaskId())
            .nodeId(spec.getNodeId())
            .brokers(brokerAddresses)
            .username(spec.getUsername())
            .password(spec.getPassword())
            .tenantId(spec.getTenantId())
            .thingIdPrefix(spec.getThingIdPrefix())
            .thingIdStartAt(spec.getThingIdStartAt())
            .cleanSession(spec.isCleanSession())
            .keepAliveInSec(spec.getKeepAliveInSec())
            .ackTimeoutInSec(spec.getAckTimeoutInSec())
            .reconnectMaxAttempts(spec.getReconnectMaxAttempts())
            .reconnectIntervalInMs(spec.getReconnectIntervalInMs())
            .connectTimeoutInMs(spec.getConnectTimeoutInMs())
            .maxInflightQueue(spec.getMaxInflightQueue())
            .expiryIntervalInSec(spec.getExpiryIntervalInSec())
            .protocol(spec.getProtocol())
            .isEmptyClientId(spec.isEmptyClientId())
            .authType(spec.getAuthType())
            .willConfig(spec.getWillConfig())
            .localPortRangeConfig(spec.getLocalPortRangeConfig())
            .localAddressProvider(addressProvider)
            .authStrategy(AuthStrategyMapper.fromWorkerTaskSpec(spec))
            .build();
    }
}
