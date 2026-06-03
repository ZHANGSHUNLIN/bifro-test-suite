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

package org.apache.bifromq.testsuite.worker;

import java.util.Optional;
import org.apache.bifromq.testsuite.template.PlaceholderTemplate;
import org.apache.bifromq.testsuite.template.TemplateVariable;
import org.apache.bifromq.testsuite.template.TemplateVariableResolver;

public final class PayloadTemplateValidator {

    private static final String PH_TIMESTAMP_MS = "timestamp_ms";
    private static final String PH_TIMESTAMP_S = "timestamp_s";
    private static final String PH_INDEX = "index";
    private static final String PH_CLIENT_ID = "client_id";
    private static final String PH_TASK_ID = "task_id";
    private static final String PH_UUID = "uuid";
    private static final String PH_RANDOM_TEXT_PREFIX = "random_text:";
    private static final String PH_RANDOM_INT_PREFIX = "random_int:";
    private static final TemplateVariableResolver PAYLOAD_VARIABLES = new PayloadVariableResolver();

    private PayloadTemplateValidator() {
    }

    public static void validate(String template) {
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("Payload template must not be null or empty");
        }
        PlaceholderTemplate.validate(template, PAYLOAD_VARIABLES);
    }

    private static final class PayloadVariableResolver implements TemplateVariableResolver {

        @Override
        public Optional<TemplateVariable> resolve(String expression) {
            return switch (expression) {
                case PH_TIMESTAMP_MS, PH_TIMESTAMP_S, PH_INDEX, PH_CLIENT_ID, PH_TASK_ID, PH_UUID ->
                    Optional.of(context -> "");
                default -> resolveParameterized(expression);
            };
        }

        private Optional<TemplateVariable> resolveParameterized(String expression) {
            if (expression.startsWith(PH_RANDOM_TEXT_PREFIX)) {
                parseRandomTextLength(expression);
                return Optional.of(context -> "");
            }
            if (expression.startsWith(PH_RANDOM_INT_PREFIX)) {
                parseRandomIntRange(expression);
                return Optional.of(context -> "");
            }
            return Optional.empty();
        }

        private int parseRandomTextLength(String expression) {
            String rawLength = expression.substring(PH_RANDOM_TEXT_PREFIX.length());
            int length;
            try {
                length = Integer.parseInt(rawLength);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid random_text length: " + rawLength, e);
            }
            if (length <= 0) {
                throw new IllegalArgumentException("random_text length must be > 0, got " + length);
            }
            return length;
        }

        private int[] parseRandomIntRange(String expression) {
            String rest = expression.substring(PH_RANDOM_INT_PREFIX.length());
            String[] parts = rest.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                    "random_int requires format 'random_int:min:max', got '" + expression + "'");
            }
            int min;
            int max;
            try {
                min = Integer.parseInt(parts[0].trim());
                max = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("random_int min/max must be integers", e);
            }
            if (min > max) {
                throw new IllegalArgumentException("random_int min (" + min + ") must be <= max (" + max + ")");
            }
            return new int[] {min, max};
        }
    }
}
