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

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VertxEventBusClient {

    private final EventBus eventBus;
    private final EventBusTimeoutPolicy timeoutPolicy;
    private final EventBusErrorMapper errorMapper;

    public VertxEventBusClient(EventBus eventBus, EventBusTimeoutPolicy timeoutPolicy,
                               EventBusErrorMapper errorMapper) {
        this.eventBus = eventBus;
        this.timeoutPolicy = timeoutPolicy;
        this.errorMapper = errorMapper;
    }

    public <T> CompletableFuture<T> request(String address, Object payload, EventBusRequestKind kind) {
        Duration timeout = timeoutPolicy.timeoutFor(kind);
        return eventBus.<T>request(address, payload)
            .toCompletionStage()
            .toCompletableFuture()
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .thenApply(Message::body)
            .exceptionally(e -> {
                throw errorMapper.map(kind, address, e);
            });
    }
}
