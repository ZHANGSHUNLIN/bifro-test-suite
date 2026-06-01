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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TemplatePayloadStrategy implements PayloadStrategy {

    private static final String PH_TIMESTAMP_MS = "timestamp_ms";
    private static final String PH_TIMESTAMP_S = "timestamp_s";

    private static final String PH_INDEX = "index";
    private static final String PH_CLIENT_ID = "client_id";
    private static final String PH_TASK_ID = "task_id";
    private static final String PH_UUID = "uuid";
    private static final String PH_RANDOM_TEXT_PREFIX = "random_text:";
    private static final String PH_RANDOM_INT_PREFIX = "random_int:";
    private static final char[] PRINTABLE_CHARS;

    static {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        PRINTABLE_CHARS = chars.toCharArray();
    }

    private final List<Token> tokens;
    private final String clientId;

    private final String taskId;

    public TemplatePayloadStrategy(String template) {
        this(template, "", "");
    }

    public TemplatePayloadStrategy(String template, String clientId, String taskId) {
        if (template == null) {
            throw new IllegalArgumentException("Payload template must not be null");
        }
        this.tokens = lex(template);
        this.clientId = clientId != null ? clientId : "";
        this.taskId = taskId != null ? taskId : "";
    }

    public static void validateTemplate(String template) {
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("Payload template must not be null or empty");
        }

        lex(template);
    }

    private static void appendRandomText(StringBuilder sb, int length) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(PRINTABLE_CHARS[rng.nextInt(PRINTABLE_CHARS.length)]);
        }
    }

    private static List<Token> lex(String template) {
        List<Token> result = new ArrayList<>();
        int len = template.length();
        int pos = 0;

        while (pos < len) {
            int openIdx = template.indexOf("{{", pos);
            if (openIdx == -1) {

                result.add(new Token(template.substring(pos)));
                break;
            }

            if (openIdx > pos) {
                result.add(new Token(template.substring(pos, openIdx)));
            }
            int closeIdx = template.indexOf("}}", openIdx + 2);
            if (closeIdx == -1) {
                throw new IllegalArgumentException(
                    "Unclosed placeholder '{{' at index " + openIdx + " in template: " + template);
            }
            String phContent = template.substring(openIdx + 2, closeIdx).trim();
            result.add(parseToken(phContent, template));
            pos = closeIdx + 2;
        }
        return result;
    }

    private static Token parseToken(String ph, String template) {
        if (PH_TIMESTAMP_MS.equals(ph)) {
            return new Token(TokenType.TIMESTAMP_MS);
        }
        if (PH_TIMESTAMP_S.equals(ph)) {
            return new Token(TokenType.TIMESTAMP_S);
        }
        if (PH_INDEX.equals(ph)) {
            return new Token(TokenType.INDEX);
        }
        if (PH_CLIENT_ID.equals(ph)) {
            return new Token(TokenType.CLIENT_ID);
        }
        if (PH_TASK_ID.equals(ph)) {
            return new Token(TokenType.TASK_ID);
        }
        if (PH_UUID.equals(ph)) {
            return new Token(TokenType.UUID_TOKEN);
        }
        if (ph.startsWith(PH_RANDOM_TEXT_PREFIX)) {
            String rest = ph.substring(PH_RANDOM_TEXT_PREFIX.length());
            int n;
            try {
                n = Integer.parseInt(rest);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Invalid random_text length '" + rest + "' in template: " + template);
            }
            if (n <= 0) {
                throw new IllegalArgumentException(
                    "random_text length must be > 0, got " + n + " in template: " + template);
            }
            return new Token(TokenType.RANDOM_TEXT, n, 0);
        }
        if (ph.startsWith(PH_RANDOM_INT_PREFIX)) {
            String rest = ph.substring(PH_RANDOM_INT_PREFIX.length());
            String[] parts = rest.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                    "random_int requires format 'random_int:min:max', got '" + ph + "' in template: " + template);
            }
            int min;
            int max;
            try {
                min = Integer.parseInt(parts[0].trim());
                max = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "random_int min/max must be integers in template: " + template);
            }
            if (min > max) {
                throw new IllegalArgumentException(
                    "random_int min (" + min + ") must be <= max (" + max + ") in template: " + template);
            }
            return new Token(TokenType.RANDOM_INT, min, max);
        }
        throw new IllegalArgumentException(
            "Unknown placeholder '{{" + ph + "}}' in template: " + template
                + ". Supported: {{timestamp_ms}}, {{timestamp_s}}, {{index}}, {{client_id}}, {{task_id}},"
                + " {{uuid}}, {{random_text:N}}, {{random_int:min:max}}");
    }

    @Override
    public byte[] buildPayload(long index, int targetSize) {
        String expanded = expand(index);
        return expanded.getBytes(StandardCharsets.UTF_8);
    }

    private String expand(long index) {
        StringBuilder sb = new StringBuilder();
        for (Token token : tokens) {
            switch (token.type) {
                case LITERAL:
                    sb.append(token.literal);
                    break;
                case TIMESTAMP_MS:
                    sb.append(System.currentTimeMillis());
                    break;
                case TIMESTAMP_S:
                    sb.append(System.currentTimeMillis() / 1000L);
                    break;
                case INDEX:
                    sb.append(index);
                    break;
                case CLIENT_ID:
                    sb.append(clientId);
                    break;
                case TASK_ID:
                    sb.append(taskId);
                    break;
                case RANDOM_TEXT:
                    appendRandomText(sb, token.intArg1);
                    break;
                case RANDOM_INT:
                    int min = token.intArg1;
                    int max = token.intArg2;
                    sb.append(ThreadLocalRandom.current().nextInt(min, max + 1));
                    break;
                case UUID_TOKEN:
                    sb.append(UUID.randomUUID());
                    break;
                default:
                    break;
            }
        }
        return sb.toString();
    }

    private enum TokenType {
        LITERAL,
        TIMESTAMP_MS,
        TIMESTAMP_S,
        INDEX,
        CLIENT_ID,
        TASK_ID,
        RANDOM_TEXT,
        RANDOM_INT,
        UUID_TOKEN
    }

    private static final class Token {
        final TokenType type;

        final String literal;

        final int intArg1;

        final int intArg2;

        Token(String literal) {
            this.type = TokenType.LITERAL;
            this.literal = literal;
            this.intArg1 = 0;
            this.intArg2 = 0;
        }

        Token(TokenType type) {
            this.type = type;
            this.literal = null;
            this.intArg1 = 0;
            this.intArg2 = 0;
        }

        Token(TokenType type, int arg1, int arg2) {
            this.type = type;
            this.literal = null;
            this.intArg1 = arg1;
            this.intArg2 = arg2;
        }
    }
}
