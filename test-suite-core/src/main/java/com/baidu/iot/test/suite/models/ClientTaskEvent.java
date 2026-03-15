/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.models;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import com.baidu.iot.test.suite.constants.ClientTaskType;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by mafei01 in 3/11/21 1:43 PM
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClientTaskEvent {

    private String taskId;
    private String clientId;
    private ClientTaskType clientTaskType;
    private EventType eventType;
    private Map<String,Object> details;

    public ClientTaskEvent putDetail(String name, Object value) {
        if (details == null) {
            details = new ConcurrentHashMap<>();
        }
        details.put(name, value);
        return this;
    }

    public ClientTaskEvent clearDetail() {
        if (details == null) {
            details = new ConcurrentHashMap<>();
        }
        details.clear();
        return this;
    }

    public enum EventType {
        CONNECT_RESULT,
        CONNECT_STATUS,
        C2S_RESULT,
        S2C_RESULT
    }

}
