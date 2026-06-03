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

package org.apache.bifromq.testsuite.app.cluster.storage;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EmbeddedControlGuardState {

    GuardStatus status;
    String ownerNodeId;
    String message;

    public static EmbeddedControlGuardState notRequired() {
        return EmbeddedControlGuardState.builder()
            .status(GuardStatus.NOT_REQUIRED)
            .message("Embedded control guard is not required")
            .build();
    }

    public static EmbeddedControlGuardState claimed(String ownerNodeId) {
        return EmbeddedControlGuardState.builder()
            .status(GuardStatus.CLAIMED)
            .ownerNodeId(ownerNodeId)
            .message("Embedded control owner claimed")
            .build();
    }

    public static EmbeddedControlGuardState conflict(String ownerNodeId, String message) {
        return EmbeddedControlGuardState.builder()
            .status(GuardStatus.CONFLICT)
            .ownerNodeId(ownerNodeId)
            .message(message)
            .build();
    }

    public enum GuardStatus {
        NOT_REQUIRED,
        CLAIMED,
        CONFLICT
    }
}
