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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.qps.WaveQpsSpec;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StressStageDynamicQpsTest {

    private static final long QPS_UPDATE_INTERVAL_MS = 100;

    private Vertx vertx;
    private AtomicReference<TaskStage> taskStageRef;

    private static MqttClientConfig buildClientConfig(String clientId) {
        return MqttClientConfig.builder()
            .clientId(clientId)
            .host("localhost")
            .port(1883)
            .keepAliveInSec(120)
            .ackTimeoutInSec(120)
            .connectTimeoutInMs(30000)
            .reconnectIntervalInMs(1000)
            .willConfig(new WillConfig())
            .build();
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        taskStageRef = new AtomicReference<>(TaskStage.STARTING);
    }

    

    @AfterEach
    void tearDown() {
        vertx.close();
    }

    

    @Test
    void dynamicQps_rateUpdatedEvery100ms() throws Exception {
        
        WaveQpsSpec waveSpec = WaveQpsSpec.builder()
            .baseQps(100)
            .totalDurationMs(5000)
            .components(List.of(
                WaveQpsSpec.Component.builder()
                    .amplitude(50)
                    .periodFraction(1.0)
                    .phase(0)
                    .build()
            ))
            .build();

        ClientTaskConfig taskConfig = ClientTaskConfig.builder()
            .taskId("dyn-task")
            .pubTopic("test/dyn")
            .messageQos(MqttQoS.AT_MOST_ONCE)
            .messageSize(16)
            .publishRate(1.0)
            .stressDurationInSec(5)
            .waveQpsSpec(waveSpec)
            .build();

        MqttClientConfig clientConfig = buildClientConfig("dyn-client-001");

        PubMqttClientTask pubTask = PubMqttClientTask.builder()
            .vertx(vertx)
            .taskConfig(taskConfig)
            .mqttClientConfig(clientConfig)
            .taskStage(taskStageRef)
            .build();

        
        
        AtomicInteger currentQpsCalls = new AtomicInteger(0);
        QpsStrategy spyStrategy = new QpsStrategy() {
            @Override
            public int currentQps(long elapsedMs) {
                currentQpsCalls.incrementAndGet();
                return 100;
            }

            @Override
            public boolean isDynamic() {
                return true;
            }
        };
        injectField(pubTask, "qpsStrategy", spyStrategy);

        
        pubTask.startPublishing();
        TimeUnit.MILLISECONDS.sleep(550);

        
        pubTask.stopPublishing();

        
        int calls = currentQpsCalls.get();
        assertThat(calls)
            .as("currentQps() should be called ~5 times in 500ms at 100ms interval (got %d)", calls)
            .isBetween(3, 8);
    }

    @Test
    void fixedQps_noPeriodicTimerStarted() throws Exception {
        
        ClientTaskConfig taskConfig = ClientTaskConfig.builder()
            .taskId("fixed-task")
            .pubTopic("test/fixed")
            .messageQos(MqttQoS.AT_MOST_ONCE)
            .messageSize(16)
            .publishRate(1.0)
            .stressDurationInSec(5)
            .build();

        MqttClientConfig clientConfig = buildClientConfig("fixed-client-001");

        PubMqttClientTask pubTask = PubMqttClientTask.builder()
            .vertx(vertx)
            .taskConfig(taskConfig)
            .mqttClientConfig(clientConfig)
            .taskStage(taskStageRef)
            .build();

        
        AtomicInteger dynamicCalls = new AtomicInteger(0);
        QpsStrategy spyFixed = new QpsStrategy() {
            @Override
            public int currentQps(long elapsedMs) {
                dynamicCalls.incrementAndGet();
                return 10;
            }

            @Override
            public boolean isDynamic() {
                return false;
            }
        };
        injectField(pubTask, "qpsStrategy", spyFixed);

        pubTask.startPublishing();
        TimeUnit.MILLISECONDS.sleep(400);
        pubTask.stopPublishing();

        
        assertThat(dynamicCalls.get())
            .as("Fixed strategy should not trigger periodic currentQps() calls")
            .isEqualTo(0);

        
        Field timerField = PubMqttClientTask.class.getDeclaredField("qpsUpdateTimerId");
        timerField.setAccessible(true);
        assertThat(timerField.get(pubTask)).isNull();
    }
}
