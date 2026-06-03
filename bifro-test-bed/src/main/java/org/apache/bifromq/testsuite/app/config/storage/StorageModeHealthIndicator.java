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

package org.apache.bifromq.testsuite.app.config.storage;

import org.apache.bifromq.testsuite.app.cluster.storage.EmbeddedControlGuardState;
import org.apache.bifromq.testsuite.app.cluster.storage.EmbeddedControlStartupGuard;
import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.apache.bifromq.testsuite.config.role.NodeRoleProperties;
import org.apache.bifromq.testsuite.config.storage.StorageMode;
import org.apache.bifromq.testsuite.config.storage.StorageProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("storageMode")
public class StorageModeHealthIndicator implements HealthIndicator {

    private final StorageProperties storageProperties;
    private final NodeRoleProperties nodeRoleProperties;
    private final ObjectProvider<EmbeddedControlStartupGuard> embeddedControlStartupGuard;

    public StorageModeHealthIndicator(StorageProperties storageProperties,
                                      NodeRoleProperties nodeRoleProperties,
                                      ObjectProvider<EmbeddedControlStartupGuard> embeddedControlStartupGuard) {
        this.storageProperties = storageProperties;
        this.nodeRoleProperties = nodeRoleProperties;
        this.embeddedControlStartupGuard = embeddedControlStartupGuard;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up()
            .withDetail("mode", storageProperties.getMode())
            .withDetail("nodeRole", nodeRoleProperties.getNodeRole());
        EmbeddedControlStartupGuard guard = embeddedControlStartupGuard.getIfAvailable();
        if (guard != null) {
            EmbeddedControlGuardState state = guard.state();
            builder.withDetail("embeddedGuardStatus", state.getStatus())
                .withDetail("embeddedOwnerNodeId", detailValue(state.getOwnerNodeId()))
                .withDetail("embeddedGuardMessage", detailValue(state.getMessage()));
            if (requiresEmbeddedOwner() && state.getStatus() != EmbeddedControlGuardState.GuardStatus.CLAIMED) {
                builder.down();
            }
        } else if (requiresEmbeddedOwner()) {
            builder.down()
                .withDetail("embeddedGuardStatus", "MISSING")
                .withDetail("embeddedGuardMessage", "Embedded control guard is not available");
        }
        return builder.build();
    }

    private boolean requiresEmbeddedOwner() {
        return storageProperties.getMode() == StorageMode.EMBEDDED
            && isControlCapable(nodeRoleProperties.getNodeRole());
    }

    private static boolean isControlCapable(NodeRole role) {
        return role == NodeRole.CONTROL || role == NodeRole.ALL;
    }

    private static String detailValue(String value) {
        return value == null ? "" : value;
    }
}
