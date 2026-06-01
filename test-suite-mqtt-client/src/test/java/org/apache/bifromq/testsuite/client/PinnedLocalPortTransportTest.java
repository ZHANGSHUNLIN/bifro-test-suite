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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PinnedLocalPortTransportTest {

    @Test
    void pollPinnedPort_withAddressPinFromDifferentThread_returnsQueuedPort() throws Exception {
        String localAddress = "127.0.0.1";

        PinnedLocalPortTransport.pinLocalPort(localAddress, 10000);

        CompletableFuture<Integer> polled = CompletableFuture.supplyAsync(
            () -> PinnedLocalPortTransport.pollPinnedPort(localAddress));

        assertThat(polled.get(5, TimeUnit.SECONDS)).isEqualTo(10000);
        assertThat(PinnedLocalPortTransport.pollPinnedPort(localAddress)).isNull();
    }
}
