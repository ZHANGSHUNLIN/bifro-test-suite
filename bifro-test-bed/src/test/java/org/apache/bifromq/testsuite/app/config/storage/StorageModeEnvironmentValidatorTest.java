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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StorageModeEnvironmentValidatorTest {

    private final StorageModeEnvironmentValidator validator = new StorageModeEnvironmentValidator();

    @Test
    void postProcessEnvironment_givenDatabaseControlWithoutMongoConfig_shouldFail() {
        MockEnvironment environment = environment("database", "control");

        assertThatThrownBy(() -> validator.postProcessEnvironment(environment, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("database storage mode requires MongoDB configuration");
    }

    @Test
    void postProcessEnvironment_givenDefaultAllWithoutMongoConfig_shouldFail() {
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> validator.postProcessEnvironment(environment, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("database storage mode requires MongoDB configuration");
    }

    @Test
    void postProcessEnvironment_givenDatabaseControlWithMongoHost_shouldPass() {
        MockEnvironment environment = environment("database", "control")
            .withProperty("spring.data.mongodb.host", "localhost");

        assertThatCode(() -> validator.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void postProcessEnvironment_givenDatabaseControlWithMongoUri_shouldPass() {
        MockEnvironment environment = environment("database", "control")
            .withProperty("spring.data.mongodb.uri", "mongodb://localhost:27017/test");

        assertThatCode(() -> validator.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void postProcessEnvironment_givenEmbeddedControlWithoutMongoConfig_shouldPass() {
        MockEnvironment environment = environment("embedded", "control");

        assertThatCode(() -> validator.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void postProcessEnvironment_givenDatabaseWorkerWithoutMongoConfig_shouldPass() {
        MockEnvironment environment = environment("database", "worker");

        assertThatCode(() -> validator.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    private static MockEnvironment environment(String storageMode, String nodeRole) {
        return new MockEnvironment()
            .withProperty("bifro.storage.mode", storageMode)
            .withProperty("bifro.node-role", nodeRole);
    }
}
