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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.bifromq.testsuite.app.cluster.storage.EmbeddedControlGuardState;
import org.apache.bifromq.testsuite.app.cluster.storage.EmbeddedControlStartupGuard;
import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.apache.bifromq.testsuite.config.role.NodeRoleProperties;
import org.apache.bifromq.testsuite.config.storage.StorageMode;
import org.apache.bifromq.testsuite.config.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Status;

class StorageModeHealthIndicatorTest {

    @Test
    void health_givenEmbeddedControlOwnerClaimed_shouldBeUp() {
        StorageModeHealthIndicator indicator = indicator(StorageMode.EMBEDDED, NodeRole.CONTROL,
            EmbeddedControlGuardState.claimed("control-1"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void health_givenEmbeddedControlOwnerNotClaimed_shouldBeDown() {
        StorageModeHealthIndicator indicator = indicator(StorageMode.EMBEDDED, NodeRole.CONTROL,
            EmbeddedControlGuardState.notRequired());

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void health_givenEmbeddedWorkerWithoutGuard_shouldBeUp() {
        StorageModeHealthIndicator indicator = indicator(StorageMode.EMBEDDED, NodeRole.WORKER, null);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @SuppressWarnings("unchecked")
    private static StorageModeHealthIndicator indicator(StorageMode storageMode, NodeRole nodeRole,
                                                        EmbeddedControlGuardState guardState) {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setMode(storageMode);
        NodeRoleProperties nodeRoleProperties = mock(NodeRoleProperties.class);
        when(nodeRoleProperties.getNodeRole()).thenReturn(nodeRole);
        ObjectProvider<EmbeddedControlStartupGuard> guardProvider = mock(ObjectProvider.class);
        if (guardState != null) {
            EmbeddedControlStartupGuard guard = mock(EmbeddedControlStartupGuard.class);
            when(guard.state()).thenReturn(guardState);
            when(guardProvider.getIfAvailable()).thenReturn(guard);
        }
        return new StorageModeHealthIndicator(storageProperties, nodeRoleProperties, guardProvider);
    }
}
