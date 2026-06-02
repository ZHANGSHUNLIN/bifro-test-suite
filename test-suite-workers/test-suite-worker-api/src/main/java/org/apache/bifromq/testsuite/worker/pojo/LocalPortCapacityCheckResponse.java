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

package org.apache.bifromq.testsuite.worker.pojo;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalPortCapacityCheckResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String errorMessage;
    private String taskId;
    private String nodeId;
    private int assignedClients;
    private long capacity;
    private int localAddressCount;
    @Builder.Default
    private List<String> localAddresses = new ArrayList<>();
    private boolean multiAddressEnabled;
    private boolean sourcePortPreallocationEnabled;
    private int startPort;
    private int endPort;
    private int portCapacityPerAddress;
    private int reservedFallbackPortsPerAddress;
    private long missingCount;
    private int occupiedPortCount;
    @Builder.Default
    private List<OccupiedPort> occupiedPorts = new ArrayList<>();
    @Builder.Default
    private List<Integer> excludedPorts = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OccupiedPort implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String localAddress;
        private int port;
        private String state;
    }
}
