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

import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.utils.ConnectionUtil;
import io.netty.channel.EventLoop;
import io.vertx.core.Vertx;

import java.util.concurrent.atomic.AtomicReference;

public final class MqttClientFactory {

    private MqttClientFactory() {
    }

    public static MQTTClientWrapper createClient(
        Vertx vertx,
        ClientTaskConfig taskConfig,
        MqttClientConfig mqttClientConfig,
        EventLoop eventLoop,
        AtomicReference<TaskStage> taskStage) {

        MqttClientImpl impl = resolveImpl(taskConfig, mqttClientConfig);
        if (impl == MqttClientImpl.HIVEMQ) {
            return new HiveMQTTClientWrapper(vertx, taskConfig, mqttClientConfig, taskStage);
        } else {
            return new VertxMQTTClientWrapper(vertx, taskConfig, mqttClientConfig, eventLoop, taskStage);
        }
    }

    private static MqttClientImpl resolveImpl(ClientTaskConfig taskConfig, MqttClientConfig mqttClientConfig) {
        if (taskConfig.getClientImpl() != null) {
            return taskConfig.getClientImpl();
        }
        
        String protocol = mqttClientConfig.getProtocol();
        if (ConnectionUtil.isSSL(protocol)) {
            return MqttClientImpl.HIVEMQ;
        }
        
        return taskConfig.isMqtt5() ? MqttClientImpl.HIVEMQ : MqttClientImpl.VERTX;
    }
}
