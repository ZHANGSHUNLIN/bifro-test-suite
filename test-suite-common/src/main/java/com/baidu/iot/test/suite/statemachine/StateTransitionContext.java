
package com.baidu.iot.test.suite.statemachine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

/**
 * Context for state transitions.
 *
 * @param <S> state type
 */
@Getter
public class StateTransitionContext<S> {

    private final S fromState;
    private final S toState;
    private final Object event;
    private final Map<String, Object> metadata;
    private Throwable error;

    public StateTransitionContext(S fromState, S toState, Object event) {
        this.fromState = fromState;
        this.toState = toState;
        this.event = event;
        this.metadata = new ConcurrentHashMap<>();
    }

    public StateTransitionContext<S> withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public StateTransitionContext<S> withError(Throwable error) {
        this.error = error;
        return this;
    }

    public static <S> StateTransitionContext<S> of(S fromState, S toState, Object event) {
        return new StateTransitionContext<>(fromState, toState, event);
    }
}
