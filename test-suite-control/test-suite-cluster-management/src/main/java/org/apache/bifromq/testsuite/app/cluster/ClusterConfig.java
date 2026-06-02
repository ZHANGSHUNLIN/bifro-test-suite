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

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bifro.cluster")
public class ClusterConfig {


    private Duration heartbeatInterval = Duration.ofSeconds(15);
    private Duration heartbeatTimeout = Duration.ofSeconds(30);
    private Duration staleCleanupInterval = Duration.ofMinutes(1);
    private int cpuWeight = 1;
    private int memoryWeight = 1;

    public long getHeartbeatIntervalMillis() {
        return heartbeatInterval.toMillis();
    }

    public long getHeartbeatTimeoutMillis() {
        return heartbeatTimeout.toMillis();
    }
}