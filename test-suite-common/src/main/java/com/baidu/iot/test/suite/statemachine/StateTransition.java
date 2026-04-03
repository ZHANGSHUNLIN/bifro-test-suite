
package com.baidu.iot.test.suite.statemachine;

import java.util.function.Predicate;

import lombok.Getter;

/**
 * Represents a state transition rule.
 *
 * @param <S> state type
 * @param <E> event type
 */
@Getter
public class StateTransition<S, E> {

    private final S from;
    private final S to;
    private final E event;
    private final Predicate<StateTransitionContext<S>> guard;

    private StateTransition(Builder<S, E> builder) {
        this.from = builder.from;
        this.to = builder.to;
        this.event = builder.event;
        this.guard = builder.guard;
    }

    /**
     * Check if this transition can be applied.
     *
     * @param currentState the current state
     * @param event the triggering event
     * @param context the transition context
     * @return true if transition can be applied
     */
    public boolean canApply(S currentState, E event, StateTransitionContext<S> context) {
        if (!matches(currentState, event)) {
            return false;
        }
        if (guard != null) {
            return guard.test(context);
        }
        return true;
    }

    /**
     * Check if this transition matches the given state and event.
     *
     * @param currentState the current state
     * @param event the triggering event
     * @return true if matches
     */
    public boolean matches(S currentState, E event) {
        boolean fromMatches = from == null || from.equals(currentState);
        boolean eventMatches = this.event == null || this.event.equals(event);
        return fromMatches && eventMatches;
    }

    public static <S, E> Builder<S, E> builder() {
        return new Builder<>();
    }

    public static class Builder<S, E> {
        private S from;
        private S to;
        private E event;
        private Predicate<StateTransitionContext<S>> guard;

        public Builder<S, E> from(S from) {
            this.from = from;
            return this;
        }

        public Builder<S, E> to(S to) {
            this.to = to;
            return this;
        }

        public Builder<S, E> on(E event) {
            this.event = event;
            return this;
        }

        public Builder<S, E> guard(Predicate<StateTransitionContext<S>> guard) {
            this.guard = guard;
            return this;
        }

        public StateTransition<S, E> build() {
            return new StateTransition<>(this);
        }
    }
}
