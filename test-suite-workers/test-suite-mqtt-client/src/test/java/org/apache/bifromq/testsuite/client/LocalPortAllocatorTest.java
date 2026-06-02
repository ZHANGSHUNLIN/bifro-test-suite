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

package org.apache.bifromq.testsuite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LocalPortAllocatorTest {

    @Test
    void allocate_withPreallocation_doesNotWrapPorts() {
        LocalPortRangeConfig config = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(10002)
            .build();

        assertThat(LocalPortAllocator.allocate(0, 2, config)).isEqualTo(10000);
        assertThat(LocalPortAllocator.allocate(1, 2, config)).isEqualTo(10000);
        assertThat(LocalPortAllocator.allocate(2, 2, config)).isEqualTo(10001);
        assertThat(LocalPortAllocator.allocate(3, 2, config)).isEqualTo(10001);
        assertThatThrownBy(() -> LocalPortAllocator.allocate(4, 2, config))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Source port preallocation exhausted");
    }

    @Test
    void allocate_withExcludedPorts_skipsOccupiedPorts() {
        LocalPortRangeConfig config = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(10003)
            .excludedPorts(java.util.List.of(10001))
            .build();

        assertThat(LocalPortAllocator.allocate(0, 1, config)).isEqualTo(10000);
        assertThat(LocalPortAllocator.allocate(1, 1, config)).isEqualTo(10002);
        assertThatThrownBy(() -> LocalPortAllocator.allocate(2, 1, config))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Source port preallocation exhausted");
    }

    @Test
    void allocate_withPreallocation_reservesTailPortsForFallback() {
        LocalPortRangeConfig config = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(10040)
            .build();

        assertThat(LocalPortAllocator.assignablePortCount(config)).isEqualTo(25);
        assertThat(LocalPortAllocator.fallbackPortCount(config)).isEqualTo(16);
        assertThat(LocalPortAllocator.allocate(24, 1, config)).isEqualTo(10024);
        assertThatThrownBy(() -> LocalPortAllocator.allocate(25, 1, config))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Source port preallocation exhausted");
    }

    @Test
    void fallback_withSmallPreallocation_allocatesReservedTailPort() {
        LocalPortRangeConfig config = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(10004)
            .excludedPorts(java.util.List.of(10003))
            .build();

        assertThat(LocalPortAllocator.fallback(0, config)).isEqualTo(10004);
        assertThatThrownBy(() -> LocalPortAllocator.fallback(1, config))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Source port fallback exhausted");
    }

    @Test
    void fallback_withLargePreallocation_allocatesFromRangeEndAndSkipsExcludedPorts() {
        LocalPortRangeConfig config = LocalPortRangeConfig.builder()
            .enabled(true)
            .startPort(10000)
            .endPort(10040)
            .excludedPorts(java.util.List.of(10039))
            .build();

        assertThat(LocalPortAllocator.fallback(0, config)).isEqualTo(10040);
        assertThat(LocalPortAllocator.fallback(1, config)).isEqualTo(10038);
        assertThat(LocalPortAllocator.fallback(2, config)).isEqualTo(10037);
        assertThat(LocalPortAllocator.fallback(15, config)).isEqualTo(10024);
        assertThatThrownBy(() -> LocalPortAllocator.fallback(16, config))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Source port fallback exhausted");
    }
}
