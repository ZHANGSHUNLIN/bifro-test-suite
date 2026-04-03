
package com.baidu.iot.test.suite.pipeline;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.baidu.iot.test.suite.statemachine.StateMachine;
import com.baidu.iot.test.suite.TaskStage;
import com.baidu.iot.test.suite.TaskEvent;

import io.vertx.core.Vertx;
import lombok.Getter;

/**
 * Context for pipeline execution.
 */
@Getter
public class PipelineContext {

    private final Vertx vertx;
    private final StateMachine<TaskStage, TaskEvent> stateMachine;
    private final Map<String, Object> stageData;
    private final Map<String, Object> config;
    private  final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final CompletableFuture<Void> completionFuture;
    private final int expectPubCount;
    private final int expectSubCount;

    public PipelineContext(Vertx vertx,
                        StateMachine<TaskStage, TaskEvent> stateMachine,
                        Map<String, Object> config,
                        int expectPubCount, int expectSubCount) {
        this.vertx = vertx;
        this.stateMachine = stateMachine;
        this.config = config;
        this.stageData = new ConcurrentHashMap<>();
        this.completionFuture = new CompletableFuture<>();
        this.expectPubCount = expectPubCount;
        this.expectSubCount = expectSubCount;
    }

    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String key, Class<T> type) {
        Object value = config.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public static PipelineContext of(Vertx vertx,
                                  StateMachine<TaskStage, TaskEvent> stateMachine,
                                  Map<String, Object> config,
                                  int expectPubCount, int expectSubCount) {
        return new PipelineContext(vertx, stateMachine, config, expectPubCount, expectSubCount);
    }

    public boolean isCancelled() {
        return this.getCancelled().get();
    }
}
