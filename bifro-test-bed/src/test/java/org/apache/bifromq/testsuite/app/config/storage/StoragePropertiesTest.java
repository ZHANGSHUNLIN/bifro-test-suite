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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.bifromq.testsuite.config.storage.StorageMode;
import org.apache.bifromq.testsuite.config.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class StoragePropertiesTest {

    @Test
    void storageProperties_givenNoConfig_shouldDefaultToDatabaseMode() {
        StorageProperties properties = Binder.get(new MockEnvironment())
            .bind("bifro.storage", Bindable.of(StorageProperties.class))
            .orElseGet(StorageProperties::new);

        assertThat(properties.getMode()).isEqualTo(StorageMode.DATABASE);
        assertThat(properties.getEmbedded().getDatabase()).isEqualTo("bifro-test-local");
    }

    @Test
    void storageProperties_givenEmbeddedConfig_shouldBindEmbeddedFields() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("bifro.storage.mode", "embedded")
            .withProperty("bifro.storage.embedded.data-dir", "./target/mongo")
            .withProperty("bifro.storage.embedded.database", "bifro-test-embedded")
            .withProperty("bifro.storage.embedded.port", "37017")
            .withProperty("bifro.storage.embedded.allow-control-takeover", "true");

        StorageProperties properties = Binder.get(environment)
            .bind("bifro.storage", Bindable.of(StorageProperties.class))
            .orElseGet(StorageProperties::new);

        assertThat(properties.getMode()).isEqualTo(StorageMode.EMBEDDED);
        assertThat(properties.getEmbedded().getDataDir()).isEqualTo("./target/mongo");
        assertThat(properties.getEmbedded().getDatabase()).isEqualTo("bifro-test-embedded");
        assertThat(properties.getEmbedded().getPort()).isEqualTo(37017);
        assertThat(properties.getEmbedded().isAllowControlTakeover()).isTrue();
    }
}
