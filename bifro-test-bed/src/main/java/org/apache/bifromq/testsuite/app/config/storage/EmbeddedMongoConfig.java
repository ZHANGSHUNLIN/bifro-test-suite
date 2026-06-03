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

import com.mongodb.reactivestreams.client.MongoClient;
import org.apache.bifromq.testsuite.app.cluster.storage.EmbeddedControlStartupGuard;
import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;
import org.apache.bifromq.testsuite.config.storage.StorageMode;
import org.apache.bifromq.testsuite.config.storage.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;

@Configuration
@ConditionalOnControlPlane
@ConditionalOnProperty(prefix = "bifro.storage", name = "mode", havingValue = "embedded")
public class EmbeddedMongoConfig {

    @Bean(destroyMethod = "close")
    public EmbeddedMongoRuntime embeddedMongoRuntime(StorageProperties storageProperties,
                                                     EmbeddedControlStartupGuard startupGuard) {
        if (storageProperties.getMode() != StorageMode.EMBEDDED) {
            throw new IllegalStateException("Embedded MongoDB can only start in embedded storage mode");
        }
        startupGuard.start();
        return EmbeddedMongoRuntime.start(storageProperties.getEmbedded());
    }

    @Bean
    public MongoConnectionDetails embeddedMongoConnectionDetails(EmbeddedMongoRuntime runtime) {
        return new EmbeddedMongoConnectionDetails(runtime);
    }

    @Bean
    public ReactiveMongoDatabaseFactory embeddedReactiveMongoDatabaseFactory(MongoClient mongoClient,
                                                                            EmbeddedMongoRuntime runtime) {
        return new SimpleReactiveMongoDatabaseFactory(mongoClient, runtime.connectionString().getDatabase());
    }
}
