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

package org.apache.bifromq.testsuite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalPortUsageTest {

    @Test
    void findOccupied_detectsListeningIpv4Port() throws Exception {
        assumeTrue(Files.isReadable(Path.of("/proc/net/tcp")));
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = socket.getLocalPort();

            List<LocalPortUsage.OccupiedPort> occupied =
                LocalPortUsage.findOccupied(List.of("127.0.0.1"), port, port);

            assertThat(occupied)
                .anySatisfy(item -> {
                    assertThat(item.getLocalAddress()).isEqualTo("127.0.0.1");
                    assertThat(item.getPort()).isEqualTo(port);
                    assertThat(item.getState()).isEqualTo("LISTEN");
                });
        }
    }
}
