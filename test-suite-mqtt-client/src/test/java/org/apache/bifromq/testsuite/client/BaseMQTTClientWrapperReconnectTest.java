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

import org.apache.bifromq.testsuite.TaskStage;
import org.apache.bifromq.testsuite.configs.ClientTaskConfig;
import org.apache.bifromq.testsuite.configs.MqttClientConfig;
import org.apache.bifromq.testsuite.constants.ConnectionStatus;
import org.apache.bifromq.testsuite.models.TopicFilter;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

    @BeforeEach
    void setUp() {
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
        taskStage = new AtomicReference<>(TaskStage.ONGOING);
    }

    @AfterEach
    void tearDown() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    
    
    

    
    @Test
    void testRecordConnectSuccess_shouldResetReconnectAttempts() {
        
        CountableWrapper wrapper = createWrapper();

        wrapper.reconnectAttempts.set(2);   

        
        wrapper.recordConnectSuccess();

        
        assertThat(wrapper.reconnectAttempts.get())
            .as("reconnectAttempts must be 0 after successful connect (Bug 1 check)")
            .isEqualTo(0);
    }

    
    @Test
    void testReconnectAttempts_accumulateAcrossCyclesWhenNotReset_demonstratesBug1() {
        CountableWrapper wrapper = createWrapper();

        
        
        wrapper.reconnectAttempts.set(2);  

        
        wrapper.tryRecoverConnect();  

        
        
        assertThat(wrapper.reconnectAttempts.get())
            .as("Bug 1: counter accumulated across cycles — after one retry in cycle 2 it reads 3 not 1")
            .isEqualTo(3);

        
        
        List<ConnectionStatus> events = new ArrayList<>();
        wrapper.connectCallback = events::add;
        wrapper.tryRecoverConnect();  

        assertThat(events)
            .as("Bug 1: client gives up on 2nd retry of cycle 2 instead of waiting for cycle budget")
            .contains(ConnectionStatus.CONNECTED_FAILED);
    }

    
    @Test
    void testReconnectAttempts_resetOnSuccess_allowsFullSecondCycle() {
        CountableWrapper wrapper = createWrapper();
        wrapper.connectCallback = status -> {
        };

        
        wrapper.reconnectAttempts.set(2);
        wrapper.recordConnectSuccess();  

        
        assertThat(wrapper.reconnectAttempts.get())
            .as("After fix: counter must be 0 so cycle 2 starts fresh")
            .isEqualTo(0);

        
        wrapper.tryRecoverConnect();  
        wrapper.tryRecoverConnect();  

        assertThat(wrapper.reconnectAttempts.get())
            .as("After 2 retries in cycle 2 the counter is 2, budget not yet exhausted")
            .isLessThan(MAX_ATTEMPTS);
    }

    
    
    

    
    @Test
    void testTryRecoverConnect_singleCall_incrementsCounterByOne() {
        CountableWrapper wrapper = createWrapper();
        wrapper.connectCallback = status -> {
        };
        assertThat(wrapper.reconnectAttempts.get()).isZero();

        wrapper.tryRecoverConnect();

        assertThat(wrapper.reconnectAttempts.get())
            .as("Single tryRecoverConnect call must increment counter by exactly 1")
            .isEqualTo(1);
    }

    
    @Test
    void testTryRecoverConnect_calledTwicePerFailedAttempt_doublesCounterCost_demonstratesBug2() {
        CountableWrapper wrapper = createWrapper();
        wrapper.connectCallback = status -> {
        };

        
        wrapper.tryRecoverConnect();   
        wrapper.tryRecoverConnect();   

        assertThat(wrapper.reconnectAttempts.get())
            .as("Bug 2: two calls for one failure advance counter by 2 instead of 1")
            .isEqualTo(2);
    }

    
    @Test
    void testTryRecoverConnect_doubleCallPerAttempt_exhaustsBudgetAfterOneAndHalfAttempts_demonstratesBug2() {
        CountableWrapper wrapper = createWrapper();
        List<ConnectionStatus> events = new ArrayList<>();
        wrapper.connectCallback = events::add;

        
        wrapper.tryRecoverConnect();   
        wrapper.tryRecoverConnect();   

        assertThat(events).as("Budget not yet exhausted after attempt 1")
            .doesNotContain(ConnectionStatus.CONNECTED_FAILED);

        
        wrapper.tryRecoverConnect();   
        assertThat(events).as("Not failed yet — counter=3 scheduled via getAndIncrement(2)=2")
            .doesNotContain(ConnectionStatus.CONNECTED_FAILED);

        
        wrapper.tryRecoverConnect();   

        assertThat(events)
            .as("Bug 2: CONNECTED_FAILED fires after 2 real attempts (4 total calls) instead of after 4 real attempts")
            .contains(ConnectionStatus.CONNECTED_FAILED);
    }

    
    @Test
    void testTryRecoverConnect_oneCallPerAttempt_allowsFullBudget() {
        CountableWrapper wrapper = createWrapper();
        List<ConnectionStatus> events = new ArrayList<>();
        wrapper.connectCallback = events::add;

        
        wrapper.tryRecoverConnect();  
        assertThat(events).as("Not failed yet after attempt 1").doesNotContain(ConnectionStatus.CONNECTED_FAILED);

        wrapper.tryRecoverConnect();  
        assertThat(events).as("Not failed yet after attempt 2").doesNotContain(ConnectionStatus.CONNECTED_FAILED);

        wrapper.tryRecoverConnect();  

        
        
        
        assertThat(events).as("Not failed yet after 3rd attempt (budget allows 3 retries, limit fires on 4th call)")
            .doesNotContain(ConnectionStatus.CONNECTED_FAILED);

        wrapper.tryRecoverConnect();  

        assertThat(events)
            .as("CONNECTED_FAILED fires after budget exhausted on 4th call with MAX_ATTEMPTS=3")
            .contains(ConnectionStatus.CONNECTED_FAILED);
    }

    @Test
    void recoverAfterConnectFailure_withFixedSourcePortBindConflict_rotatesLocalPortBeforeRetry() {
        mqttClientConfig.setLocalAddress("127.0.0.1");
        mqttClientConfig.setLocalPort(10000);
        mqttClientConfig.setLocalPortRangeConfig(LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(10004)
            .build());
        CountableWrapper wrapper = createWrapper();

        wrapper.recoverAfterConnectFailure(new java.net.BindException("Address already in use"));

        assertThat(mqttClientConfig.getLocalPort()).isNotEqualTo(10000);
        assertThat(mqttClientConfig.getLocalPort()).isBetween(10001, 10004);
    }

    
    
    

    
    @Test
    void testBug1AndBug2Combined_exhaustsBudgetPrematurely() {
        CountableWrapper wrapper = createWrapper();
        List<ConnectionStatus> events = new ArrayList<>();
        wrapper.connectCallback = events::add;

        
        wrapper.tryRecoverConnect();   
        wrapper.tryRecoverConnect();   

        
        

        
        wrapper.tryRecoverConnect();   
        
        wrapper.tryRecoverConnect();   

        assertThat(events)
            .as("Combined Bug1+Bug2: CONNECTED_FAILED after effectively 2 double-call attempts instead of 4 single-call ones")
            .contains(ConnectionStatus.CONNECTED_FAILED);
    }

    
    
    

    private CountableWrapper createWrapper() {
        return new CountableWrapper(vertx, mqttClientConfig, clientTaskConfig, taskStage);
    }

    
    static class CountableWrapper extends BaseMQTTClientWrapper {

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
    }
}
