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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;

class EmbeddedMongoConnectionDetailsTest {

    @Test
    void getConnectionString_shouldExposeEmbeddedRuntimeConnectionString() {
        EmbeddedMongoRuntime runtime = mock(EmbeddedMongoRuntime.class);
        ConnectionString connectionString = new ConnectionString("mongodb://127.0.0.1:37017/bifro-test-local");
        when(runtime.connectionString()).thenReturn(connectionString);

        EmbeddedMongoConnectionDetails details = new EmbeddedMongoConnectionDetails(runtime);

        assertThat(details.getConnectionString()).isSameAs(connectionString);
    }

    @Test
    void embeddedReactiveMongoDatabaseFactory_shouldUseEmbeddedDatabaseName() {
        EmbeddedMongoRuntime runtime = mock(EmbeddedMongoRuntime.class);
        MongoClient mongoClient = mock(MongoClient.class);
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        ConnectionString connectionString = new ConnectionString("mongodb://127.0.0.1:37017/bifro-test-local");
        when(runtime.connectionString()).thenReturn(connectionString);
        when(mongoClient.getDatabase("bifro-test-local")).thenReturn(mongoDatabase);

        ReactiveMongoDatabaseFactory factory =
            new EmbeddedMongoConfig().embeddedReactiveMongoDatabaseFactory(mongoClient, runtime);
        factory.getMongoDatabase().block();

        verify(mongoClient).getDatabase("bifro-test-local");
        verify(mongoClient, never()).getDatabase("ignored");
    }
}
