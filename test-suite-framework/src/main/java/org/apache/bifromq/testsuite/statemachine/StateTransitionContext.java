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

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

@Getter
public class StateTransitionContext<S> {

    private final S fromState;
    private final S toState;
    private final Object event;
    private final Map<String, Object> metadata;
    private final Instant transitionTimestamp;
    private Throwable error;

    public StateTransitionContext(S fromState, S toState, Object event) {
        this.fromState = fromState;
        this.toState = toState;
        this.event = event;
        this.metadata = new ConcurrentHashMap<>();
        this.transitionTimestamp = Instant.now();
    }

    public static <S> StateTransitionContext<S> of(S fromState, S toState, Object event) {
        return new StateTransitionContext<>(fromState, toState, event);
    }

    public StateTransitionContext<S> withMetadata(String key, Object value) {
        if (key != null && value != null) {
            this.metadata.put(key, value);
        }
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
}
