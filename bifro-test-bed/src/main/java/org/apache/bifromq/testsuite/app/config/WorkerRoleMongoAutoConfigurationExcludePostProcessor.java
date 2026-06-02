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

package org.apache.bifromq.testsuite.app.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.bifromq.testsuite.cluster.NodeRole;
import org.apache.bifromq.testsuite.config.role.NodeRoleCondition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class WorkerRoleMongoAutoConfigurationExcludePostProcessor implements EnvironmentPostProcessor {

    static final String AUTO_CONFIGURE_EXCLUDE_PROPERTY = "spring.autoconfigure.exclude";
    private static final String PROPERTY_SOURCE_NAME = "workerRoleMongoAutoConfigurationExcludes";
    private static final Set<String> MONGO_AUTO_CONFIGURATIONS = Set.of(
        MongoAutoConfiguration.class.getName(),
        MongoReactiveAutoConfiguration.class.getName(),
        MongoDataAutoConfiguration.class.getName(),
        MongoReactiveDataAutoConfiguration.class.getName(),
        MongoRepositoriesAutoConfiguration.class.getName(),
        MongoReactiveRepositoriesAutoConfiguration.class.getName()
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (currentRole(environment) != NodeRole.WORKER) {
            return;
        }
        LinkedHashSet<String> excludes = new LinkedHashSet<>();
        String existingValue = environment.getProperty(AUTO_CONFIGURE_EXCLUDE_PROPERTY);
        if (existingValue != null && !existingValue.isBlank()) {
            Arrays.stream(existingValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(excludes::add);
        }
        excludes.addAll(MONGO_AUTO_CONFIGURATIONS);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(
            AUTO_CONFIGURE_EXCLUDE_PROPERTY, excludes.stream().collect(Collectors.joining(","))
        )));
    }

    private NodeRole currentRole(ConfigurableEnvironment environment) {
        String value = environment.getProperty(NodeRoleCondition.NODE_ROLE_PROPERTY);
        if (value == null || value.isBlank()) {
            value = environment.getProperty(NodeRoleCondition.NODE_ROLE_KEBAB_PROPERTY);
        }
        return NodeRole.from(value);
    }
}
