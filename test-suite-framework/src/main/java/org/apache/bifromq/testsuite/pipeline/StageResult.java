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

package org.apache.bifromq.testsuite.pipeline;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

@Getter
public final class StageResult {

    private final boolean success;
    private final String message;
    private final Map<String, Object> data;
    private final Throwable error;

    private StageResult(boolean success, String message, Throwable error) {
        this.success = success;
        this.message = message;
        this.data = new ConcurrentHashMap<>();
        this.error = error;
    }

    public static StageResult success() {
        return new StageResult(true, null, null);
    }

    public static StageResult success(String message) {
        return new StageResult(true, message, null);
    }

    public static StageResult failure(String message) {
        return new StageResult(false, message, null);
    }

    public static StageResult failure(Throwable error) {
        return new StageResult(false, error.getMessage(), error);
    }

    public static StageResult failure(String message, Throwable error) {
        return new StageResult(false, message, error);
    }

    public StageResult withData(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    @Override
    public String toString() {
        return "StageResult{"
            + "success=" + success
            + ", message='" + message + '\''
            + ", error=" + (error != null ? error.getClass().getSimpleName() : "null")
            + '}';
    }
}
