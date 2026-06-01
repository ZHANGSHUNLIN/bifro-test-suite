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

import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

public class EventBusErrorMapper {

    public static final String NO_CONSUMER = "NO_CONSUMER";
    public static final String QUERY_TIMEOUT = "QUERY_TIMEOUT";
    public static final String REMOTE_HANDLER_FAILED = "REMOTE_HANDLER_FAILED";
    public static final String REQUEST_CANCELLED = "REQUEST_CANCELLED";
    public static final String EVENT_BUS_ERROR = "EVENT_BUS_ERROR";

    public EventBusRequestException map(EventBusRequestKind kind, String address, Throwable error) {
        Throwable root = unwrap(error);
        return new EventBusRequestException(kind, category(root), address, root);
    }

    private String category(Throwable error) {
        if (error instanceof TimeoutException) {
            return QUERY_TIMEOUT;
        }
        if (error instanceof CancellationException || error instanceof InterruptedException) {
            return REQUEST_CANCELLED;
        }
        if (error instanceof ReplyException replyException) {
            ReplyFailure failureType = replyException.failureType();
            if (failureType == ReplyFailure.NO_HANDLERS) {
                return NO_CONSUMER;
            }
            if (failureType == ReplyFailure.TIMEOUT) {
                return QUERY_TIMEOUT;
            }
            if (failureType == ReplyFailure.RECIPIENT_FAILURE) {
                return REMOTE_HANDLER_FAILED;
            }
        }
        return EVENT_BUS_ERROR;
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
            || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? new IllegalStateException("unknown EventBus error") : current;
    }
}
