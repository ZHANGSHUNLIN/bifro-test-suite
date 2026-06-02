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

package org.apache.bifromq.testsuite.app.bean;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClusterNodeInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String nodeId;
    private String host;
    private long timestamp;
    private MemoryInfo memory;
    private CpuInfo cpu;
    private List<NetworkInterfaceInfo> networkInterfaces;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class MemoryInfo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private long max;
        private long total;
        private long used;
        private long free;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CpuInfo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private int processors;
        private double loadAverage;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class NetworkInterfaceInfo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String name;
        private String displayName;
        private boolean up;
        private boolean loopback;
        private boolean virtual;
        private boolean multicastSupported;
        private int mtu;
        private List<String> addresses;

    }
}
