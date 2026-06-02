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

import java.util.Map;

public final class TemplateRenderContext {

    private final Map<String, Object> values;

    private TemplateRenderContext(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    public static TemplateRenderContext of(Map<String, Object> values) {
        return new TemplateRenderContext(values);
    }

    public static TemplateRenderContext empty() {
        return new TemplateRenderContext(Map.of());
    }

    public long longValue(String key, long defaultValue) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isEmpty()) {
            return Long.parseLong(stringValue);
        }
        return defaultValue;
    }

    public String stringValue(String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
