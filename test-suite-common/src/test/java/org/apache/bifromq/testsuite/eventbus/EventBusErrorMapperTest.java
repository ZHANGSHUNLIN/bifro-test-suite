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

import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class EventBusErrorMapperTest {

    private final EventBusErrorMapper mapper = new EventBusErrorMapper();

    @Test
    void map_shouldClassifyNoConsumer() {
        EventBusRequestException result = mapper.map(
            EventBusRequestKind.NODE_METRICS,
            "node.a.metrics",
            new ReplyException(ReplyFailure.NO_HANDLERS));

        assertThat(result.getCategory()).isEqualTo(EventBusErrorMapper.NO_CONSUMER);
        assertThat(result.getKind()).isEqualTo(EventBusRequestKind.NODE_METRICS);
        assertThat(result.getAddress()).isEqualTo("node.a.metrics");
    }

    @Test
    void map_shouldUnwrapAndClassifyTimeout() {
        EventBusRequestException result = mapper.map(
            EventBusRequestKind.CLIENT_QUERY,
            "node.a.clients",
            new CompletionException(new TimeoutException("late")));

        assertThat(result.getCategory()).isEqualTo(EventBusErrorMapper.QUERY_TIMEOUT);
    }

    @Test
    void map_shouldClassifyRemoteFailure() {
        EventBusRequestException result = mapper.map(
            EventBusRequestKind.LOCAL_PORT_CAPACITY,
            "node.a.local-port-capacity",
            new ReplyException(ReplyFailure.RECIPIENT_FAILURE, "failed"));

        assertThat(result.getCategory()).isEqualTo(EventBusErrorMapper.REMOTE_HANDLER_FAILED);
    }
}
