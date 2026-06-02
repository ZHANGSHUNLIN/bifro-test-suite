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

package org.apache.bifromq.testsuite.worker.type;

import org.apache.bifromq.testsuite.qps.ProfileQpsSpec;
import org.apache.bifromq.testsuite.qps.QpsStrategy;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskSpec;

public final class PublishQpsStrategyFactory {

    private PublishQpsStrategyFactory() {
    }

    public static QpsStrategy create(TaskConfig config) {
        return TaskRateStrategyFactory.publish(config);
    }

    public static boolean hasDynamicPublishQps(TaskConfig config) {
        return create(config).isDynamic();
    }

    public static ProfileQpsSpec clientPublishProfileQpsSpec(TaskConfig config) {
        if (config.getQpsMode() != TaskConfig.QpsMode.DYNAMIC) {
            return null;
        }
        TaskConfig.ProfileConfig profileConfig = config.getProfileConfig();
        if (profileConfig == null
            || profileConfig.getDataPoints() == null
            || profileConfig.getDataPoints().isEmpty()) {
            return null;
        }
        return ProfileQpsSpec.builder()
            .dataPoints(profileConfig.getDataPoints())
            .totalDurationMs(profileConfig.getTotalDurationMs())
            .endBehavior(profileConfig.getEndBehavior())
            .build();
    }

    public static QpsStrategy create(WorkerTaskSpec spec) {
        return TaskRateStrategyFactory.publish(spec);
    }

    public static boolean hasDynamicPublishQps(WorkerTaskSpec spec) {
        return create(spec).isDynamic();
    }

    public static ProfileQpsSpec clientPublishProfileQpsSpec(WorkerTaskSpec spec) {
        if (spec.getQpsMode() != TaskConfig.QpsMode.DYNAMIC) {
            return null;
        }
        TaskConfig.ProfileConfig profileConfig = spec.getProfileConfig();
        if (profileConfig == null
            || profileConfig.getDataPoints() == null
            || profileConfig.getDataPoints().isEmpty()) {
            return null;
        }
        return ProfileQpsSpec.builder()
            .dataPoints(profileConfig.getDataPoints())
            .totalDurationMs(profileConfig.getTotalDurationMs())
            .endBehavior(profileConfig.getEndBehavior())
            .build();
    }

}
