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

public final class LocalPortAllocator {
    public static final int FALLBACK_PORT_RESERVE = 16;

    private LocalPortAllocator() {
    }

    public static int allocate(int clientIndex, int ipCount) {
        return allocate(clientIndex, ipCount, new LocalPortRangeConfig());
    }

    public static int allocate(int clientIndex, int ipCount, LocalPortRangeConfig config) {
        if (ipCount <= 0) {
            return 0;
        }
        LocalPortRangeConfig effectiveConfig = config == null ? new LocalPortRangeConfig() : config.normalized();
        if (!effectiveConfig.isEnabled()) {
            return 0;
        }
        int positionOnIp = clientIndex / ipCount;
        int assignablePortCount = assignablePortCount(effectiveConfig);
        if (positionOnIp >= assignablePortCount) {
            throw new IllegalStateException("Source port preallocation exhausted: clientIndex=" + clientIndex
                + ", ipCount=" + ipCount + ", portRange=" + effectiveConfig.getStartPort()
                + "-" + effectiveConfig.getEndPort() + ", excludedPorts=" + effectiveConfig.getExcludedPorts()
                + ", reservedFallbackPorts=" + fallbackPortCount(effectiveConfig)
                + ", capacity=" + ((long) ipCount * assignablePortCount));
        }
        return portAt(effectiveConfig, positionOnIp);
    }

    public static int ipIndex(int clientIndex, int ipCount) {
        if (ipCount <= 0) {
            return 0;
        }
        return clientIndex % ipCount;
    }

    public static int fallback(int fallbackIndex, LocalPortRangeConfig config) {
        LocalPortRangeConfig effectiveConfig = config == null ? new LocalPortRangeConfig() : config.normalized();
        if (!effectiveConfig.isEnabled()) {
            return 0;
        }
        int fallbackPortCount = fallbackPortCount(effectiveConfig);
        if (fallbackIndex < 0 || fallbackIndex >= fallbackPortCount) {
            throw new IllegalStateException("Source port fallback exhausted: fallbackIndex=" + fallbackIndex
                + ", portRange=" + effectiveConfig.getStartPort() + "-" + effectiveConfig.getEndPort()
                + ", excludedPorts=" + effectiveConfig.getExcludedPorts()
                + ", reservedFallbackPorts=" + fallbackPortCount);
        }
        return portAtReverse(effectiveConfig, fallbackIndex);
    }

    public static int assignablePortCount(LocalPortRangeConfig config) {
        LocalPortRangeConfig effectiveConfig = config == null ? new LocalPortRangeConfig() : config.normalized();
        if (!effectiveConfig.isEnabled()) {
            return 0;
        }
        return Math.max(0, usablePortCount(effectiveConfig) - fallbackPortCount(effectiveConfig));
    }

    public static int fallbackPortCount(LocalPortRangeConfig config) {
        LocalPortRangeConfig effectiveConfig = config == null ? new LocalPortRangeConfig() : config.normalized();
        if (!effectiveConfig.isEnabled()) {
            return 0;
        }
        int usablePortCount = usablePortCount(effectiveConfig);
        if (usablePortCount <= 1) {
            return 0;
        }
        if (usablePortCount <= FALLBACK_PORT_RESERVE * 2) {
            return 1;
        }
        return FALLBACK_PORT_RESERVE;
    }

    private static int usablePortCount(LocalPortRangeConfig config) {
        return Math.max(0, config.getEndPort() - config.getStartPort() + 1 - config.getExcludedPorts().size());
    }

    private static int portAt(LocalPortRangeConfig config, int position) {
        int port = config.getStartPort() + position;
        for (Integer excludedPort : config.getExcludedPorts()) {
            if (excludedPort <= port) {
                port++;
                continue;
            }
            break;
        }
        if (port <= config.getEndPort()) {
            return port;
        }
        throw new IllegalStateException("Source port preallocation exhausted: position=" + position
            + ", excludedPorts=" + config.getExcludedPorts());
    }

    private static int portAtReverse(LocalPortRangeConfig config, int position) {
        int port = config.getEndPort() - position;
        for (int i = config.getExcludedPorts().size() - 1; i >= 0; i--) {
            Integer excludedPort = config.getExcludedPorts().get(i);
            if (excludedPort >= port) {
                port--;
                continue;
            }
            break;
        }
        if (port >= config.getStartPort()) {
            return port;
        }
        throw new IllegalStateException("Source port fallback exhausted: position=" + position
            + ", excludedPorts=" + config.getExcludedPorts());
    }
}
