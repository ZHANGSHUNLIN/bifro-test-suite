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

package org.apache.bifromq.testsuite.template;

import java.util.ArrayList;
import java.util.List;

public final class PlaceholderTemplate {

    private final List<Token> tokens;

    private PlaceholderTemplate(List<Token> tokens) {
        this.tokens = List.copyOf(tokens);
    }

    public static PlaceholderTemplate compile(String template, TemplateVariableResolver resolver) {
        if (template == null) {
            throw new IllegalArgumentException("Template must not be null");
        }
        if (resolver == null) {
            throw new IllegalArgumentException("Template variable resolver must not be null");
        }
        return new PlaceholderTemplate(lex(template, resolver));
    }

    public static void validate(String template, TemplateVariableResolver resolver) {
        compile(template, resolver);
    }

    public String render(TemplateRenderContext context) {
        TemplateRenderContext resolvedContext = context == null ? TemplateRenderContext.empty() : context;
        StringBuilder sb = new StringBuilder();
        for (Token token : tokens) {
            sb.append(token.render(resolvedContext));
        }
        return sb.toString();
    }

    private static List<Token> lex(String template, TemplateVariableResolver resolver) {
        List<Token> result = new ArrayList<>();
        int len = template.length();
        int pos = 0;
        while (pos < len) {
            int openIdx = template.indexOf("{{", pos);
            if (openIdx == -1) {
                result.add(Token.literal(template.substring(pos)));
                break;
            }
            if (openIdx > pos) {
                result.add(Token.literal(template.substring(pos, openIdx)));
            }
            int closeIdx = template.indexOf("}}", openIdx + 2);
            if (closeIdx == -1) {
                throw new IllegalArgumentException(
                    "Unclosed placeholder '{{' at index " + openIdx + " in template: " + template);
            }
            String expression = template.substring(openIdx + 2, closeIdx).trim();
            TemplateVariable variable = resolver.resolve(expression)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Unknown placeholder '{{" + expression + "}}' in template: " + template));
            result.add(Token.variable(variable));
            pos = closeIdx + 2;
        }
        return result;
    }

    private interface Token {

        String render(TemplateRenderContext context);

        static Token literal(String literal) {
            return context -> literal;
        }

        static Token variable(TemplateVariable variable) {
            return variable::render;
        }
    }
}
