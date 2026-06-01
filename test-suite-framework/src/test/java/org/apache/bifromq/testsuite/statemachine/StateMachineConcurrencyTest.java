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

package org.apache.bifromq.testsuite.statemachine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StateMachineConcurrencyTest {

    
    @Test
    void testConcurrentTransitions_shouldHandleSafely() throws Exception {
        
        StateMachine<TestState, TestEvent> stateMachine = new StateMachine<>(TestState.INIT);
        stateMachine.addTransition(builder -> builder
            .from(TestState.INIT)
            .to(TestState.RUNNING)
            .on(TestEvent.START));
        stateMachine.addTransition(builder -> builder
            .from(TestState.RUNNING)
            .to(TestState.SUCCESS)
            .on(TestEvent.COMPLETE));
        stateMachine.addTransition(builder -> builder
            .from(TestState.RUNNING)
            .to(TestState.FAILED)
            .on(TestEvent.FAIL));

        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger successCount = new AtomicInteger(0);

        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);
                    Boolean transitionResult = result.get(1, TimeUnit.SECONDS);
                    if (transitionResult) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    
                } finally {
                    latch.countDown();
                }
            });
        }

        
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
    }

    
    @Test
    void testConcurrentDifferentEvents_shouldHandleSafely() throws Exception {
        
        StateMachine<TestState, TestEvent> stateMachine = new StateMachine<>(TestState.INIT);
        stateMachine.addTransition(builder -> builder
            .from(TestState.INIT)
            .to(TestState.RUNNING)
            .on(TestEvent.START));
        stateMachine.addTransition(builder -> builder
            .from(TestState.RUNNING)
            .to(TestState.SUCCESS)
            .on(TestEvent.COMPLETE));

        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger successCount = new AtomicInteger(0);

        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);
                    Boolean transitionResult = result.get(1, TimeUnit.SECONDS);
                    if (transitionResult) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    
                } finally {
                    latch.countDown();
                }
            });
        }

        
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
    }

    
    @Test
    void testConcurrentTransitions_withListener_shouldNotifyOnce() throws Exception {
        
        StateMachine<TestState, TestEvent> stateMachine = new StateMachine<>(TestState.INIT);
        stateMachine.addTransition(builder -> builder
            .from(TestState.INIT)
            .to(TestState.RUNNING)
            .on(TestEvent.START));

        CountDownLatch listenerLatch = new CountDownLatch(1);
        stateMachine.addListener((from, to, event) -> {
            listenerLatch.countDown();
        });

        CountDownLatch executeLatch = new CountDownLatch(5);

        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    CompletableFuture<Boolean> result = stateMachine.transition(TestEvent.START);
                    result.get(1, TimeUnit.SECONDS);
                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    
                } finally {
                    executeLatch.countDown();
                }
            });
        }

        
        assertThat(executeLatch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        
        assertThat(listenerLatch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(stateMachine.getCurrentState()).isEqualTo(TestState.RUNNING);
    }

    
    enum TestState {
        INIT, RUNNING, SUCCESS, FAILED
    }

    
    enum TestEvent {
        START, COMPLETE, FAIL
    }
}
