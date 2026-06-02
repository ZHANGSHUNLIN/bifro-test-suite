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

import java.util.List;

public final class LocalPortCapacity {

    public static final int MAX_PORT_CAPACITY_PER_ADDRESS = 65535;

    private LocalPortCapacity() {
    }

    public static long calculate(boolean multiAddressEnabled, LocalPortRangeConfig portRangeConfig,
                                 List<String> localAddresses) {
        LocalPortRangeConfig config =
            portRangeConfig == null ? new LocalPortRangeConfig() : portRangeConfig.normalized();
        int addressCount = multiAddressEnabled && config.isEnabled() ? effectiveAddressCount(localAddresses) : 1;
        return (long) addressCount * portCapacity(config);
    }

    public static int effectiveAddressCount(List<String> localAddresses) {
        if (localAddresses == null || localAddresses.isEmpty()) {
            return 1;
        }
        return localAddresses.size();
    }

    public static int portCapacity(LocalPortRangeConfig portRangeConfig) {
        LocalPortRangeConfig config =
            portRangeConfig == null ? new LocalPortRangeConfig() : portRangeConfig.normalized();
        if (!config.isEnabled()) {
            return MAX_PORT_CAPACITY_PER_ADDRESS;
        }
        return LocalPortAllocator.assignablePortCount(config);
    }
}
