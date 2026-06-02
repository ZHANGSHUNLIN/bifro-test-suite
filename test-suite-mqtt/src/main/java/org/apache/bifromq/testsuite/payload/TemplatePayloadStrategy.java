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

package org.apache.bifromq.testsuite.payload;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.apache.bifromq.testsuite.template.PlaceholderTemplate;
import org.apache.bifromq.testsuite.template.TemplateRenderContext;
import org.apache.bifromq.testsuite.template.TemplateRenderers;
import org.apache.bifromq.testsuite.template.TemplateVariable;
import org.apache.bifromq.testsuite.template.TemplateVariableResolver;

public class TemplatePayloadStrategy implements PayloadStrategy {

    private static final String CTX_INDEX = "index";
    private static final String CTX_CLIENT_ID = "client_id";
    private static final String CTX_TASK_ID = "task_id";
    private static final String PH_TIMESTAMP_MS = "timestamp_ms";
    private static final String PH_TIMESTAMP_S = "timestamp_s";
    private static final String PH_INDEX = "index";
    private static final String PH_CLIENT_ID = "client_id";
    private static final String PH_TASK_ID = "task_id";
    private static final String PH_UUID = "uuid";
    private static final String PH_RANDOM_TEXT_PREFIX = "random_text:";
    private static final String PH_RANDOM_INT_PREFIX = "random_int:";
    private static final TemplateVariableResolver PAYLOAD_VARIABLES = new PayloadVariableResolver();

    private final PlaceholderTemplate template;
    private final String clientId;
    private final String taskId;

    public TemplatePayloadStrategy(String template) {
        this(template, "", "");
    }

    public TemplatePayloadStrategy(String template, String clientId, String taskId) {
        if (template == null) {
            throw new IllegalArgumentException("Payload template must not be null");
        }
        this.template = PlaceholderTemplate.compile(template, PAYLOAD_VARIABLES);
        this.clientId = clientId != null ? clientId : "";
        this.taskId = taskId != null ? taskId : "";
    }

    public static void validateTemplate(String template) {
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("Payload template must not be null or empty");
        }

        PlaceholderTemplate.validate(template, PAYLOAD_VARIABLES);
    }

    @Override
    public byte[] buildPayload(long index, int targetSize) {
        String expanded = template.render(TemplateRenderContext.of(Map.of(
            CTX_INDEX, index,
            CTX_CLIENT_ID, clientId,
            CTX_TASK_ID, taskId)));
        return expanded.getBytes(StandardCharsets.UTF_8);
    }

    private static final class PayloadVariableResolver implements TemplateVariableResolver {

        @Override
        public Optional<TemplateVariable> resolve(String expression) {
            return switch (expression) {
                case PH_TIMESTAMP_MS -> Optional.of(context -> String.valueOf(System.currentTimeMillis()));
                case PH_TIMESTAMP_S -> Optional.of(context -> String.valueOf(System.currentTimeMillis() / 1000L));
                case PH_INDEX -> Optional.of(context -> String.valueOf(context.longValue(CTX_INDEX, 0)));
                case PH_CLIENT_ID -> Optional.of(context -> context.stringValue(CTX_CLIENT_ID));
                case PH_TASK_ID -> Optional.of(context -> context.stringValue(CTX_TASK_ID));
                case PH_UUID -> Optional.of(context -> TemplateRenderers.uuid());
                default -> resolveParameterized(expression);
            };
        }

        private Optional<TemplateVariable> resolveParameterized(String expression) {
            if (expression.startsWith(PH_RANDOM_TEXT_PREFIX)) {
                int length = parseRandomTextLength(expression);
                return Optional.of(context -> TemplateRenderers.randomText(length));
            }
            if (expression.startsWith(PH_RANDOM_INT_PREFIX)) {
                int[] range = parseRandomIntRange(expression);
                return Optional.of(context -> String.valueOf(TemplateRenderers.randomInt(range[0], range[1])));
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
