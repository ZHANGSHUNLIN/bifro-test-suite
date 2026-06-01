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

package org.apache.bifromq.testsuite.app.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClusterConfigTest {

    @Test
    void defaultValues_shouldHaveCorrectDefaults() {
        // When
        ClusterConfig config = new ClusterConfig();

        // Then
        assertThat(config.getCpuWeight()).isEqualTo(1);
        assertThat(config.getMemoryWeight()).isEqualTo(1);
    }

    @Test
    void setters_shouldUpdateValues() {
        // Given
        ClusterConfig config = new ClusterConfig();

        // When
        config.setCpuWeight(2);
        config.setMemoryWeight(3);

        // Then
        assertThat(config.getCpuWeight()).isEqualTo(2);
        assertThat(config.getMemoryWeight()).isEqualTo(3);
    }
}
