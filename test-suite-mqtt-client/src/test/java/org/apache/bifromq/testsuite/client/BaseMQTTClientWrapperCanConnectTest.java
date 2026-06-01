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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.Vertx;
import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.constants.ConnectionStatus;
import org.apache.bifromq.testsuite.metric.BifroTaskMetric;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import org.apache.bifromq.testsuite.models.TopicFilter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BaseMQTTClientWrapperCanConnectTest {

    private Vertx vertx;
    private MqttClientConfig mqttClientConfig;
    private ClientTaskConfig clientTaskConfig;
    private AtomicReference<TaskStage> taskStage;
    private TestWrapper testWrapper;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MetricsHelper.init(registry);
        vertx = Vertx.vertx();
        mqttClientConfig = MqttClientConfig.builder()
            .clientId("test-client-123")
            .host("localhost")
            .port(1883)
            .build();

        clientTaskConfig = new ClientTaskConfig();
        clientTaskConfig.setTaskId("test-task");
        clientTaskConfig.setNodeId("test-node");
        taskStage = new AtomicReference<>();

        testWrapper = new TestWrapper(vertx, mqttClientConfig, clientTaskConfig, taskStage);
    }

    @Test
    void testCanConnect_STARTING_shouldReturnTrue() {
        
        taskStage.set(TaskStage.STARTING);

        
        boolean result = testWrapper.canConnect(TaskStage.STARTING);

        
        assertThat(result).isTrue();
    }

    @Test
    void testCanConnect_SHUTTING_shouldReturnTrue() {
        
        taskStage.set(TaskStage.SHUTTING);

        
        boolean result = testWrapper.canConnect(TaskStage.SHUTTING);

        
        assertThat(result).isTrue();
    }

    @Test
    void testCanConnect_ONGOING_shouldReturnTrue() {
        
        taskStage.set(TaskStage.ONGOING);

        
        boolean result = testWrapper.canConnect(TaskStage.ONGOING);

        
        assertThat(result).isTrue();
    }

    @Test
    void testCanConnect_INIT_shouldReturnTrue() {
        
        taskStage.set(TaskStage.INIT);

        
        boolean result = testWrapper.canConnect(TaskStage.INIT);

        
        assertThat(result).isTrue();
    }

    @Test
    void testCanConnect_SHUTDOWN_shouldReturnFalse() {
        
        taskStage.set(TaskStage.SHUTDOWN);

        
        boolean result = testWrapper.canConnect(TaskStage.SHUTDOWN);

        
        assertThat(result).isFalse();
    }

    @Test
    void testCanConnect_STOPPED_shouldReturnFalse() {
        
        taskStage.set(TaskStage.STOPPED);

        
        boolean result = testWrapper.canConnect(TaskStage.STOPPED);

        
        assertThat(result).isFalse();
    }

    @Test
    void recordSubscribeSuccessShouldCountTopicFilters() {
        testWrapper.recordSubscribeSuccessForTest(2);

        double count = registry.find(BifroTaskMetric.SUBSCRIBE_COMPLETION_COUNT.getName())
            .tag("taskId", "test-task")
            .tag("nodeId", "test-node")
            .counter()
            .count();

        assertThat(count).isEqualTo(2.0);
    }

    
    private static class TestWrapper extends BaseMQTTClientWrapper {

        public TestWrapper(Vertx vertx, MqttClientConfig clientConfig,
                           ClientTaskConfig taskConfig, AtomicReference<TaskStage> taskStage) {
            super(vertx, clientConfig, taskConfig, taskStage);
        }

        @Override
        public String getClientId() {
            return clientConfig.getClientId();
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public ConnectionStatus getStatus() {
            return ConnectionStatus.INIT;
        }

        @Override
        public CompletableFuture<List<Integer>> subscribe(Set<TopicFilter> topicFilters) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        CompletableFuture<Void> internalConnect() {
            return null;
        }

        @Override
        public CompletableFuture<Void> unsubscribeAll() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> publish(byte[] payload, String topic, int qos, boolean isDup, boolean isRetain) {
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

        void recordSubscribeSuccessForTest(int subscriptionCount) {
            recordSubscribeSuccess(subscriptionCount);
        }
    }
}
