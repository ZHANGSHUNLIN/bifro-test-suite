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

import io.vertx.core.Vertx;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.TaskTemplate;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.worker.type.ExecutionPlan;
import org.apache.bifromq.testsuite.worker.type.TaskType;
import org.apache.bifromq.testsuite.worker.type.TaskTypeRegistry;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpec;

@Slf4j
public final class TaskWorkerFactory {

    private static final Map<TaskTemplate, String> TEMPLATE_TO_TYPE_ID = Map.of(
        TaskTemplate.CONN_STANDARD, "mqtt.conn.standard",
        TaskTemplate.CONN_IMMEDIATE_DISCONNECT, "mqtt.conn.immediate_disconnect",
        TaskTemplate.CONN_PUBLISH_ON_CONNECT, "mqtt.conn.publish_on_connect",
        TaskTemplate.PUBSUB_STANDARD, "mqtt.pubsub.standard",
        TaskTemplate.PUBSUB_PUB_ONLY, "mqtt.pubsub.standard",
        TaskTemplate.PUBSUB_SUB_ONLY, "mqtt.pubsub.standard",
        TaskTemplate.CHAOS_STANDARD, "mqtt.chaos.standard"
    );

    private static final TaskTypeRegistry REGISTRY = TaskTypeRegistry.createDefault();

    private TaskWorkerFactory() {
    }

    public static TaskWorker create(Vertx vertx, WorkerPlanSpec spec) {
        TaskTemplate template = spec.executionContext().executionConfig().template();
        String templateName = template == null ? null : template.name();
        log.info("Creating task worker for template: {}, taskId: {}",
            templateName, spec.executionContext().taskId());

        String typeId = TEMPLATE_TO_TYPE_ID.get(template);
        if (typeId == null) {
            if (template == TaskTemplate.CUSTOM) {
                throw new UnsupportedOperationException(Messages.get("error.worker.customNotImpl"));
            }
            throw new IllegalArgumentException("Unknown template: " + template);
        }

        TaskType taskType = REGISTRY.get(typeId);
        ExecutionPlan plan = taskType.buildPlan(spec, vertx);
        return new GenericTaskWorker(vertx, plan);
    }
}
