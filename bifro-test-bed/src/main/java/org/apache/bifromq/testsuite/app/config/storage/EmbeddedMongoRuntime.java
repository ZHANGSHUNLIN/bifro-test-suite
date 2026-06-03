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

import com.mongodb.ConnectionString;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.mongo.types.DatabaseDir;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.config.storage.EmbeddedMongoProperties;

@Slf4j
public class EmbeddedMongoRuntime implements AutoCloseable {

    private static final String BIND_IP = "127.0.0.1";

    private final TransitionWalker.ReachedState<RunningMongodProcess> reachedState;
    private final ConnectionString connectionString;

    private EmbeddedMongoRuntime(TransitionWalker.ReachedState<RunningMongodProcess> reachedState,
                                 ConnectionString connectionString) {
        this.reachedState = reachedState;
        this.connectionString = connectionString;
    }

    public static EmbeddedMongoRuntime start(EmbeddedMongoProperties properties) {
        try {
            Path dataDir = Path.of(properties.getDataDir()).toAbsolutePath().normalize();
            Files.createDirectories(dataDir);
            Net net = Net.of(BIND_IP, resolvePort(properties.getPort()), false);
            Version version = Version.valueOf(properties.getVersion());
            Mongod mongod = Mongod.builder()
                .net(Start.to(Net.class).initializedWith(net))
                .databaseDir(Start.to(DatabaseDir.class).initializedWith(DatabaseDir.of(dataDir)))
                .build();
            TransitionWalker.ReachedState<RunningMongodProcess> reachedState = mongod.start(version);
            int actualPort = reachedState.current().getServerAddress().getPort();
            ConnectionString connectionString =
                new ConnectionString("mongodb://" + BIND_IP + ":" + actualPort + "/" + properties.getDatabase());
            log.info("Embedded MongoDB started, version={}, uri={}, dataDir={}",
                version, connectionString.getConnectionString(), dataDir);
            return new EmbeddedMongoRuntime(reachedState, connectionString);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare embedded MongoDB data directory", e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to start embedded MongoDB", e);
        }
    }

    public ConnectionString connectionString() {
        return connectionString;
    }

    @Override
    public void close() {
        log.info("Stopping embedded MongoDB");
        reachedState.close();
        log.info("Embedded MongoDB stopped");
    }

    private static int resolvePort(int configuredPort) {
        if (configuredPort > 0) {
            return configuredPort;
        }
        return Net.defaults().getPort();
    }
}
