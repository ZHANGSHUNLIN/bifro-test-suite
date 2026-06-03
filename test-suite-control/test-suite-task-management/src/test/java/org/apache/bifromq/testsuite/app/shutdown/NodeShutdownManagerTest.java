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

package org.apache.bifromq.testsuite.app.shutdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class NodeShutdownManagerTest {

    @Test
    void shutdown_shouldRunParticipantsInOrderAndInvokeCallback() throws Exception {
        GracefulShutdownProperties properties = new GracefulShutdownProperties();
        List<String> invoked = new ArrayList<>();
        NodeShutdownManager manager = new NodeShutdownManager(properties, List.of(
            participant("second", 20, invoked),
            participant("first", 10, invoked)
        ));
        CountDownLatch callback = new CountDownLatch(1);

        manager.stop(callback::countDown);

        assertThat(callback.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(invoked).containsExactly("first", "second");
        assertThat(manager.state()).isEqualTo(NodeShutdownState.COMPLETED);
    }

    @Test
    void shutdown_givenParticipantTimeout_shouldStillInvokeCallback() throws Exception {
        GracefulShutdownProperties properties = new GracefulShutdownProperties();
        properties.setParticipantTimeout(Duration.ofMillis(20));
        NodeShutdownManager manager = new NodeShutdownManager(properties, List.of(new ShutdownParticipant() {
            @Override
            public String name() {
                return "stuck";
            }

            @Override
            public CompletableFuture<Void> shutdown() {
                return new CompletableFuture<>();
            }
        }));
        CountDownLatch callback = new CountDownLatch(1);

        manager.stop(callback::countDown);

        assertThat(callback.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(manager.state()).isEqualTo(NodeShutdownState.COMPLETED);
    }

    private ShutdownParticipant participant(String name, int order, List<String> invoked) {
        return new ShutdownParticipant() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public CompletableFuture<Void> shutdown() {
                invoked.add(name);
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
