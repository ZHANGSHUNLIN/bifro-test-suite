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

package org.apache.bifromq.testsuite.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.bifromq.testsuite.statemachine.StateMachine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PipelineContextDiagnosticsTest {

    private final Vertx vertx = Vertx.vertx();

    @AfterEach
    void tearDown() {
        vertx.close();
    }

    @Test
    void refreshStageDiagnostics_shouldAttachScopeCountersAndLimitedPendingSamples() {
        PipelineContext<String, String> context = new PipelineContext<>(vertx, new StateMachine<>("INIT"));
        context.getPipelineProgress().add(PipelineStageSnapshot.builder()
            .key("connect")
            .label("Connect")
            .status("RUNNING")
            .startedAt(System.currentTimeMillis() - 1000)
            .build());
        StageExecutionScope scope = context.stageScopeOrCreate("connect");
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            futures.add(future);
            scope.track("client-" + i, future);
        }
        futures.get(0).complete(null);
        futures.get(1).completeExceptionally(new RuntimeException("boom"));
        scope.recordFailureReason("connect_timeout");

        PipelineStageSnapshot snapshot = context.refreshStageDiagnostics("connect");

        assertThat(snapshot.getStarted()).isEqualTo(12);
        assertThat(snapshot.getCompleted()).isEqualTo(2);
        assertThat(snapshot.getFailed()).isEqualTo(1);
        assertThat(snapshot.getPending()).isEqualTo(10);
        assertThat(snapshot.getPendingSamples()).hasSize(10);
        assertThat(snapshot.getFailureReasons()).containsEntry("connect_timeout", 1);
        assertThat(snapshot.getDurationMs()).isGreaterThanOrEqualTo(0);
    }
}
