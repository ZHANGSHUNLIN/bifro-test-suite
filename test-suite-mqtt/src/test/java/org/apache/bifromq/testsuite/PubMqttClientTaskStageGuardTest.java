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

package org.apache.bifromq.testsuite;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.bifromq.testsuite.client.MQTTClientWrapper;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.metric.MetricsHelper;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.Vertx;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PubMqttClientTaskStageGuardTest {

    private Vertx vertx;
    private PubMqttClientTask pubTask;
    private MQTTClientWrapper mockWrapper;
    private AtomicReference<TaskStage> taskStageRef;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        registry = new SimpleMeterRegistry();
        resetMetricsHelper();
        MetricsHelper.init(registry);

        taskStageRef = new AtomicReference<>(TaskStage.ONGOING);

        ClientTaskConfig taskConfig = ClientTaskConfig.builder()
            .taskId("test-task")
            .pubTopic("test/topic")
            .messageQos(MqttQoS.AT_MOST_ONCE)
            .messageSize(16)
            .publishRate(1.0)
            .stressDurationInSec(30)
            .build();

        MqttClientConfig clientConfig = MqttClientConfig.builder()
            .clientId("test-client-001")
            .host("localhost")
            .port(1883)
            .keepAliveInSec(120)
            .ackTimeoutInSec(120)
            .connectTimeoutInMs(30000)
            .reconnectIntervalInMs(1000)
            .willConfig(new WillConfig())
            .build();

        pubTask = PubMqttClientTask.builder()
            .vertx(vertx)
            .taskConfig(taskConfig)
            .mqttClientConfig(clientConfig)
            .taskStage(taskStageRef)
            .build();

        
        mockWrapper = mock(MQTTClientWrapper.class);
        Field wrapperField = MqttClientTask.class.getDeclaredField("mqttClientWrapper");
        wrapperField.setAccessible(true);
        wrapperField.set(pubTask, mockWrapper);
    }

    @AfterEach
    void tearDown() {
        vertx.close();
    }

    @Test
    void publish_doesNotCallWrapper_whenTaskStageIsSHUTTING() throws Exception {
        
        taskStageRef.set(TaskStage.SHUTTING);

        
        invokePrivatePublish();

        
        verify(mockWrapper, never()).publish(any(), anyString(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void publish_doesNotCallWrapper_whenTaskStageIsSHUTDOWN() throws Exception {
        taskStageRef.set(TaskStage.SHUTDOWN);

        invokePrivatePublish();

        verify(mockWrapper, never()).publish(any(), anyString(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void publish_doesNotCallWrapper_whenTaskStageIsSTOPPED() throws Exception {
        taskStageRef.set(TaskStage.STOPPED);

        invokePrivatePublish();

        verify(mockWrapper, never()).publish(any(), anyString(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void publish_doesNotCallWrapper_whenTaskStageIsFAILED() throws Exception {
        taskStageRef.set(TaskStage.FAILED);

        invokePrivatePublish();

        verify(mockWrapper, never()).publish(any(), anyString(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void publish_callsWrapper_whenTaskStageIsONGOING() throws Exception {
        
        taskStageRef.set(TaskStage.ONGOING);
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
        org.mockito.Mockito.when(mockWrapper.publish(any(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
            .thenReturn(future);

        
        invokePrivatePublish();

        
        verify(mockWrapper).publish(any(), anyString(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void publish_withMultipleTopics_usesRoundRobinTopics() throws Exception {
        taskStageRef.set(TaskStage.ONGOING);
        ClientTaskConfig multiTopicConfig = ClientTaskConfig.builder()
            .taskId("test-task")
            .pubTopic("test/topic/0")
            .pubTopics(List.of("test/topic/0", "test/topic/1", "test/topic/2"))
            .messageQos(MqttQoS.AT_MOST_ONCE)
            .messageSize(16)
            .publishRate(1.0)
            .stressDurationInSec(30)
            .build();
        pubTask = PubMqttClientTask.builder()
            .vertx(vertx)
            .taskConfig(multiTopicConfig)
            .mqttClientConfig(pubTask.getClientConfig())
            .taskStage(taskStageRef)
            .build();
        setMockWrapper(pubTask);
        org.mockito.Mockito.when(mockWrapper.publish(any(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        invokePrivatePublish();
        invokePrivatePublish();
        invokePrivatePublish();
        invokePrivatePublish();

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(mockWrapper);
        inOrder.verify(mockWrapper).publish(any(), org.mockito.Mockito.eq("test/topic/0"),
            anyInt(), anyBoolean(), anyBoolean());
        inOrder.verify(mockWrapper).publish(any(), org.mockito.Mockito.eq("test/topic/1"),
            anyInt(), anyBoolean(), anyBoolean());
        inOrder.verify(mockWrapper).publish(any(), org.mockito.Mockito.eq("test/topic/2"),
            anyInt(), anyBoolean(), anyBoolean());
        inOrder.verify(mockWrapper).publish(any(), org.mockito.Mockito.eq("test/topic/0"),
            anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void publish_successRecordsActualQosDistribution() throws Exception {
        taskStageRef.set(TaskStage.ONGOING);
        ClientTaskConfig qos1Config = ClientTaskConfig.builder()
            .taskId("test-task")
            .nodeId("node-1")
            .pubTopic("test/topic")
            .messageQos(MqttQoS.AT_LEAST_ONCE)
            .messageSize(16)
            .publishRate(1.0)
            .stressDurationInSec(30)
            .build();
        pubTask = PubMqttClientTask.builder()
            .vertx(vertx)
            .taskConfig(qos1Config)
            .mqttClientConfig(pubTask.getClientConfig())
            .taskStage(taskStageRef)
            .build();
        setMockWrapper(pubTask);
        org.mockito.Mockito.when(mockWrapper.publish(any(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
            .thenReturn(CompletableFuture.completedFuture(null));

        invokePrivatePublish();

        assertCounter("bifro_task_metric_qos1_message_count", 1.0);
        assertCounter("bifro_task_metric_publish_completion_count", 1.0);
        assertCounter("bifro_task_metric_throughput_messages", 1.0);
        org.assertj.core.api.Assertions.assertThat(registry.find("bifro_task_metric_qos0_message_count").counter())
            .isNull();
    }

    @Test
    void publish_failureDoesNotRecordSuccessfulMessageCounters() throws Exception {
        taskStageRef.set(TaskStage.ONGOING);
        org.mockito.Mockito.when(mockWrapper.publish(any(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("publish failed")));

        invokePrivatePublish();

        assertCounter("bifro_task_metric_publish_failure_count", 1.0);
        org.assertj.core.api.Assertions.assertThat(registry.find("bifro_task_metric_publish_completion_count").counter())
            .isNull();
        org.assertj.core.api.Assertions.assertThat(registry.find("bifro_task_metric_throughput_messages").counter())
            .isNull();
    }
    
    

    private void invokePrivatePublish() throws Exception {
        Method publishMethod = PubMqttClientTask.class.getDeclaredMethod("publish", long.class);
        publishMethod.setAccessible(true);
        publishMethod.invoke(pubTask, 0L);
    }

    private void setMockWrapper(PubMqttClientTask task) throws Exception {
        Field wrapperField = MqttClientTask.class.getDeclaredField("mqttClientWrapper");
        wrapperField.setAccessible(true);
        wrapperField.set(task, mockWrapper);
    }

    private void assertCounter(String name, double count) {
        org.assertj.core.api.Assertions.assertThat(registry.find(name).counter()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(registry.find(name).counter().count()).isEqualTo(count);
    }

    private void resetMetricsHelper() throws Exception {
        resetPrivateStaticCollection("COUNTER_CACHE");
        resetPrivateStaticCollection("TIMER_CACHE");
        resetPrivateStaticCollection("FROZEN_TIMER_SNAPSHOTS");
        resetPrivateStaticCollection("FAILED_METRIC_KEYS");
    }

    @SuppressWarnings("unchecked")
    private void resetPrivateStaticCollection(String fieldName) throws Exception {
        Field field = MetricsHelper.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(null);
        if (value instanceof java.util.Map<?, ?> map) {
            ((java.util.Map<Object, Object>) map).clear();
        } else if (value instanceof java.util.Set<?> set) {
            ((java.util.Set<Object>) set).clear();
        }
    }
}
