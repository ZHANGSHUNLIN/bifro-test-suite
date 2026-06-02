/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.scheduler;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class ScheduledTaskExecutorRegistry {
    private final Map<ScheduledTaskKind, ScheduledTaskExecutor> executors =
        new EnumMap<>(ScheduledTaskKind.class);

    public ScheduledTaskExecutorRegistry(Collection<ScheduledTaskExecutor> executors) {
        if (executors == null) {
            return;
        }
        executors.forEach(this::register);
    }

    public void register(ScheduledTaskExecutor executor) {
        if (executor == null || executor.kind() == null) {
            throw new IllegalArgumentException("Scheduled task executor and kind are required");
        }
        ScheduledTaskExecutor existing = executors.putIfAbsent(executor.kind(), executor);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate scheduled task executor: " + executor.kind());
        }
    }

    public Optional<ScheduledTaskExecutor> find(ScheduledTaskKind kind) {
        return Optional.ofNullable(executors.get(kind));
    }
}
