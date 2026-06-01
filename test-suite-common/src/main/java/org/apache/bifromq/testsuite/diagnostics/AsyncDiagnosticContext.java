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

package org.apache.bifromq.testsuite.diagnostics;

import java.util.Objects;

public final class AsyncDiagnosticContext {
    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private AsyncDiagnosticContext() {
    }

    public static Snapshot current() {
        return CURRENT.get();
    }

    public static Scope with(String taskId, String nodeId, String stage, String clientId) {
        return install(new Snapshot(taskId, nodeId, stage, clientId));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Runnable wrap(Snapshot snapshot, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        return () -> {
            try (Scope ignored = install(snapshot)) {
                runnable.run();
            }
        };
    }

    private static Scope install(Snapshot next) {
        Snapshot previous = CURRENT.get();
        if (next == null || next.isEmpty()) {
            CURRENT.remove();
        } else {
            CURRENT.set(next);
        }
        return new Scope(previous);
    }

    public record Snapshot(String taskId, String nodeId, String stage, String clientId) {
        public boolean isEmpty() {
            return isBlank(taskId) && isBlank(nodeId) && isBlank(stage) && isBlank(clientId);
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Snapshot previous;

        private Scope(Snapshot previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null || previous.isEmpty()) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
