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

package org.apache.bifromq.testsuite.eventbus;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Vertx;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VertxEventBusClientTest {

    private Vertx vertx;
    private VertxEventBusClient client;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        client = new VertxEventBusClient(
            vertx.eventBus(),
            new EventBusTimeoutPolicy(),
            new EventBusErrorMapper());
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    void request_shouldReturnReplyBody() throws Exception {
        vertx.eventBus().consumer("test.echo", message -> message.reply("ok:" + message.body()));

        String result = client.<String>request("test.echo", "payload", EventBusRequestKind.NODE_METRICS)
            .get(2, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("ok:payload");
    }
}
