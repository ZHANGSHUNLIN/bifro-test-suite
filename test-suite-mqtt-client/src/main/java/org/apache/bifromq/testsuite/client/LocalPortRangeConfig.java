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

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalPortRangeConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private boolean enabled = false;

    @Builder.Default
    private int startPort = 10000;

    @Builder.Default
    private int endPort = 65535;
    @Builder.Default
    private List<Integer> excludedPorts = new ArrayList<>();

    public LocalPortRangeConfig normalized() {
        LocalPortRangeConfig copy = LocalPortRangeConfig.builder()
            .enabled(enabled)
            .startPort(startPort)
            .endPort(endPort)
            .excludedPorts(excludedPorts == null ? new ArrayList<>() : new ArrayList<>(excludedPorts))
            .build();
        copy.validate();
        copy.excludedPorts = copy.excludedPorts.stream()
            .filter(Objects::nonNull)
            .filter(port -> port >= copy.startPort && port <= copy.endPort)
            .distinct()
            .sorted()
            .collect(Collectors.toCollection(ArrayList::new));
        return copy;
    }

    public void validate() {
        if (startPort < 1 || startPort > 65535) {
            throw new IllegalArgumentException("local port startPort must be in range 1-65535");
        }
        if (endPort < 1 || endPort > 65535) {
            throw new IllegalArgumentException("local port endPort must be in range 1-65535");
        }
        if (startPort > endPort) {
            throw new IllegalArgumentException("local port startPort must be less than or equal to endPort");
        }
    }
}
