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

package org.apache.bifromq.testsuite.app.config.vertx.codec;

import io.vertx.core.eventbus.MessageCodec;
import org.apache.bifromq.testsuite.TaskSchedule;
import org.apache.bifromq.testsuite.metric.NodeMetricsRequest;
import org.apache.bifromq.testsuite.metric.NodeMetricsResponse;
import org.apache.bifromq.testsuite.pipeline.PipelineProgressEvent;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryRequest;
import org.apache.bifromq.testsuite.worker.pojo.ClientQueryResponse;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckRequest;
import org.apache.bifromq.testsuite.worker.pojo.LocalPortCapacityCheckResponse;
import org.apache.bifromq.testsuite.worker.pojo.TaskStateChangeEvent;

public enum CodecType {

    TaskSchedule(new GenericCodecSupplier<>(1004, TaskSchedule.class)),
    NodeMetricsRequest(new GenericCodecSupplier<>(1012, NodeMetricsRequest.class)),
    NodeMetricsResponse(new GenericCodecSupplier<>(1013, NodeMetricsResponse.class)),
    TaskStateChangeEvent(new GenericCodecSupplier<>(1016, TaskStateChangeEvent.class)),
    ClientQueryRequest(new GenericCodecSupplier<>(1017, ClientQueryRequest.class)),
    ClientQueryResponse(new GenericCodecSupplier<>(1018, ClientQueryResponse.class)),
    PipelineProgressEvent(new GenericCodecSupplier<>(1020, PipelineProgressEvent.class)),
    LocalPortCapacityCheckRequest(new GenericCodecSupplier<>(1021, LocalPortCapacityCheckRequest.class)),
    LocalPortCapacityCheckResponse(new GenericCodecSupplier<>(1022, LocalPortCapacityCheckResponse.class)),

    ;
    private final CodecSupplier<?> codecSupplier;

    <T> CodecType(CodecSupplier<T> codecSupplier) {
        this.codecSupplier = codecSupplier;
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> getMessageClass() {
        return (Class<T>) codecSupplier.messageClass();
    }

    @SuppressWarnings("unchecked")
    public <T> MessageCodec<T, T> getCodec() {
        return (MessageCodec<T, T>) codecSupplier.get();
    }
}
