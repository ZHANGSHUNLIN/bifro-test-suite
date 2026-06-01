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

public class EventBusRequestException extends RuntimeException {

    private final EventBusRequestKind kind;
    private final String category;
    private final String address;

    public EventBusRequestException(EventBusRequestKind kind, String category, String address, Throwable cause) {
        super(category + ": kind=" + kind + ", address=" + address + ", message=" + rootMessage(cause), cause);
        this.kind = kind;
        this.category = category;
        this.address = address;
    }

    public EventBusRequestKind getKind() {
        return kind;
    }

    public String getCategory() {
        return category;
    }

    public String getAddress() {
        return address;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
            || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null) {
            return "unknown";
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.toString() : message;
    }
}
