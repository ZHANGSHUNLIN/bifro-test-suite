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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.statemachine.StateMachine;
import io.vertx.core.Vertx;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

class TaskPipelineTest {

    private Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    private PipelineContext<TaskStage, TaskEvent> createTestContext() {
        StateMachine<TaskStage, TaskEvent> stateMachine = new StateMachine<>(TaskStage.INIT);
        return new PipelineContext<>(vertx, stateMachine);
    }

    private PipelineStage<PipelineContext<TaskStage, TaskEvent>> createSimpleStage(String name) {
        return new PipelineStage<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public CompletableFuture<StageResult> execute(PipelineContext<TaskStage, TaskEvent> context) {
                return CompletableFuture.completedFuture(StageResult.success());
            }
        };
    }

    private PipelineStage<PipelineContext<TaskStage, TaskEvent>> createFailingStage(String name) {
        return new PipelineStage<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public CompletableFuture<StageResult> execute(PipelineContext<TaskStage, TaskEvent> context) {
                return CompletableFuture.completedFuture(StageResult.failure("Stage failed"));
            }
        };
    }

    private PipelineStage<PipelineContext<TaskStage, TaskEvent>> createExceptionStage(String name) {
        return new PipelineStage<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public CompletableFuture<StageResult> execute(PipelineContext<TaskStage, TaskEvent> context) {
                return CompletableFuture.failedFuture(new RuntimeException("Stage exception"));
            }
        };
    }

    private PipelineStage<PipelineContext<TaskStage, TaskEvent>> createSlowStage(String name) {
        return new PipelineStage<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public CompletableFuture<StageResult> execute(PipelineContext<TaskStage, TaskEvent> context) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return StageResult.success();
                });
            }
        };
    }

    private PipelineStage<PipelineContext<TaskStage, TaskEvent>> createOrderedStage(String name,
                                                                                    AtomicInteger executionOrder,
                                                                                    int expectedOrder) {
        return new PipelineStage<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public CompletableFuture<StageResult> execute(PipelineContext<TaskStage, TaskEvent> context) {
                executionOrder.incrementAndGet();
                return CompletableFuture.completedFuture(StageResult.success());
            }

            @Override
            public void onAfter(PipelineContext<TaskStage, TaskEvent> context, StageResult result) {
                assertThat(executionOrder.get()).isEqualTo(expectedOrder);
            }
        };
    }

    

    private PipelineStage<PipelineContext<TaskStage, TaskEvent>> createCancelableStage(String name,
                                                                                       AtomicInteger cancelCount) {
        return new PipelineStage<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public CompletableFuture<StageResult> execute(PipelineContext<TaskStage, TaskEvent> context) {
                return CompletableFuture.completedFuture(StageResult.success());
            }

            @Override
            public void onCancelled(PipelineContext<TaskStage, TaskEvent> context) {
                cancelCount.incrementAndGet();
            }
        };
    }

    private PipelineStage<PipelineContext<TaskStage, TaskEvent>> createStageWithHooks(String name,
                                                                                      AtomicInteger beforeCount,
                                                                                      AtomicInteger afterCount,
                                                                                      AtomicInteger errorCount) {
        return new PipelineStage<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public CompletableFuture<StageResult> execute(PipelineContext<TaskStage, TaskEvent> context) {
                if (name.equals("Exception")) {
                    return CompletableFuture.failedFuture(new RuntimeException("Test exception"));
                }
                return CompletableFuture.completedFuture(StageResult.success());
            }

            @Override
            public void onBefore(PipelineContext<TaskStage, TaskEvent> context) {
                if (beforeCount != null) {
                    beforeCount.incrementAndGet();
                }
            }

            @Override
            public void onAfter(PipelineContext<TaskStage, TaskEvent> context, StageResult result) {
                if (afterCount != null) {
                    afterCount.incrementAndGet();
                }
            }

            @Override
            public void onError(PipelineContext<TaskStage, TaskEvent> context, Throwable error) {
                if (errorCount != null) {
                    errorCount.incrementAndGet();
                }
            }
        };
    }

    private PipelineStage<PipelineContext<TaskStage, TaskEvent>> createNonExecutableStage(String name,
                                                                                          AtomicInteger executionCount) {
        return new PipelineStage<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public CompletableFuture<StageResult> execute(PipelineContext<TaskStage, TaskEvent> context) {
                executionCount.incrementAndGet();
                return CompletableFuture.completedFuture(StageResult.success());
            }

            @Override
            public boolean canExecute(PipelineContext<TaskStage, TaskEvent> context) {
                return false;
            }
        };
    }

    @Nested
    class BuilderTests {

        @Test
        void testBuilder_withStages_shouldBuildPipeline() {
            
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage1 = createSimpleStage("Stage1");
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage2 = createSimpleStage("Stage2");

            
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(stage1)
                    .addStage(stage2)
                    .build();

            
            assertThat(pipeline.getStageCount()).isEqualTo(2);
        }

        @Test
        void testBuilder_withNoStages_shouldBuildEmptyPipeline() {
            

            
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder().build();

            
            assertThat(pipeline.getStageCount()).isEqualTo(0);
        }

        @Test
        void testBuilder_withNullStage_shouldIgnoreStage() {
            
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage = createSimpleStage("Stage1");

            
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(null)
                    .addStage(stage)
                    .addStage(null)
                    .build();

            
            assertThat(pipeline.getStageCount()).isEqualTo(1);
        }

        @Test
        void testBuilder_withErrorStage_shouldBuildPipelineWithErrorStage() {
            
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> errorStage = createSimpleStage("ErrorStage");

            
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .onError(errorStage)
                    .build();

            
            assertThat(pipeline.getStageCount()).isEqualTo(0);
        }

        @Test
        void testBuilder_withCleanupStage_shouldBuildPipelineWithCleanupStage() {
            
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> cleanupStage = createSimpleStage("CleanupStage");

            
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .onCleanup(cleanupStage)
                    .build();

            
            assertThat(pipeline.getStageCount()).isEqualTo(0);
        }

        @Test
        void testBuilder_withCancelStage_shouldBuildPipelineWithCancelStage() {
            
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> cancelStage = createSimpleStage("CancelStage");

            
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .onCancel(cancelStage)
                    .build();

            
            assertThat(pipeline.getStageCount()).isEqualTo(0);
        }
    }

    @Nested
    class ExecuteTests {

        @Test
        void testExecute_withSingleStage_shouldCompleteSuccessfully() {
            
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage = createSimpleStage("Stage1");
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(stage)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            CompletableFuture<Void> result = pipeline.execute(context);

            
            result.join();
            assertThat(result.isDone()).isTrue();
            assertThat(context.getCompletionFuture().isDone()).isTrue();
        }

        @Test
        void testExecute_withMultipleStages_shouldCompleteSuccessfully() {
            
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage1 = createSimpleStage("Stage1");
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage2 = createSimpleStage("Stage2");
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage3 = createSimpleStage("Stage3");
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(stage1)
                    .addStage(stage2)
                    .addStage(stage3)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            CompletableFuture<Void> result = pipeline.execute(context);

            
            result.join();
            assertThat(result.isDone()).isTrue();
            assertThat(context.getCompletionFuture().isDone()).isTrue();
        }

        @Test
        void testExecute_withEmptyPipeline_shouldCompleteImmediately() {
            
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder().build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            CompletableFuture<Void> result = pipeline.execute(context);

            
            result.join();
            assertThat(result.isDone()).isTrue();
        }

        @Test
        void testExecute_shouldExecuteStagesInOrder() {
            
            AtomicInteger executionOrder = new AtomicInteger(0);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage1 =
                createOrderedStage("Stage1", executionOrder, 1);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage2 =
                createOrderedStage("Stage2", executionOrder, 2);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage3 =
                createOrderedStage("Stage3", executionOrder, 3);
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(stage1)
                    .addStage(stage2)
                    .addStage(stage3)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            pipeline.execute(context).join();

            
            assertThat(executionOrder.get()).isEqualTo(3);
        }

        @Test
        void testExecute_withFailingStage_shouldPropagateException() {
            
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> successStage = createSimpleStage("Success");
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> failStage = createFailingStage("Fail");
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> nextStage = createSimpleStage("Next");
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(successStage)
                    .addStage(failStage)
                    .addStage(nextStage)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            CompletableFuture<Void> result = pipeline.execute(context);

            
            assertThatThrownBy(result::join)
                .isInstanceOf(java.util.concurrent.CompletionException.class)
                .hasMessageContaining("Stage Fail failed");
            assertThat(context.getStageData().containsKey("lastError")).isTrue();
            assertThat(result.isCompletedExceptionally()).isTrue();
        }

        @Test
        void testExecute_withExceptionInStage_shouldPropagateException() {
            
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> successStage = createSimpleStage("Success");
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> exceptionStage = createExceptionStage("Exception");
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> nextStage = createSimpleStage("Next");
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(successStage)
                    .addStage(exceptionStage)
                    .addStage(nextStage)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            CompletableFuture<Void> result = pipeline.execute(context);

            
            assertThatThrownBy(result::join)
                .isInstanceOf(java.util.concurrent.CompletionException.class)
                .hasMessageContaining("Stage exception");
            assertThat(result.isCompletedExceptionally()).isTrue();
        }
    }

    @Nested
    class CancelTests {

        @Test
        void testCancel_duringExecution_shouldStopPipeline() throws Exception {
            
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> slowStage = createSlowStage("Slow");
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(slowStage)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            CompletableFuture<Void> executeFuture = pipeline.execute(context);
            vertx.setTimer(100, v -> pipeline.cancel(context));

            
            Thread.sleep(200); 
            assertThat(context.isCancelled()).isTrue();
            assertThat(context.getCompletionFuture().isCompletedExceptionally()).isTrue();
        }

        @Test
        void testCancel_shouldCallOnCancelledForCurrentAndRemainingStages() {
            
            AtomicInteger cancelCount = new AtomicInteger(0);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> cancelHandler = new PipelineStage<>() {
                @Override
                public String getName() {
                    return "CancelHandler";
                }

                @Override
                public CompletableFuture<StageResult> execute(PipelineContext<TaskStage, TaskEvent> context) {
                    return CompletableFuture.completedFuture(StageResult.success());
                }

                @Override
                public void onCancelled(PipelineContext<TaskStage, TaskEvent> context) {
                    cancelCount.incrementAndGet();
                }
            };
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage1 = createSimpleStage("Stage1");
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> cancelableStage1 =
                createCancelableStage("Cancellable1", cancelCount);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> cancelableStage2 =
                createCancelableStage("Cancellable2", cancelCount);
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(stage1)
                    .addStage(cancelableStage1)
                    .addStage(cancelableStage2)
                    .onCancel(cancelHandler)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            context.cancel();
            pipeline.execute(context).join();

            
            assertThat(cancelCount.get()).isGreaterThan(0);
        }

        @Test
        void cancelShouldTimeoutHungStageCancelFuture() throws Exception {
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> hangingCancelStage = new PipelineStage<>() {
                @Override
                public String getName() {
                    return "HungCancel";
                }

                @Override
                public CompletableFuture<StageResult> execute(PipelineContext<TaskStage, TaskEvent> context) {
                    return context.stageScopeOrCreate(getName()).result();
                }

                @Override
                public CompletableFuture<Void> cancel(PipelineContext<TaskStage, TaskEvent> context) {
                    return new CompletableFuture<>();
                }
            };
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(hangingCancelStage)
                    .cancelTimeoutMs(50)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            pipeline.execute(context);
            CompletableFuture<Void> cancelFuture = pipeline.cancel(context);

            cancelFuture.get(1, TimeUnit.SECONDS);
            assertThat(cancelFuture).isDone();
            assertThat(context.stageScope("HungCancel").isCancelled()).isTrue();
        }

        @Test
        void stageScopeShouldTrackPendingFuture() {
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();
            StageExecutionScope scope = context.stageScopeOrCreate("TrackedStage");
            CompletableFuture<Void> pendingFuture = new CompletableFuture<>();

            scope.track("pending-work", pendingFuture);
            StageCancelSnapshot beforeCancel = scope.snapshot();

            assertThat(beforeCancel.pending()).isEqualTo(1);
            assertThat(beforeCancel.pendingNames()).containsExactly("pending-work");

            scope.cancel();
            StageCancelSnapshot afterCancel = scope.snapshot();

            assertThat(scope.isCancelled()).isTrue();
            assertThat(afterCancel.started()).isEqualTo(1);
            assertThat(afterCancel.pending()).isEqualTo(1);
        }
    }

    @Nested
    class StageLifecycleTests {

        @Test
        void testExecute_shouldCallOnBeforeForAllStages() {
            
            AtomicInteger beforeCount = new AtomicInteger(0);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage1 =
                createStageWithHooks("Stage1", beforeCount, null, null);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage2 =
                createStageWithHooks("Stage2", beforeCount, null, null);
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(stage1)
                    .addStage(stage2)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            pipeline.execute(context).join();

            
            assertThat(beforeCount.get()).isEqualTo(2);
        }

        @Test
        void testExecute_shouldCallOnAfterForAllStages() {
            
            AtomicInteger afterCount = new AtomicInteger(0);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage1 =
                createStageWithHooks("Stage1", null, afterCount, null);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage2 =
                createStageWithHooks("Stage2", null, afterCount, null);
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(stage1)
                    .addStage(stage2)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            pipeline.execute(context).join();

            
            assertThat(afterCount.get()).isEqualTo(2);
        }

        @Test
        void testExecute_shouldCallOnErrorForFailedStages() {
            
            AtomicInteger errorCount = new AtomicInteger(0);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> exceptionStage =
                createStageWithHooks("Exception", null, null, errorCount);
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(createSimpleStage("Stage1"))
                    .addStage(exceptionStage)
                    .addStage(createSimpleStage("Stage2"))
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            assertThatThrownBy(() -> pipeline.execute(context).join())
                .isInstanceOf(java.util.concurrent.CompletionException.class);

            
            assertThat(errorCount.get()).isEqualTo(1);
        }

        @Test
        void testExecute_withStageThatCannotExecute_shouldSkipStage() {
            
            AtomicInteger executionCount = new AtomicInteger(0);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> nonExecutableStage =
                createNonExecutableStage("NonExecutable", executionCount);
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> executableStage = createSimpleStage("Executable");
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(nonExecutableStage)
                    .addStage(executableStage)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            pipeline.execute(context).join();

            
            assertThat(executionCount.get()).isEqualTo(0);
        }
    }

    @Nested
    class CurrentStageIndexTests {

        @Test
        void testGetCurrentStageIndex_shouldUpdateDuringExecution() {
            
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage1 = createSimpleStage("Stage1");
            PipelineStage<PipelineContext<TaskStage, TaskEvent>> stage2 = createSimpleStage("Stage2");
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder()
                    .addStage(stage1)
                    .addStage(stage2)
                    .build();
            PipelineContext<TaskStage, TaskEvent> context = createTestContext();

            
            pipeline.execute(context).join();

            
            
            assertThat(pipeline.getCurrentStageIndex()).isEqualTo(1);
        }

        @Test
        void testGetCurrentStageIndex_withEmptyPipeline_shouldBeZero() {
            
            TaskPipeline<PipelineContext<TaskStage, TaskEvent>> pipeline =
                TaskPipeline.<PipelineContext<TaskStage, TaskEvent>>builder().build();

            
            int index = pipeline.getCurrentStageIndex();

            
            assertThat(index).isEqualTo(0);
        }
    }
}
