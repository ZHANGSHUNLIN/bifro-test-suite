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

import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.statemachine.StateMachine;
import io.vertx.core.Vertx;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PipelineContextTest {

    @Nested
    class ConstructorTests {

        @Test
        void testConstructor_withValidParameters_shouldCreateContext() {
            
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);

            
            PipelineContext<TaskStage, TaskEvent> context = new PipelineContext<>(vertx, stateMachine);

            
            assertThat(context.getVertx()).isSameAs(vertx);
            assertThat(context.getStateMachine()).isSameAs(stateMachine);
            assertThat(context.getStageData()).isNotNull().isEmpty();
            assertThat(context.getCancelled()).isInstanceOf(AtomicBoolean.class);
            assertThat(context.getCompletionFuture()).isNotNull();
            assertThat(context.isCancelled()).isFalse();

            vertx.close();
        }
    }

    @Nested
    class CancelTests {

        @Test
        void testCancel_shouldSetCancelledFlag() {
            
            Vertx vertx = Vertx.vertx();
            PipelineContext<TaskStage, TaskEvent> context =
                new PipelineContext<>(vertx, new StateMachine<>(TaskStage.INIT));

            
            context.cancel();

            
            assertThat(context.isCancelled()).isTrue();

            vertx.close();
        }

        @Test
        void testCancel_multipleCalls_shouldKeepCancelledTrue() {
            
            Vertx vertx = Vertx.vertx();
            PipelineContext<TaskStage, TaskEvent> context =
                new PipelineContext<>(vertx, new StateMachine<>(TaskStage.INIT));

            
            context.cancel();
            context.cancel();
            context.cancel();

            
            assertThat(context.isCancelled()).isTrue();

            vertx.close();
        }
    }

    @Nested
    class StageDataTests {

        @Test
        void testStageData_shouldBeModifiable() {
            
            Vertx vertx = Vertx.vertx();
            PipelineContext<TaskStage, TaskEvent> context =
                new PipelineContext<>(vertx, new StateMachine<>(TaskStage.INIT));

            
            context.getStageData().put("key1", "value1");
            context.getStageData().put("key2", 42);

            
            assertThat(context.getStageData()).hasSize(2);
            assertThat(context.getStageData().get("key1")).isEqualTo("value1");
            assertThat(context.getStageData().get("key2")).isEqualTo(42);

            vertx.close();
        }

        @Test
        void testStageData_shouldStartEmpty() {
            
            Vertx vertx = Vertx.vertx();
            PipelineContext<TaskStage, TaskEvent> context =
                new PipelineContext<>(vertx, new StateMachine<>(TaskStage.INIT));

            
            assertThat(context.getStageData()).isEmpty();

            vertx.close();
        }
    }

    @Nested
    class CompletionFutureTests {

        @Test
        void testCompletionFuture_shouldNotBeCompletedInitially() {
            
            Vertx vertx = Vertx.vertx();
            PipelineContext<TaskStage, TaskEvent> context =
                new PipelineContext<>(vertx, new StateMachine<>(TaskStage.INIT));

            
            CompletableFuture<Void> future = context.getCompletionFuture();

            
            assertThat(future).isNotNull();
            assertThat(future.isDone()).isFalse();
            assertThat(future.isCompletedExceptionally()).isFalse();

            vertx.close();
        }

        @Test
        void testCompletionFuture_shouldBeCompletable() {
            
            Vertx vertx = Vertx.vertx();
            PipelineContext<TaskStage, TaskEvent> context =
                new PipelineContext<>(vertx, new StateMachine<>(TaskStage.INIT));

            
            context.getCompletionFuture().complete(null);

            
            assertThat(context.getCompletionFuture().isDone()).isTrue();
            assertThat(context.getCompletionFuture().isCompletedExceptionally()).isFalse();

            vertx.close();
        }
    }

    @Nested
    class GetterTests {

        @Test
        void testGetVertx_shouldReturnProvidedVertxInstance() {
            
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            PipelineContext<TaskStage, TaskEvent> context = new PipelineContext<>(vertx, stateMachine);

            
            assertThat(context.getVertx()).isSameAs(vertx);

            vertx.close();
        }

        @Test
        void testGetStateMachine_shouldReturnProvidedStateMachine() {
            
            Vertx vertx = Vertx.vertx();
            StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
            PipelineContext<TaskStage, TaskEvent> context = new PipelineContext<>(vertx, stateMachine);

            
            assertThat(context.getStateMachine()).isSameAs(stateMachine);

            vertx.close();
        }

        @Test
        void testGetCancelled_shouldReturnAtomicBoolean() {
            
            Vertx vertx = Vertx.vertx();
            PipelineContext<TaskStage, TaskEvent> context =
                new PipelineContext<>(vertx, new StateMachine<>(TaskStage.INIT));

            
            assertThat(context.getCancelled()).isNotNull();
            assertThat(context.getCancelled().get()).isFalse();

            vertx.close();
        }
    }

    @Nested
    class DelayTests {

        @Test
        void testGetDelayAfterStageInSec_defaultIsZero() {
            
            Vertx vertx = Vertx.vertx();
            PipelineContext<TaskStage, TaskEvent> context =
                new PipelineContext<>(vertx, new StateMachine<>(TaskStage.INIT));

            
            assertThat(context.getDelayAfterStageInSec()).isZero();

            vertx.close();
        }
    }
}
