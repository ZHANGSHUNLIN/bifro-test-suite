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

package org.apache.bifromq.testsuite.config.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class NodeIdentityPropertiesTest {

    @Test
    void nodeIdentity_givenNodeId_shouldUseItAsStableIdentity() {
        NodeIdentityProperties properties = properties(Map.of("bifro.node-id", "worker-1"));

        assertThat(properties.getNodeId()).isEqualTo("worker-1");
    }

    @Test
    void nodeIdentity_givenNodeIdAlias_shouldUseItAsStableIdentity() {
        NodeIdentityProperties properties = properties(Map.of("bifro.nodeId", "worker-1"));

        assertThat(properties.getNodeId()).isEqualTo("worker-1");
    }

    @Test
    void nodeIdentity_givenNoNodeId_shouldUseLocalNode() {
        NodeIdentityProperties properties = properties(Map.of());

        assertThat(properties.getNodeId()).isEqualTo("local-node");
    }

    private NodeIdentityProperties properties(Map<String, Object> values) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", values));
        return new NodeIdentityProperties(environment);
    }
}
