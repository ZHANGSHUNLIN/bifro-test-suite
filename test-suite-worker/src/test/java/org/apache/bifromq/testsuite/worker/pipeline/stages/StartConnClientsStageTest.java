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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.bifromq.testsuite.ConnMqttClientTask;
import org.apache.bifromq.testsuite.Constants;
import org.apache.bifromq.testsuite.MqttClientTask;
import org.apache.bifromq.testsuite.TaskEvent;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.pipeline.PipelineStageSnapshot;
import org.apache.bifromq.testsuite.pipeline.StageResult;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;
import org.apache.bifromq.testsuite.statemachine.StateMachine;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.context.TaskExecutionContext;
import org.apache.bifromq.testsuite.worker.context.TaskPipelineContext;
import org.apache.bifromq.testsuite.worker.ratelimit.GuavaRateLimiter;
import org.apache.bifromq.testsuite.worker.ratelimit.IRateLimiter;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.type.WorkerPlanSpecMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StartConnClientsStageTest {

    @Mock
    private ConnMqttClientTask connClientTask1;

    @Mock
    private ConnMqttClientTask connClientTask2;

    @Mock
    private ConnMqttClientTask connClientTask3;

    @Mock
    private TaskConfig taskConfig;

    private Vertx vertx;
    private StateMachine<TaskStage, TaskEvent> stateMachine;
    private ConcurrentHashMap<String, MqttClientTask> connClients;
    private String taskId;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        stateMachine = new StateMachine<>(TaskStage.INIT);
        connClients = new ConcurrentHashMap<>();
        taskId = "test-task-id";

        connClients.put("client1", connClientTask1);
        connClients.put("client2", connClientTask2);
        connClients.put("client3", connClientTask3);

        when(connClientTask1.getCId()).thenReturn("client1");
        when(connClientTask2.getCId()).thenReturn("client2");
        when(connClientTask3.getCId()).thenReturn("client3");

        when(connClientTask1.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(connClientTask2.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(connClientTask3.connect()).thenReturn(CompletableFuture.completedFuture(null));

        when(taskConfig.getTaskId()).thenReturn(taskId);
        when(taskConfig.getNodeId()).thenReturn("node");
        when(taskConfig.getPublishRate()).thenReturn(1.0);
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    private TaskPipelineContext createTestContext() {
        return createTestContextWithRateLimiter(1000);
    }

    private TaskPipelineContext createTestContextWithRateLimiter(double permitsPerSecond) {
        return createTestContextWithRateLimiterAndClientMaps(
            permitsPerSecond, connClients, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    private TaskPipelineContext createTestContextWithLimiterAndStrategies(
        IRateLimiter rateLimiter, QpsStrategy connectQpsStrategy, QpsStrategy subscribeQpsStrategy) {
        TaskExecutionContext ec = new TaskExecutionContext(
            taskId, WorkerPlanSpecMapper.buildExecutionConfig(taskConfig),
            3, 5, 30, 0, 0, 1, 0,
            rateLimiter, rateLimiter, connectQpsStrategy,
            QpsStrategy.fixed(1000),
            rateLimiter, subscribeQpsStrategy,
            QpsStrategy.fixed(1000),
            null, 0, 0, 0, 0,
            connClients, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
            Optional.empty());
        return new TaskPipelineContext(vertx, stateMachine, ec, result -> {
        });
    }

    private TaskPipelineContext createTestContextWithRateLimiterAndClientMaps(
        double permitsPerSecond,
        ConcurrentHashMap<String, MqttClientTask> connClientMap,
        ConcurrentHashMap<String, MqttClientTask> pubClientMap,
        ConcurrentHashMap<String, MqttClientTask> subClientMap) {
        GuavaRateLimiter rateLimiter = new GuavaRateLimiter((int) permitsPerSecond);
        TaskExecutionContext ec = new TaskExecutionContext(
            taskId, WorkerPlanSpecMapper.buildExecutionConfig(taskConfig),
            3, 5, 30, 0, 0, 1, 0,
            rateLimiter, rateLimiter, QpsStrategy.fixed((int) permitsPerSecond),
            QpsStrategy.fixed((int) permitsPerSecond),
            rateLimiter, QpsStrategy.fixed(Integer.MAX_VALUE),
            QpsStrategy.fixed((int) permitsPerSecond),
            null, 0, 0, 0, 0,
            connClientMap, pubClientMap, subClientMap,
            Optional.empty());
        return new TaskPipelineContext(vertx, stateMachine, ec, result -> {
        });
    }

    private void awaitStartedConnection(AtomicBoolean connectStarted) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (connectStarted.get()) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private static final class TrackingRateLimiter implements IRateLimiter {
        private final int initialQps;
        private final AtomicBoolean nonPositiveRateObserved;
        private final AtomicInteger setRateCallCount = new AtomicInteger(0);
        private volatile int currentQps;

        private TrackingRateLimiter(int initialQps, AtomicBoolean nonPositiveRateObserved) {
            this.initialQps = initialQps;
            this.currentQps = initialQps;
            this.nonPositiveRateObserved = nonPositiveRateObserved;
        }

        @Override
        public int getPermitsPerSecond() {
            return currentQps;
        }

        @Override
        public double getPermitsPerSecondValue() {
            return currentQps;
        }

        @Override
        public long getIntervalNanos() {
            return 1_000_000_000L / Math.max(1, currentQps);
        }

        @Override
        public long getAcquiredCount() {
            return 0;
        }

        @Override
        public long getFailedCount() {
            return 0;
        }

        @Override
        public void resetMetrics() {
        }

        @Override
        public long getTotalWaitNanos() {
            return 0;
        }

        @Override
        public void setRate(int permitsPerSecond) {
            setRate((double) permitsPerSecond);
        }

        @Override
        public void setRate(double permitsPerSecond) {
            setRateCallCount.incrementAndGet();
            if (permitsPerSecond <= 0) {
                nonPositiveRateObserved.set(true);
            }
            currentQps = (int) Math.round(permitsPerSecond);
        }

        @Override
        public void dispose() {
        }

        @Override
        public CompletableFuture<Void> executeWithRateLimit(
            int total,
            java.util.function.Function<Integer, CompletableFuture<Void>> action) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Thread worker = new Thread(() -> {
                try {
                    for (int i = 0; i < total; i++) {
                        action.apply(i);
                        Thread.sleep(35);
                    }
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }, "tracking-rate-limiter");
            worker.setDaemon(true);
            worker.start();
            return future;
        }

        @Override
        public CompletableFuture<Void> executeContinuously(
            java.util.function.Function<Long, CompletableFuture<Void>> action) {
            return CompletableFuture.completedFuture(null);
        }

        private int getSetRateCallCount() {
            return setRateCallCount.get();
        }
    }

    @Nested
    class ConstructorTests {

        @Test
        void testConstructor_withValidParameters_shouldCreateStage() {
            StartConnClientsStage stage = new StartConnClientsStage("");
            assertThat(stage.getName()).isEqualTo("StartConnClients");
        }

        @Test
        void testConstructor_withNullClientTag_shouldCreateStage() {

            StartConnClientsStage stage = new StartConnClientsStage(null);
            assertThat(stage).isNotNull();
            assertThat(stage.getName()).isEqualTo("StartConnClients");
        }

        @Test
        void testConstructor_withEmptyClients_shouldCreateStage() {

            StartConnClientsStage stage = new StartConnClientsStage("");
            assertThat(stage).isNotNull();
            assertThat(stage.getName()).isEqualTo("StartConnClients");
        }
    }

    @Nested
    class GetNameTests {

        @Test
        void testGetName_shouldReturnCorrectName() {

            StartConnClientsStage stage = new StartConnClientsStage("");
            String name = stage.getName();
            assertThat(name).isEqualTo("StartConnClients");
        }
    }

    @Nested
    class ExecuteTests {

        @Test
        void testExecute_shouldConnectAllClients() throws Exception {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            CompletableFuture<StageResult> result = stage.execute(context);
            assertThat(result.get().isSuccess()).isTrue();
        }

        @Test
        void testExecute_withCancelledContext_shouldComplete() throws Exception {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            context.cancel();
            CompletableFuture<StageResult> result = stage.execute(context);
            assertThat(result.get()).isNotNull();
        }

        @Test
        void testExecute_withEmptyClients_shouldSkipGracefully() throws Exception {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            connClients.clear();
            TaskPipelineContext context = createTestContext();
            CompletableFuture<StageResult> result = stage.execute(context);
            assertThat(result.get().isSuccess()).isTrue();
        }

        @Test
        void testExecute_withSingleClient_shouldConnectSuccessfully() throws Exception {

            connClients.clear();
            connClients.put("client1", connClientTask1);
            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            CompletableFuture<StageResult> result = stage.execute(context);
            assertThat(result.get().isSuccess()).isTrue();
        }

        @Test
        void testExecute_withConnTag_shouldUseMergedPubSubWhenConnClientsEmpty() throws Exception {
            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            ConcurrentHashMap<String, MqttClientTask> emptyConnMap = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, MqttClientTask> pubClientMap = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, MqttClientTask> subClientMap = new ConcurrentHashMap<>();
            pubClientMap.put("pub-1", connClientTask1);
            subClientMap.put("sub-1", connClientTask2);
            TaskPipelineContext context = createTestContextWithRateLimiterAndClientMaps(
                1000, emptyConnMap, pubClientMap, subClientMap);

            CompletableFuture<StageResult> result = stage.execute(context);

            assertThat(result.get().isSuccess()).isTrue();
            assertThat(stage.getConnectedCount()).isEqualTo(2);
        }

        @Test
        void testExecute_shouldPublishEventMessages() throws Exception {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            CompletableFuture<StageResult> result = stage.execute(context);
            assertThat(result.get().isSuccess()).isTrue();
        }

        @Test
        void testExecute_withConnectProfileEndingAtZero_shouldDispatchAllWithoutRateClamp() throws Exception {
            AtomicBoolean nonPositiveRateObserved = new AtomicBoolean(false);
            TrackingRateLimiter limiter = new TrackingRateLimiter(200, nonPositiveRateObserved);
            when(taskConfig.getConnectProfileDataPoints()).thenReturn(List.of(
                new long[] {0, 3_000},
                new long[] {1_000, 3_000},
                new long[] {1_001, 0}
            ));
            TaskPipelineContext context = createTestContextWithLimiterAndStrategies(
                limiter,
                new QpsStrategy() {
                    @Override
                    public int currentQps(long elapsedMs) {
                        return 0;
                    }

                    @Override
                    public boolean isDynamic() {
                        return true;
                    }
                },
                QpsStrategy.fixed(Integer.MAX_VALUE));
            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);

            CompletableFuture<StageResult> result = stage.execute(context);

            assertThat(result.get().isSuccess()).isTrue();
            assertThat(stage.getConnectedCount()).isEqualTo(3);
            assertThat(limiter.getSetRateCallCount()).isZero();
        }

        @Test
        void testExecute_withConnectProfile_shouldUseStageLocalClock() throws Exception {
            when(taskConfig.getConnectProfileDataPoints()).thenReturn(List.of(
                new long[] {0, 0},
                new long[] {100, 30_000},
                new long[] {1_000, 0}
            ));
            TaskPipelineContext context = createTestContextWithRateLimiter(1);
            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);

            CompletableFuture<StageResult> result = stage.execute(context);

            assertThat(stage.getConnectedCount()).isEqualTo(0);
            assertThat(result.get(2, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(stage.getConnectedCount()).isEqualTo(3);
        }

        @Test
        void testExecute_withConnectWave_shouldUseFiniteDispatchPlan() throws Exception {
            when(taskConfig.getConnectWaveQpsSpec()).thenReturn(WaveQpsSpec.builder()
                .baseQps(3_000)
                .totalDurationMs(1_000)
                .build());
            TaskPipelineContext context = createTestContextWithRateLimiter(1);
            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);

            CompletableFuture<StageResult> result = stage.execute(context);

            assertThat(result.get(2, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(stage.getConnectedCount()).isEqualTo(3);
        }

        @Test
        void testExecute_withConnectionFailure_shouldRecordFailureReasonsInScope() throws Exception {
            when(connClientTask1.connect()).thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("connect timed out")));
            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            context.getPipelineProgress().add(PipelineStageSnapshot.builder()
                .key(stage.getName())
                .label(stage.getLabel())
                .status("RUNNING")
                .startedAt(System.currentTimeMillis())
                .build());

            StageResult result = stage.execute(context).get(2, TimeUnit.SECONDS);

            assertThat(result.isSuccess()).isTrue();
            assertThat(context.stageScope(stage.getName()).failureReasons())
                .containsEntry("connect_timeout", 1);
            PipelineStageSnapshot snapshot = context.refreshStageDiagnostics(stage.getName());
            assertThat(snapshot.getFailed()).isEqualTo(1);
            assertThat(snapshot.getFailureReasons()).containsEntry("connect_timeout", 1);
        }
    }

    @Nested
    class OnBeforeTests {

        @Test
        void testOnBefore_shouldLogInfo() {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            stage.onBefore(context);
            assertThat(context.isCancelled()).isFalse();
        }
    }

    @Nested
    class OnAfterTests {

        @Test
        void testOnAfter_shouldLogResult() {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            StageResult successResult = StageResult.success("All clients connected");
            TaskPipelineContext context = createTestContext();
            stage.onAfter(context, successResult);
            assertThat(context).isNotNull();
        }
    }

    @Nested
    class OnErrorTests {

        @Test
        void testOnError_shouldLogError() {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            RuntimeException error = new RuntimeException("Connection failed");
            TaskPipelineContext context = createTestContext();
            stage.onError(context, error);
            assertThat(context).isNotNull();
        }
    }

    @Nested
    class CancelTests {

        @Test
        void testCancel_shouldUnregisterEventConsumer() throws Exception {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            stage.execute(context).get();
            CompletableFuture<Void> cancelResult = stage.cancel(context);
            assertThat(cancelResult).isNotNull();
            assertThat(cancelResult.isDone()).isTrue();
        }

        @Test
        void testCancel_withNoExecution_shouldComplete() {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            CompletableFuture<Void> cancelResult = stage.cancel(context);
            assertThat(cancelResult).isNotNull();
            assertThat(cancelResult.isDone()).isTrue();
        }

        @Test
        void testCancel_shouldCompleteStageFuture() throws Exception {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();
            CompletableFuture<StageResult> executeFuture = stage.execute(context);

            stage.cancel(context);

            assertThat(executeFuture).isNotNull();
        }

        @Test
        void testCancel_withPendingConnection_shouldReturnImmediately() throws Exception {
            CompletableFuture<Void> pendingConnection = new CompletableFuture<>();
            AtomicBoolean connectStarted = new AtomicBoolean(false);
            connClients.clear();
            connClients.put("client1", connClientTask1);
            doAnswer(invocation -> {
                connectStarted.set(true);
                return pendingConnection;
            }).when(connClientTask1).connect();
            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContext();

            stage.execute(context);
            awaitStartedConnection(connectStarted);
            CompletableFuture<Void> cancelResult = stage.cancel(context);

            assertThat(cancelResult).isDone();
            assertThat(pendingConnection).isNotDone();
            pendingConnection.complete(null);
        }
    }

    @Nested
    class RateLimiterTests {

        @Test
        void testExecute_withHighRateLimiter_shouldConnectQuickly() throws Exception {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContextWithRateLimiter(10000);
            CompletableFuture<StageResult> result = stage.execute(context);
            assertThat(result.get().isSuccess()).isTrue();
        }

        @Test
        void testExecute_withLowRateLimiter_shouldConnectSlowly() throws Exception {

            StartConnClientsStage stage = new StartConnClientsStage(Constants.CONN_CLIENT_TAG);
            TaskPipelineContext context = createTestContextWithRateLimiter(10);

            CompletableFuture<StageResult> result = stage.execute(context);
            assertThat(result.get().isSuccess()).isTrue();
        }
    }

    @Nested
    class HandleClientTaskEventTests {

        @Test
        void testHandleClientTaskEvent_withSuccessEvent_shouldIncrementConnectedCount() {
        }
    }
}
