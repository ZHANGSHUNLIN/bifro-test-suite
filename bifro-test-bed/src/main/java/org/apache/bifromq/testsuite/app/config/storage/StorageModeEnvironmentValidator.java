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

package org.apache.bifromq.testsuite.app.config.storage;

import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.apache.bifromq.testsuite.config.role.NodeRoleCondition;
import org.apache.bifromq.testsuite.config.storage.StorageMode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

public class StorageModeEnvironmentValidator implements EnvironmentPostProcessor {

    private static final String STORAGE_MODE_PROPERTY = "bifro.storage.mode";
    private static final String MONGO_URI_PROPERTY = "spring.data.mongodb.uri";
    private static final String MONGO_HOST_PROPERTY = "spring.data.mongodb.host";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        StorageMode storageMode = StorageMode.from(environment.getProperty(STORAGE_MODE_PROPERTY));
        NodeRole nodeRole = currentRole(environment);
        if (storageMode != StorageMode.DATABASE || !isControlCapable(nodeRole)) {
            return;
        }
        if (hasText(environment.getProperty(MONGO_URI_PROPERTY))
            || hasText(environment.getProperty(MONGO_HOST_PROPERTY))) {
            return;
        }
        throw new IllegalStateException("database storage mode requires MongoDB configuration on control/all nodes");
    }

    private static NodeRole currentRole(ConfigurableEnvironment environment) {
        String value = environment.getProperty(NodeRoleCondition.NODE_ROLE_PROPERTY);
        if (!hasText(value)) {
            value = environment.getProperty(NodeRoleCondition.NODE_ROLE_KEBAB_PROPERTY);
        }
        NodeRole parsed = NodeRole.from(value);
        return parsed == NodeRole.UNKNOWN ? NodeRole.ALL : parsed;
    }

    private static boolean isControlCapable(NodeRole role) {
        return role == NodeRole.CONTROL || role == NodeRole.ALL;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
