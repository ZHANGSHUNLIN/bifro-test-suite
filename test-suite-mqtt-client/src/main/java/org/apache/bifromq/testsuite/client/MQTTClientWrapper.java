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
/*
 * Copyright (C) 2024 Baidu, Inc. All Rights Reserved.
 */

package org.apache.bifromq.testsuite.client;

import org.apache.bifromq.testsuite.IPubMsgListener;
import org.apache.bifromq.testsuite.constants.ConnectionStatus;
import org.apache.bifromq.testsuite.models.TopicFilter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface MQTTClientWrapper {

    String getClientId();

    boolean isConnected();

    ConnectionStatus getStatus();

    
    void setMessageListener(IPubMsgListener listener);

    CompletableFuture<Void> connect(Consumer<ConnectionStatus> connectCallback);

    CompletableFuture<List<Integer>> subscribe(Set<TopicFilter> topicFilters);

    CompletableFuture<Void> unsubscribeAll();

    CompletableFuture<Void> publish(byte[] payload, String topic, int qos, boolean isDup, boolean isRetain);

    CompletableFuture<Void> disconnect();

    CompletableFuture<Void> close();

    long getConnectedAt();
}
