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

import static org.apache.bifromq.testsuite.constants.ClientTaskType.PUB;

import io.vertx.core.Vertx;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.PubMqttClientTask;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;

@Slf4j
public class InitPubClientsStage extends BaseInitClientsStage {

    @Override
    public String getName() {
        return "InitPubClients";
    }

    @Override
    public String getLabel() {
        return Messages.get("pipeline.stage.initPub");
    }

    @Override
    public TaskEvent getTriggerEvent() {
        return null;
    }

    @Override
    int clientSize(TaskPipelineContext context) {
        return context.getExecutionContext().expectedPubCount();
    }

    @Override
    String clientIdTypePrefix() {
        return "p";
    }

    @Override
    String clientType() {
        return "pub";
    }

    @Override
    Map<String, MqttClientTask> taskClientMap(TaskPipelineContext context) {
        return context.getExecutionContext().pubClients();
    }

    @Override
    MqttClientTask buildClientTask(
        int index,
        Vertx vertx, TaskPipelineContext context, ClientTaskConfig clientTaskConfig, MqttClientConfig mqttClientConfig,
        AtomicReference<TaskStage> sAtomicReference) {

        clientTaskConfig.setType(PUB);
        var assignment = context.getExecutionContext().topicDistributionPlanner().publisherAssignment(index);
        clientTaskConfig.setPubTopics(assignment.publishTopics());
        clientTaskConfig.setPubTopic(assignment.primaryTopic());

        return new PubMqttClientTask(vertx, clientTaskConfig, mqttClientConfig, sAtomicReference);
    }

    @Override
    public void onBefore(TaskPipelineContext context) {
        log.info("Starting to initialize pub clients, count: {}",
            context.getExecutionContext().expectedPubCount());
    }
}
