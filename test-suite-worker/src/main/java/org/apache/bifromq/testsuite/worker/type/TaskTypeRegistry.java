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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.bifromq.testsuite.worker.type.impl.ChaosStandardTaskType;
import org.apache.bifromq.testsuite.worker.type.impl.ConnStandardTaskType;
import org.apache.bifromq.testsuite.worker.type.impl.PubSubStandardTaskType;

public class TaskTypeRegistry {

    private final Map<String, TaskType> registry = new LinkedHashMap<>();

    public static TaskTypeRegistry createDefault() {
        TaskTypeRegistry reg = new TaskTypeRegistry();
        reg.register(new ConnStandardTaskType(),
            "mqtt.conn.standard",
            "mqtt.conn.immediate_disconnect",
            "mqtt.conn.publish_on_connect");
        reg.register(new PubSubStandardTaskType(),
            "mqtt.pubsub.standard");
        reg.register(new ChaosStandardTaskType(),
            "mqtt.chaos.standard");
        return reg;
    }

    public void register(TaskType type, String... typeIds) {
        for (String id : typeIds) {
            if (registry.containsKey(id)) {
                throw new IllegalArgumentException("Task type already registered: " + id);
            }
            registry.put(id, type);
        }
    }

    public TaskType get(String typeId) {
        TaskType type = registry.get(typeId);
        if (type == null) {
            throw new IllegalArgumentException("Unknown task type: " + typeId
                + ". Registered: " + registry.keySet());
        }
        return type;
    }

    public Set<String> allTypeIds() {
        return Set.copyOf(registry.keySet());
    }
}
