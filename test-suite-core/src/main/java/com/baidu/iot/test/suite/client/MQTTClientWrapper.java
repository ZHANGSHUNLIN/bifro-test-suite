/*
 * Copyright (C) 2024 Baidu, Inc. All Rights Reserved.
 */

package com.baidu.iot.test.suite.client;

import com.baidu.iot.test.suite.IPubMsgListener;
import com.baidu.iot.test.suite.constants.ConnectionStatus;
import com.baidu.iot.test.suite.models.TopicFilter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface MQTTClientWrapper {

    String getClientId();

    boolean isConnected();

    ConnectionStatus getStatus();

    void connect(Consumer<ConnectionStatus> connectCallback, IPubMsgListener pubMsgListener);

    CompletableFuture<List<Integer>> subscribe(Set<TopicFilter> topicFilters);

    CompletableFuture<Void> unsubscribeAll();

    CompletableFuture<Void> publish(byte[] payload, String topic, int qos, boolean isDup, boolean isRetain);

    CompletableFuture<Void> disconnect();

    CompletableFuture<Void> close();
}
