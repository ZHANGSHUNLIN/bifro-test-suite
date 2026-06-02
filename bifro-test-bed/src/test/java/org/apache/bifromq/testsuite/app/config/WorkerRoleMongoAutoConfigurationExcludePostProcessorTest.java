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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class WorkerRoleMongoAutoConfigurationExcludePostProcessorTest {

    private final WorkerRoleMongoAutoConfigurationExcludePostProcessor postProcessor =
        new WorkerRoleMongoAutoConfigurationExcludePostProcessor();

    @Test
    void postProcessEnvironment_givenWorkerRole_shouldExcludeMongoAutoConfigurations() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("bifro.node-role", "worker")
            .withProperty("spring.autoconfigure.exclude", "example.ExistingAutoConfiguration");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.autoconfigure.exclude"))
            .contains("example.ExistingAutoConfiguration")
            .contains(MongoReactiveAutoConfiguration.class.getName())
            .contains(MongoReactiveDataAutoConfiguration.class.getName());
    }

    @Test
    void postProcessEnvironment_givenControlRole_shouldKeepMongoAutoConfigurations() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("bifro.node-role", "control");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.autoconfigure.exclude")).isNull();
    }

    @Test
    void postProcessEnvironment_givenCamelCaseProperty_shouldResolveWorkerRole() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("testRole", Map.of(
            "bifro.nodeRole", "worker"
        )));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.autoconfigure.exclude"))
            .contains(MongoReactiveAutoConfiguration.class.getName());
    }
}
