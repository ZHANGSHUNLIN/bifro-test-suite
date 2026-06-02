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

package org.apache.bifromq.testsuite.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.client.MQTTClientWrapper;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.constants.ConnectionStatus;
import org.apache.bifromq.testsuite.worker.pojo.ClientInfo;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryRequest;
import org.junit.jupiter.api.Test;

class ClientQueryServiceTest {

    @Test
    void testQuery_withLocalEndpoint_shouldReturnLocalAddressAndPort() {
        ClientQueryService service = new ClientQueryService();
        MqttClientTask clientTask = mockClientTask("client_0_0", "10.0.0.8", 10000);
        ClientQueryRequest request = ClientQueryRequest.builder()
            .taskId("task-1")
            .clientType("conn")
            .page(0)
            .size(10)
            .build();

        var response = service.query(request, Map.of("client_0_0", clientTask));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getClients()).hasSize(1);
        ClientInfo clientInfo = response.getClients().get(0);
        assertThat(clientInfo.getLocalAddress()).isEqualTo("10.0.0.8");
        assertThat(clientInfo.getLocalPort()).isEqualTo(10000);
    }

    @Test
    void testQuery_withoutLocalPort_shouldReturnNullLocalPort() {
        ClientQueryService service = new ClientQueryService();
        MqttClientTask clientTask = mockClientTask("client_0_0", "10.0.0.8", 0);
        ClientQueryRequest request = ClientQueryRequest.builder()
            .taskId("task-1")
            .clientType("conn")
            .page(0)
            .size(10)
            .build();

        var response = service.query(request, Map.of("client_0_0", clientTask));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getClients()).hasSize(1);
        ClientInfo clientInfo = response.getClients().get(0);
        assertThat(clientInfo.getLocalAddress()).isEqualTo("10.0.0.8");
        assertThat(clientInfo.getLocalPort()).isNull();
    }

    private MqttClientTask mockClientTask(String clientId, String localAddress, int localPort) {
        MqttClientConfig config = MqttClientConfig.builder()
            .clientId(clientId)
            .host("broker.example.com")
            .port(1883)
            .localAddress(localAddress)
            .localPort(localPort)
            .build();
        MQTTClientWrapper wrapper = mock(MQTTClientWrapper.class);
        when(wrapper.getConnectedAt()).thenReturn(123L);

        MqttClientTask clientTask = mock(MqttClientTask.class);
        when(clientTask.getClientConfig()).thenReturn(config);
        when(clientTask.getMqttClientWrapper()).thenReturn(wrapper);
        when(clientTask.getConnectionStatus()).thenReturn(ConnectionStatus.CONNECTED);
        return clientTask;
    }
}
