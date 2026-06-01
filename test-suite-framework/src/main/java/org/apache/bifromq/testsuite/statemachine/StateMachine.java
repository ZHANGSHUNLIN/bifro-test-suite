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

package org.apache.bifromq.testsuite.statemachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

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

    public StateMachine<S, E> addTransition(StateTransition<S, E> transition) {
        S from = transition.getFrom();
        if (from != null) {
            transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(transition);
        } else {

            for (S state : transitions.keySet()) {
                transitions.get(state).add(transition);
            }
        }
        return this;
    }

    public StateMachine<S, E> addTransition(
        Function<StateTransition.Builder<S, E>, StateTransition.Builder<S, E>> builderConsumer) {
        StateTransition.Builder<S, E> builder = StateTransition.builder();
        StateTransition<S, E> transition = builderConsumer.apply(builder).build();
        return addTransition(transition);
    }

    public StateMachine<S, E> addAnyTransition(S to, E event) {
        return addTransition(StateTransition.<S, E>builder()
            .from(null)
            .to(to)
            .on(event)
            .build());
    }

    public StateMachine<S, E> addListener(StateChangeListener<S> listener) {
        if (listener != null) {
            this.listeners.add(listener);
        }
        return this;
    }

    public CompletableFuture<Boolean> transition(E event) {
        return transition(event, new ConcurrentHashMap<>());
    }

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

        if (!transition.canApply(fromState, event, transitionContext)) {
            log.warn("Transition guard failed: {} -> {}, event={}", fromState, toState, event);
            result.complete(false);
            return result;
        }

        boolean success = currentState.compareAndSet(fromState, toState);

        if (!success) {
            log.warn("State transition failed (concurrent): {} -> {}, actual={}",
                fromState, toState, currentState.get());
            result.complete(false);
            return result;
        }

        log.info("State transition: {} -> {}, event={}, context={}",
            fromState, toState, event, context);

        notifyListeners(fromState, toState, transitionContext);

        result.complete(true);
        return result;
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> transitionGeneric(Object event) {
        return transition((E) event, new ConcurrentHashMap<>());
    }

    public S getCurrentState() {
        return currentState.get();
    }

    public AtomicReference<S> getCurrentStateReference() {
        return currentState;
    }

    public boolean canTransition(E event) {
        S fromState = currentState.get();
        return findTransition(fromState, event) != null;
    }

    public void reset() {
        currentState.set(initialState);
    }

    public List<StateTransition<S, E>> getAllTransitions() {
        List<StateTransition<S, E>> result = new ArrayList<>();
        for (List<StateTransition<S, E>> list : transitions.values()) {
            for (StateTransition<S, E> t : list) {
                if (!result.contains(t)) {
                    result.add(t);
                }
            }
        }
        return result;
    }

    public List<S> getAllStates() {
        java.util.Set<S> states = new java.util.LinkedHashSet<>();
        states.add(initialState);
        for (StateTransition<S, E> t : getAllTransitions()) {
            if (t.getFrom() != null) {
                states.add(t.getFrom());
            }
            if (t.getTo() != null) {
                states.add(t.getTo());
            }
        }
        return new ArrayList<>(states);
    }

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

        for (List<StateTransition<S, E>> allTransitions : transitions.values()) {
            for (StateTransition<S, E> transition : allTransitions) {
                if (transition.getFrom() == null && transition.matches(fromState, event)) {
                    return transition;
                }
            }
        }

        return null;
    }

    private void notifyListeners(S from, S to, StateTransitionContext<S> context) {

        for (StateChangeListener<S> listener : listeners) {
            try {
                listener.onStateExited(from, context);
            } catch (Exception e) {
                log.error("Error in state exit listener", e);
            }
        }

        for (StateChangeListener<S> listener : listeners) {
            try {
                listener.onStateEntered(to, context);
            } catch (Exception e) {
                log.error("Error in state entry listener", e);
            }
        }

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
