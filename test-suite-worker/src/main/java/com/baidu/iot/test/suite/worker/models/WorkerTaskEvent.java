/*
 * Copyright (C) 2021 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.iot.test.suite.worker.models;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class WorkerTaskEvent {

    private String taskId;
    private EventType eventType;
    private Map<String,Object> details;

    public WorkerTaskEvent putDetail(String name, Object value) {
        if (details == null) {
            details = new ConcurrentHashMap<>();
        }
        details.put(name, value);
        return this;
    }

    public enum EventType {
        PERIOD_RESULT,
        TOTAL_CONN_RESULT,
        TOTAL_PUB_SUB_RESULT,
        TASK_END,
        TASK_STOP,
        TASK_START,
        TASK_READY,
    }



}
