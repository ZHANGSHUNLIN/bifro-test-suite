
package com.baidu.iot.test.suite.pipeline;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Result of a pipeline stage execution.
 */
@Getter
public class StageResult {

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

    public StageResult withData(String key, Object value) {
        this.data.put(key, value);
        return this;
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

    @Override
    public String toString() {
        return "StageResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", error=" + (error != null ? error.getClass().getSimpleName() : "null") +
                '}';
    }
}
