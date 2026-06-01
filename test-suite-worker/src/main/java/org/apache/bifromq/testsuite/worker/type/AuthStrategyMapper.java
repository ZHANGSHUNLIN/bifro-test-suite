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

import org.apache.bifromq.testsuite.client.AuthStrategy;
import org.apache.bifromq.testsuite.client.AuthType;
import org.apache.bifromq.testsuite.client.ByocAuthStrategy;
import org.apache.bifromq.testsuite.client.IotCoreAuthStrategy;
import org.apache.bifromq.testsuite.client.NoneAuthStrategy;
import org.apache.bifromq.testsuite.client.NormalAuthStrategy;
import org.apache.bifromq.testsuite.worker.TaskConfig;
import org.apache.bifromq.testsuite.worker.WorkerTaskSpec;
import org.jspecify.annotations.NonNull;

public final class AuthStrategyMapper {

    private AuthStrategyMapper() {
    }

    @NonNull
    public static AuthStrategy fromTaskConfig(TaskConfig config) {
        AuthType authType = config.getAuthType();
        if (authType == null) {
            return new NormalAuthStrategy();
        }
        return switch (authType) {
            case NONE -> new NoneAuthStrategy();
            case BYOC -> new ByocAuthStrategy(
                config.getTenantId(),
                config.getPassword(),
                config.getThingIdPrefix());
            case IOT_CORE -> new IotCoreAuthStrategy(
                config.getTenantId(),
                config.getPassword(),
                config.getThingIdPrefix());
            default -> new NormalAuthStrategy();
        };
    }

    @NonNull
    public static AuthStrategy fromWorkerTaskSpec(WorkerTaskSpec spec) {
        AuthType authType = spec.getAuthType();
        if (authType == null) {
            return new NormalAuthStrategy();
        }
        return switch (authType) {
            case NONE -> new NoneAuthStrategy();
            case BYOC -> new ByocAuthStrategy(
                spec.getTenantId(),
                spec.getPassword(),
                spec.getThingIdPrefix());
            case IOT_CORE -> new IotCoreAuthStrategy(
                spec.getTenantId(),
                spec.getPassword(),
                spec.getThingIdPrefix());
            default -> new NormalAuthStrategy();
        };
    }
}
