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

package org.apache.bifromq.testsuite.worker.pipeline.stages;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.Constants;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;

@Slf4j
public class StartConnClientsStage extends BaseConnClientsStage {
    private final String clientTag;
    @Getter
    private final TaskEvent triggerEvent;

    public StartConnClientsStage(String clientTag) {
        this.clientTag = clientTag;
        this.triggerEvent = null;
    }

    public StartConnClientsStage(String clientTag, TaskEvent triggerEvent) {
        this.clientTag = clientTag;
        this.triggerEvent = triggerEvent;
    }

    public String getName() {
        return clientTag == null || clientTag.isEmpty()
            ? "StartConnClients"
            : "StartConnClients-" + clientTag;
    }

    @Override
    public String getLabel() {
        if (clientTag == null || clientTag.isEmpty()) {
            return Messages.get("pipeline.stage.buildConn");
        }
        return switch (clientTag) {
            case "CONN_CLIENTS" -> Messages.get("pipeline.stage.buildConn");
            case "PUB_CLIENTS" -> Messages.get("pipeline.stage.buildPubConn");
            case "SUB_CLIENTS" -> Messages.get("pipeline.stage.buildSubConn");
            default -> "connecting-" + clientTag;
        };
    }

    Map<String, MqttClientTask> taskClientMap(TaskPipelineContext context) {
        if (Constants.CONN_CLIENT_TAG.equals(clientTag)) {
            Map<String, MqttClientTask> connClients = context.getExecutionContext().connClients();
            if (!connClients.isEmpty()) {
                return connClients;
            }

            Map<String, MqttClientTask> pubClients = context.getExecutionContext().pubClients();
            Map<String, MqttClientTask> subClients = context.getExecutionContext().subClients();
            if (pubClients.isEmpty() && subClients.isEmpty()) {
                return connClients;
            }

            Map<String, MqttClientTask> mergedClients = new HashMap<>(pubClients.size() + subClients.size());
            mergedClients.putAll(pubClients);
            mergedClients.putAll(subClients);
            log.info("Using merged pub/sub client set for connect stage: pub={}, sub={}, total={}",
                pubClients.size(), subClients.size(), mergedClients.size());
            return mergedClients;
        } else if (Constants.PUB_CLIENT_TAG.equals(clientTag)) {
            return context.getExecutionContext().pubClients();
        } else if (Constants.SUB_CLIENT_TAG.equals(clientTag)) {
            return context.getExecutionContext().subClients();
        }
        throw new IllegalArgumentException("Unknown clientTag: " + clientTag);
    }

}
