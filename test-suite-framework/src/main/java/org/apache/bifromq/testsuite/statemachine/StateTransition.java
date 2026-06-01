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

import java.util.function.Predicate;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public final class StateTransition<S, E> {

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

    public static <S, E> Builder<S, E> builder() {
        return new Builder<>();
    }

    public boolean canApply(S currentState, E event, StateTransitionContext<S> context) {
        if (!matches(currentState, event)) {
            return false;
        }
        if (guard != null) {
            return guard.test(context);
        }
        return true;
    }

    public boolean matches(S currentState, E event) {
        boolean fromMatches = from == null || from.equals(currentState);
        boolean eventMatches = this.event == null || this.event.equals(event);
        return fromMatches && eventMatches;
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
