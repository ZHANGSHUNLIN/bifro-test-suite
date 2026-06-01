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

import org.apache.bifromq.testsuite.constants.PayloadMode;

public final class PayloadStrategyFactory {

    private PayloadStrategyFactory() {
    }

    
    public static PayloadStrategy create(PayloadMode mode, String payloadTemplate) {
        return create(mode, payloadTemplate, "", "");
    }

    
    public static PayloadStrategy create(PayloadMode mode, String payloadTemplate,
                                         String clientId, String taskId) {
        PayloadMode resolvedMode = (mode != null) ? mode : PayloadMode.BIFRO;
        return switch (resolvedMode) {
            case RANDOM -> new RandomPayloadStrategy();
            case TEMPLATE -> {
                TemplatePayloadStrategy.validateTemplate(payloadTemplate);
                yield new TemplatePayloadStrategy(payloadTemplate, clientId, taskId);
            }
            default -> new BifroPayloadStrategy();
        };
    }
}
