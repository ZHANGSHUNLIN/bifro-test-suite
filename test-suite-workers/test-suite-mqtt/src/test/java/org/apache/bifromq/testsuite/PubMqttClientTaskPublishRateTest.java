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
import org.apache.bifromq.testsuite.ratelimit.TokenBucketRateLimiter;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PubMqttClientTaskPublishRateTest {

    private Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
    }

    @AfterEach
    void tearDown() {
        vertx.close();
    }

    

    private PubMqttClientTask buildTask(double publishRate) {
        ClientTaskConfig taskConfig = ClientTaskConfig.builder()
            .taskId("publish-rate-test")
            .pubTopic("test/topic")
            .messageQos(MqttQoS.AT_MOST_ONCE)
            .messageSize(16)
            .publishRate(publishRate)
            .stressDurationInSec(60)
            .build();

        MqttClientConfig clientConfig = MqttClientConfig.builder()
            .clientId("interval-client-001")
            .host("localhost")
            .port(1883)
            .keepAliveInSec(120)
            .ackTimeoutInSec(120)
            .connectTimeoutInMs(30000)
            .reconnectIntervalInMs(1000)
            .willConfig(new WillConfig())
            .build();

        return PubMqttClientTask.builder()
            .vertx(vertx)
            .taskConfig(taskConfig)
            .mqttClientConfig(clientConfig)
            .taskStage(new AtomicReference<>(TaskStage.ONGOING))
            .build();
    }

    private long getRateLimiterIntervalNanos(PubMqttClientTask task) throws Exception {
        Field rateLimiterField = PubMqttClientTask.class.getDeclaredField("rateLimiter");
        rateLimiterField.setAccessible(true);
        TokenBucketRateLimiter limiter = (TokenBucketRateLimiter) rateLimiterField.get(task);
        return limiter.getIntervalNanos();
    }

    @Test
    void publishRate_0Point1_rateLimiterIntervalIs10Seconds() throws Exception {
        PubMqttClientTask task = buildTask(0.1);
        assertThat(getRateLimiterIntervalNanos(task))
            .isEqualTo(10_000_000_000L);
    }

    @Test
    void publishRate_0Point5_rateLimiterIntervalIs2Seconds() throws Exception {
        PubMqttClientTask task = buildTask(0.5);
        assertThat(getRateLimiterIntervalNanos(task))
            .isEqualTo(2_000_000_000L);
    }

    @Test
    void publishRate_1_rateLimiterIntervalIs1Second() throws Exception {
        PubMqttClientTask task = buildTask(1);
        assertThat(getRateLimiterIntervalNanos(task))
            .isEqualTo(1_000_000_000L);
    }

    @Test
    void publishRate_5_rateLimiterIntervalIs200ms() throws Exception {
        PubMqttClientTask task = buildTask(5);
        assertThat(getRateLimiterIntervalNanos(task))
            .isEqualTo(200_000_000L);
    }

    @Test
    void publishRate_2_rateLimiterIntervalIs500ms() throws Exception {
        PubMqttClientTask task = buildTask(2);
        assertThat(getRateLimiterIntervalNanos(task))
            .isEqualTo(500_000_000L);
    }

    @Test
    void publishRate_10_rateLimiterIntervalIs100ms() throws Exception {
        PubMqttClientTask task = buildTask(10);
        assertThat(getRateLimiterIntervalNanos(task))
            .isEqualTo(100_000_000L);
    }
}
