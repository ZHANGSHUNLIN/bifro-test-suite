
package com.baidu.iot.test.suite.statemachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

/**
 * State machine for managing state transitions.
 *
 * @param <S> state type
 * @param <E> event type
 */
@Slf4j
public class StateMachine<S, E> {

    private final AtomicReference<S> currentState;
    private final Map<S, List<StateTransition<S, E>>> transitions;
    private final List<StateChangeListener<S>> listeners;
    private final S initialState;

    public StateMachine(S initialState) {
        this.initialState = initialState;
        this.currentState = new AtomicReference<>(initialState);
        this.transitions = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Add a state transition.
     *
     * @param transition transition to add
     * @return this state machine
     */
    public StateMachine<S, E> addTransition(StateTransition<S, E> transition) {
        S from = transition.getFrom();
        if (from != null) {
            transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(transition);
        } else {
            // Add to all states
            for (S state : transitions.keySet()) {
                transitions.get(state).add(transition);
            }
        }
        return this;
    }

    /**
     * Add a transition using builder.
     *
     * @param builderConsumer consumer to configure transition
     * @return this state machine
     */
    public StateMachine<S, E> addTransition(
            Function<StateTransition.Builder<S, E>, StateTransition.Builder<S, E>> builderConsumer) {
        StateTransition.Builder<S, E> builder = StateTransition.builder();
        StateTransition<S, E> transition = builderConsumer.apply(builder).build();
        return addTransition(transition);
    }

    /**
     * Add a transition that can be triggered from any state.
     *
     * @param to target state
     * @param event triggering event
     * @return this state machine
     */
    public StateMachine<S, E> addAnyTransition(S to, E event) {
        return addTransition(StateTransition.<S, E>builder()
                .from(null)
                .to(to)
                .on(event)
                .build());
    }

    /**
     * Add a state change listener.
     *
     * @param listener listener to add
     * @return this state machine
     */
    public StateMachine<S, E> addListener(StateChangeListener<S> listener) {
        if (listener != null) {
            this.listeners.add(listener);
        }
        return this;
    }

    /**
     * Attempt to transition to a new state.
     *
     * @param event triggering event
     * @return CompletableFuture that completes with true if transition succeeded
     */
    public CompletableFuture<Boolean> transition(E event) {
        return transition(event, new ConcurrentHashMap<>());
    }

    /**
     * Attempt to transition to a new state with context.
     *
     * @param event triggering event
     * @param context additional context
     * @return CompletableFuture that completes with true if transition succeeded
     */
    public CompletableFuture<Boolean> transition(E event, Map<String, Object> context) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        S fromState = currentState.get();
        StateTransition<S, E> transition = findTransition(fromState, event);

        if (transition == null) {
            log.warn("No transition found for state={}, event={}", fromState, event);
            result.complete(false);
            return result;
        }

        S toState = transition.getTo();
        StateTransitionContext<S> transitionContext = StateTransitionContext.of(fromState, toState, event);
        context.forEach(transitionContext::withMetadata);

        // Check guard
        if (!transition.canApply(fromState, event, transitionContext)) {
            log.warn("Transition guard failed: {} -> {}, event={}", fromState, toState, event);
            result.complete(false);
            return result;
        }

        // Atomic state transition
        boolean success = currentState.compareAndSet(fromState, toState);

        if (!success) {
            log.warn("State transition failed (concurrent): {} -> {}, actual={}",
                    fromState, toState, currentState.get());
            result.complete(false);
            return result;
        }

        log.info("State transition: {} -> {}, event={}, context={}",
                fromState, toState, event, context);

        // Notify listeners
        notifyListeners(fromState, toState, transitionContext);

        result.complete(true);
        return result;
    }

    /**
     * Get current state.
     *
     * @return current state
     */
    public S getCurrentState() {
        return currentState.get();
    }

    /**
     * Get reference to current state (for external access).
     *
     * @return atomic reference to current state
     */
    public AtomicReference<S> getCurrentStateReference() {
        return currentState;
    }

    /**
     * Check if a transition is possible.
     *
     * @param event triggering event
     * @return true if transition is possible
     */
    public boolean canTransition(E event) {
        S fromState = currentState.get();
        return findTransition(fromState, event) != null;
    }

    /**
     * Reset to initial state.
     */
    public void reset() {
        currentState.set(initialState);
    }

    /**
     * Find a matching transition for the current state and event.
     *
     * @param fromState current state
     * @param event triggering event
     * @return matching transition or null
     */
    private StateTransition<S, E> findTransition(S fromState, E event) {
        List<StateTransition<S, E>> stateTransitions = transitions.get(fromState);
        if (stateTransitions == null) {
            return null;
        }

        for (StateTransition<S, E> transition : stateTransitions) {
            if (transition.matches(fromState, event)) {
                return transition;
            }
        }

        // Check global transitions (from == null)
        for (List<StateTransition<S, E>> allTransitions : transitions.values()) {
            for (StateTransition<S, E> transition : allTransitions) {
                if (transition.getFrom() == null && transition.matches(fromState, event)) {
                    return transition;
                }
            }
        }

        return null;
    }

    /**
     * Notify all listeners of state change.
     *
     * @param from previous state
     * @param to new state
     * @param context transition context
     */
    private void notifyListeners(S from, S to, StateTransitionContext<S> context) {
        // Notify exiting old state
        for (StateChangeListener<S> listener : listeners) {
            try {
                listener.onStateExited(from, context);
            } catch (Exception e) {
                log.error("Error in state exit listener", e);
            }
        }

        // Notify entering new state
        for (StateChangeListener<S> listener : listeners) {
            try {
                listener.onStateEntered(to, context);
            } catch (Exception e) {
                log.error("Error in state entry listener", e);
            }
        }

        // Notify state change
        for (StateChangeListener<S> listener : listeners) {
            try {
                listener.onStateChange(from, to, context);
            } catch (Exception e) {
                log.error("Error in state change listener", e);
                listener.onTransitionError(from, to, e);
            }
        }
    }
}
