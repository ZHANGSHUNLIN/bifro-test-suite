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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AsyncDiagnosticContextTest {
    @AfterEach
    void tearDown() {
        AsyncDiagnosticContext.clear();
    }

    @Test
    void with_shouldRestorePreviousContext() {
        try (AsyncDiagnosticContext.Scope ignored = AsyncDiagnosticContext.with("task-1", "node-1", "stage-1", "")) {
            assertThat(AsyncDiagnosticContext.current().taskId()).isEqualTo("task-1");
            try (AsyncDiagnosticContext.Scope nested =
                     AsyncDiagnosticContext.with("task-2", "node-2", "stage-2", "client-1")) {
                assertThat(AsyncDiagnosticContext.current().taskId()).isEqualTo("task-2");
                assertThat(AsyncDiagnosticContext.current().clientId()).isEqualTo("client-1");
            }
            assertThat(AsyncDiagnosticContext.current().taskId()).isEqualTo("task-1");
        }
        assertThat(AsyncDiagnosticContext.current()).isNull();
    }

    @Test
    void wrap_shouldInstallCapturedContextForRunnable() {
        AsyncDiagnosticContext.Snapshot snapshot =
            new AsyncDiagnosticContext.Snapshot("task-1", "node-1", "stage-1", "client-1");
        AtomicReference<AsyncDiagnosticContext.Snapshot> observed = new AtomicReference<>();

        AsyncDiagnosticContext.wrap(snapshot, () -> observed.set(AsyncDiagnosticContext.current())).run();

        assertThat(observed.get()).isEqualTo(snapshot);
        assertThat(AsyncDiagnosticContext.current()).isNull();
    }
}
