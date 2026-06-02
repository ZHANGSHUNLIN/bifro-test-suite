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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.bifromq.testsuite.WillConfig;
import org.junit.jupiter.api.Test;

class MqttClientConfigFactoryTemplateTest {

    @Test
    void create_withWillTopicTemplate_rendersClientContextPlaceholders() {
        MqttClientConfigFactory factory = factoryWithWillTopic(
            "will/{{task_id}}/{{node_id}}/{{index}}/{{client_id_short}}");

        var config = factory.create("pub", 3, new AtomicInteger());

        assertThat(config.getClientId()).isEqualTo("taskA_node_pub_0000003");
        assertThat(config.getWillConfig().getWillTopic()).isEqualTo("will/taskA/node-1/3/0000003");
    }

    @Test
    void create_withLegacyWillTopicPlaceholders_keepsCompatibility() {
        MqttClientConfigFactory factory = factoryWithWillTopic("will/{clientId}/{thingId}");

        var config = factory.create(2, new AtomicInteger());

        assertThat(config.getClientId()).isEqualTo("taskA_node_0000002");
        assertThat(config.getWillConfig().getWillTopic()).isEqualTo("will/_0000002/_0000002");
    }

    @Test
    void create_withAuthTemplates_rendersClientContextPlaceholders() {
        MqttClientConfigFactory factory = baseFactoryBuilder()
            .username("u-{{client_id_short}}-{{index}}")
            .password("p-{{task_id}}-{{node_id}}")
            .authStrategy(new NormalAuthStrategy())
            .build();

        var config = factory.create("sub", 7, new AtomicInteger());

        assertThat(config.getClientId()).isEqualTo("taskA_node_sub_0000007");
        assertThat(config.getUsername()).isEqualTo("u-0000007-7");
        assertThat(config.getPassword()).isEqualTo("p-taskA-node-1");
    }

    private MqttClientConfigFactory factoryWithWillTopic(String willTopic) {
        WillConfig willConfig = new WillConfig();
        willConfig.setWillFlag(true);
        willConfig.setWillTopic(willTopic);
        return baseFactoryBuilder()
            .willConfig(willConfig)
            .authStrategy(new NormalAuthStrategy())
            .build();
    }

    private MqttClientConfigFactory.Builder baseFactoryBuilder() {
        return MqttClientConfigFactory.builder()
            .taskId("taskA")
            .nodeId("node-1")
            .brokers(List.of(new MqttClientConfigFactory.TaskBrokerAddress("localhost", 1883)));
    }
}
