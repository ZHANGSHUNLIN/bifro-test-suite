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

package org.apache.bifromq.testsuite.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.Vertx;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.constants.ConnectionStatus;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.models.TopicFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BaseMQTTClientWrapperReconnectTest {

    private static final int MAX_ATTEMPTS = 3;

    private Vertx vertx;
    private MqttClientConfig mqttClientConfig;
    private ClientTaskConfig clientTaskConfig;
    private AtomicReference<TaskStage> taskStage;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MetricsHelper.init(registry);
        vertx = Vertx.vertx();
        mqttClientConfig = MqttClientConfig.builder()
            .clientId("test-client-reconnect")
            .host("localhost")
            .port(1883)
            .reconnectMaxAttempts(MAX_ATTEMPTS)
            .reconnectIntervalInMs(50)
            .build();

        clientTaskConfig = new ClientTaskConfig();
        clientTaskConfig.setTaskId("reconnect-test-task");
        clientTaskConfig.setNodeId("reconnect-test-node");
        taskStage = new AtomicReference<>(TaskStage.ONGOING);
    }

    @AfterEach
    void tearDown() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void recordConnectSuccessShouldResetReconnectAttempts() {
        CountableWrapper wrapper = createWrapper();

        wrapper.reconnectAttempts.set(2);
        wrapper.recordConnectSuccess();

        assertThat(wrapper.reconnectAttempts.get()).isZero();
    }

    @Test
    void duplicateRecoverAfterConnectFailureShouldScheduleOnlyOneRetry() {
        CountableWrapper wrapper = createWrapper();

        wrapper.recoverAfterConnectFailure(new RuntimeException("connection failed"));
        wrapper.recoverAfterConnectFailure(new RuntimeException("connection failed"));

        assertThat(wrapper.reconnectAttempts.get()).isEqualTo(1);
    }

    @Test
    void recoverAfterConnectFailureShouldAllowNextFailureAfterScheduledRetryStarts() {
        CountableWrapper wrapper = createWrapper();

        wrapper.recoverAfterConnectFailure(new RuntimeException("first failure"));
        wrapper.clearRecoveryScheduled();
        wrapper.recoverAfterConnectFailure(new RuntimeException("second failure"));

        assertThat(wrapper.reconnectAttempts.get()).isEqualTo(2);
    }

    @Test
    void finalFailureShouldBeRecordedOnceAndReleaseResourcesOnce() {
        CountableWrapper wrapper = createWrapper();
        List<ConnectionStatus> events = new ArrayList<>();
        wrapper.connectCallback = events::add;
        wrapper.reconnectAttempts.set(MAX_ATTEMPTS);

        wrapper.tryRecoverConnect();
        wrapper.tryRecoverConnect();

        assertThat(events).containsExactly(ConnectionStatus.CONNECTED_FAILED);
        assertThat(wrapper.getStatus()).isEqualTo(ConnectionStatus.CONNECTED_FAILED);
        assertThat(wrapper.releaseCount.get()).isEqualTo(1);
        assertThat(metricCount(BifroTaskMetric.RECONNECT_LIMIT_EXCEEDED)).isEqualTo(1.0);
    }

    @Test
    void terminalStateShouldRejectFurtherRecoveryWithoutChangingCounters() {
        CountableWrapper wrapper = createWrapper();
        wrapper.reconnectAttempts.set(MAX_ATTEMPTS);

        wrapper.tryRecoverConnect();
        int attemptsAfterFinalFailure = wrapper.reconnectAttempts.get();
        wrapper.recoverAfterConnectFailure(new RuntimeException("late failure"));
        wrapper.recordConnectFailure(new RuntimeException("late failure"));
        wrapper.recordConnectSuccess();

        assertThat(wrapper.reconnectAttempts.get()).isEqualTo(attemptsAfterFinalFailure);
        assertThat(wrapper.getStatus()).isEqualTo(ConnectionStatus.CONNECTED_FAILED);
        assertThat(wrapper.releaseCount.get()).isEqualTo(1);
        assertThat(metricCount(BifroTaskMetric.CONNECT_SUCCESS_COUNT)).isZero();
    }

    @Test
    void recoverAfterConnectFailureWithFixedSourcePortBindConflictRotatesLocalPortBeforeRetry() {
        mqttClientConfig.setLocalAddress("127.0.0.1");
        mqttClientConfig.setLocalPort(10000);
        mqttClientConfig.setLocalPortRangeConfig(LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(10004)
            .build());
        CountableWrapper wrapper = createWrapper();

        wrapper.recoverAfterConnectFailure(new BindException("Address already in use"));

        assertThat(mqttClientConfig.getLocalPort()).isNotEqualTo(10000);
        assertThat(mqttClientConfig.getLocalPort()).isBetween(10001, 10004);
    }

    private CountableWrapper createWrapper() {
        return new CountableWrapper(vertx, mqttClientConfig, clientTaskConfig, taskStage);
    }

    private double metricCount(BifroTaskMetric metric) {
        Counter counter = registry.find(metric.getName())
            .tag("taskId", clientTaskConfig.getTaskId())
            .tag("nodeId", clientTaskConfig.getNodeId())
            .counter();
        return counter == null ? 0.0 : counter.count();
    }

    static class CountableWrapper extends BaseMQTTClientWrapper {
        final AtomicInteger releaseCount = new AtomicInteger();

        CountableWrapper(Vertx vertx, MqttClientConfig clientConfig,
                         ClientTaskConfig taskConfig, AtomicReference<TaskStage> taskStage) {
            super(vertx, clientConfig, taskConfig, taskStage);
            this.status = ConnectionStatus.CONNECTED;
            this.connectCallback = status -> {
            };
        }

        @Override
        public CompletableFuture<Void> tryRecoverConnect() {
            return super.tryRecoverConnect();
        }

        @Override
        public void recordConnectSuccess() {
            super.recordConnectSuccess();
        }

        @Override
        protected void recordConnectFailure(Throwable cause) {
            super.recordConnectFailure(cause);
        }

        @Override
        public String getClientId() {
            return clientConfig.getClientId();
        }

        @Override
        public boolean isConnected() {
            return status == ConnectionStatus.CONNECTED;
        }

        @Override
        public ConnectionStatus getStatus() {
            return status;
        }

        @Override
        public CompletableFuture<List<Integer>> subscribe(Set<TopicFilter> topicFilters) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        CompletableFuture<Void> internalConnect() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> unsubscribeAll() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> publish(byte[] payload, String topic, int qos,
                                               boolean isDup, boolean isRetain) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> disconnect() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> close() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected void releaseClientResourcesAfterFinalFailure() {
            releaseCount.incrementAndGet();
        }
    }
}
