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

package org.apache.bifromq.testsuite.app.eventbus;

import io.vertx.core.Vertx;
import org.apache.bifromq.testsuite.eventbus.ClusterTaskCommandBus;
import org.apache.bifromq.testsuite.eventbus.EventBusErrorMapper;
import org.apache.bifromq.testsuite.eventbus.EventBusTimeoutPolicy;
import org.apache.bifromq.testsuite.eventbus.VertxClusterTaskCommandBus;
import org.apache.bifromq.testsuite.eventbus.VertxEventBusClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventBusAdapterConfig {

    @Bean
    public EventBusTimeoutPolicy eventBusTimeoutPolicy() {
        return new EventBusTimeoutPolicy();
    }

    @Bean
    public EventBusErrorMapper eventBusErrorMapper() {
        return new EventBusErrorMapper();
    }

    @Bean
    public VertxEventBusClient vertxEventBusClient(Vertx vertx, EventBusTimeoutPolicy timeoutPolicy,
                                                   EventBusErrorMapper errorMapper) {
        return new VertxEventBusClient(vertx.eventBus(), timeoutPolicy, errorMapper);
    }

    @Bean
    public ClusterTaskCommandBus clusterTaskCommandBus(Vertx vertx) {
        return new VertxClusterTaskCommandBus(vertx.eventBus());
    }
}
