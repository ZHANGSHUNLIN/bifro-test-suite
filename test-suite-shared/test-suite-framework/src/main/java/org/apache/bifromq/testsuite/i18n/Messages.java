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

package org.apache.bifromq.testsuite.i18n;

import java.util.function.BiFunction;
import lombok.Setter;

/**
 * Lightweight i18n utility that can be used from any module.
 *
 * <p>By default it returns the message key as-is (no-op resolver).
 * The application bootstrap (e.g. bifro-test-bed Spring context) is expected to call
 * {@link #setResolver(BiFunction)} to bind a real MessageSource implementation.
 */
public final class Messages {

    /**
     * Resolver: (key, args) -> localised message string.
     * Defaults to returning the key itself so the app still works without Spring.
     * -- SETTER --
     * Replace the resolver. Called once during Spring context initialisation.
     */
    @Setter
    private static volatile BiFunction<String, Object[], String> resolver =
        (key, args) -> key;

    private Messages() {
    }

    /**
     * Resolve a message key with optional positional arguments.
     */
    public static String get(String key, Object... args) {
        return resolver.apply(key, args);
    }
}
