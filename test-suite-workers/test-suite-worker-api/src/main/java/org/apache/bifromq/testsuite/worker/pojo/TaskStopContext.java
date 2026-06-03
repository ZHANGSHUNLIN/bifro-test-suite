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

package org.apache.bifromq.testsuite.worker.pojo;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStopContext {

    private TaskStopReason reason;
    private String message;
    private String initiator;
    private Instant requestedAt;
    private Map<String, Object> metadata;

    public static TaskStopContext userStop() {
        return TaskStopContext.builder()
            .reason(TaskStopReason.USER_STOP)
            .message("Task stopped by user request")
            .initiator("user")
            .requestedAt(Instant.now())
            .metadata(Map.of())
            .build();
    }

    public static TaskStopContext serviceShutdown(String nodeId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("nodeShutdown", true);
        if (nodeId != null && !nodeId.isBlank()) {
            metadata.put("nodeId", nodeId);
        }
        return TaskStopContext.builder()
            .reason(TaskStopReason.SERVICE_SHUTDOWN)
            .message("Task cancelled because node is shutting down")
            .initiator(nodeId == null || nodeId.isBlank() ? "node" : "node:" + nodeId)
            .requestedAt(Instant.now())
            .metadata(metadata)
            .build();
    }

    public TaskStopContext normalized() {
        return TaskStopContext.builder()
            .reason(reason == null ? TaskStopReason.USER_STOP : reason)
            .message(message)
            .initiator(initiator)
            .requestedAt(requestedAt == null ? Instant.now() : requestedAt)
            .metadata(metadata == null ? Map.of() : Map.copyOf(metadata))
            .build();
    }
}
