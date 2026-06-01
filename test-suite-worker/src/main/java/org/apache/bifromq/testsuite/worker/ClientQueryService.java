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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.PubMqttClientTask;
import org.apache.bifromq.testsuite.SubMqttClientTask;
import org.apache.bifromq.testsuite.client.MQTTClientWrapper;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.worker.pojo.ClientInfo;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryRequest;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryResponse;

@Slf4j
public class ClientQueryService {

    public ClientQueryResponse query(ClientQueryRequest request, Map<String, MqttClientTask> clientMap) {
        if (clientMap == null || clientMap.isEmpty()) {
            return ClientQueryResponse.builder()
                .success(true)
                .clients(List.of())
                .total(0)
                .page(request.getPage())
                .size(request.getSize())
                .totalPages(0)
                .build();
        }
        try {
            List<Map.Entry<String, MqttClientTask>> sortedEntries = clientMap.entrySet().stream()
                .sorted(Comparator.comparing(e -> extractClientIndex(e.getKey())))
                .toList();
            int total = sortedEntries.size();
            int totalPages = (total + request.getSize() - 1) / request.getSize();
            int fromIndex = request.getPage() * request.getSize();
            if (fromIndex >= total) {
                return ClientQueryResponse.builder()
                    .success(true)
                    .clients(List.of())
                    .total(total)
                    .page(request.getPage())
                    .size(request.getSize())
                    .totalPages(totalPages)
                    .build();
            }
            int toIndex = Math.min(fromIndex + request.getSize(), total);
            List<ClientInfo> clientInfos = sortedEntries.subList(fromIndex, toIndex).stream()
                .map(entry -> toClientInfo(entry.getValue(), request.getClientType()))
                .collect(Collectors.toList());
            return ClientQueryResponse.builder()
                .success(true)
                .clients(clientInfos)
                .total(total)
                .page(request.getPage())
                .size(request.getSize())
                .totalPages(totalPages)
                .build();
        } catch (Exception e) {
            log.error("Failed to query clients for taskId={}, clientType={}",
                request.getTaskId(), request.getClientType(), e);
            return ClientQueryResponse.builder()
                .success(false)
                .errorMessage("Query failed: " + e.getMessage())
                .clients(List.of())
                .total(0)
                .page(request.getPage())
                .size(request.getSize())
                .totalPages(0)
                .build();
        }
    }

    private int extractClientIndex(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return 0;
        }
        String[] parts = clientId.split("_");
        if (parts.length < 3) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private ClientInfo toClientInfo(MqttClientTask clientTask, String clientType) {
        MqttClientConfig config = clientTask.getClientConfig();
        MQTTClientWrapper wrapper = clientTask.getMqttClientWrapper();
        return ClientInfo.builder()
            .clientId(config.getClientId())
            .host(config.getHost())
            .port(config.getPort())
            .localAddress(config.getLocalAddress())
            .localPort(config.getLocalPort() > 0 ? config.getLocalPort() : null)
            .status(clientTask.getConnectionStatus().name())
            .connectedAt(wrapper != null ? wrapper.getConnectedAt() : null)
            .clientType(clientType)
            .pubCount(clientTask instanceof PubMqttClientTask
                ? clientTask.getMessageCount().orElse(0) : null)
            .subCount(clientTask instanceof SubMqttClientTask
                ? clientTask.getMessageCount().orElse(0) : null)
            .build();
    }
}
