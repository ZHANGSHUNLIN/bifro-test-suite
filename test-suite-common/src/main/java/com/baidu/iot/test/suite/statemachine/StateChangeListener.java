
package com.baidu.iot.test.suite.statemachine;

/**
 * Listener for state machine transitions.
 *
 * @param <S> state type
 */
public interface StateChangeListener<S> {

    /**
     * Called when state changes.
     *
     * @param from previous state
     * @param to new state
     * @param context transition context
     */
    void onStateChange(S from, S to, StateTransitionContext<S> context);

    /**
     * Called when transition fails.
     *
     * @param from current state
     * @param to target state
     * @param error the error that caused the failure
     */
    default void onTransitionError(S from, S to, Throwable error) {
    }

    /**
     * Called when entering a state.
     *
     * @param state the state being entered
     * @param context transition context
     */
    default void onStateEntered(S state, StateTransitionContext<S> context) {
    }

    /**
     * Called when exiting a state.
     *
     * @param state the state being exited
     * @param context transition context
     */
    default void onStateExited(S state, StateTransitionContext<S> context) {
    }
}
