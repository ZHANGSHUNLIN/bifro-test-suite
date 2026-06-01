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

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class LocalPortUsage {

    private static final Path PROC_NET_TCP = Path.of("/proc/net/tcp");
    private static final Path PROC_NET_TCP6 = Path.of("/proc/net/tcp6");

    private LocalPortUsage() {
    }

    public static List<OccupiedPort> findOccupied(List<String> localAddresses, int startPort, int endPort) {
        Set<String> addressSet = new HashSet<>(localAddresses == null ? List.of() : localAddresses);
        List<OccupiedPort> occupied = new ArrayList<>();
        readProcNet(PROC_NET_TCP, addressSet, startPort, endPort, occupied);
        readProcNet(PROC_NET_TCP6, addressSet, startPort, endPort, occupied);
        return occupied;
    }

    private static void readProcNet(Path path, Set<String> localAddresses, int startPort, int endPort,
                                    List<OccupiedPort> occupied) {
        if (!Files.isReadable(path)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            for (int i = 1; i < lines.size(); i++) {
                parseLine(lines.get(i), localAddresses, startPort, endPort).ifPresent(occupied::add);
            }
        } catch (IOException | RuntimeException ignored) {
            // Port preflight is best-effort. Capacity validation still runs when procfs is unavailable.
        }
    }

    private static java.util.Optional<OccupiedPort> parseLine(String line, Set<String> localAddresses,
                                                              int startPort, int endPort) {
        String[] parts = line == null ? new String[0] : line.trim().split("\\s+");
        if (parts.length < 4) {
            return java.util.Optional.empty();
        }
        String[] local = parts[1].split(":");
        if (local.length != 2) {
            return java.util.Optional.empty();
        }
        int port = Integer.parseInt(local[1], 16);
        if (port < startPort || port > endPort) {
            return java.util.Optional.empty();
        }
        String address = parseAddress(local[0]);
        if (!localAddresses.isEmpty() && !localAddresses.contains(address) && !isWildcard(address)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(OccupiedPort.builder()
            .localAddress(address)
            .port(port)
            .state(tcpState(parts[3]))
            .build());
    }

    private static String parseAddress(String hex) {
        if (hex.length() == 8) {
            return Integer.parseInt(hex.substring(6, 8), 16) + "."
                + Integer.parseInt(hex.substring(4, 6), 16) + "."
                + Integer.parseInt(hex.substring(2, 4), 16) + "."
                + Integer.parseInt(hex.substring(0, 2), 16);
        }
        if (hex.length() == 32 && hex.startsWith("0000000000000000FFFF0000")) {
            return parseAddress(hex.substring(24));
        }
        if (hex.chars().allMatch(ch -> ch == '0')) {
            return "::";
        }
        return hex;
    }

    private static boolean isWildcard(String address) {
        return "0.0.0.0".equals(address) || "::".equals(address);
    }

    private static String tcpState(String stateHex) {
        return switch (stateHex) {
            case "01" -> "ESTABLISHED";
            case "02" -> "SYN_SENT";
            case "03" -> "SYN_RECV";
            case "04" -> "FIN_WAIT1";
            case "05" -> "FIN_WAIT2";
            case "06" -> "TIME_WAIT";
            case "07" -> "CLOSE";
            case "08" -> "CLOSE_WAIT";
            case "09" -> "LAST_ACK";
            case "0A" -> "LISTEN";
            case "0B" -> "CLOSING";
            default -> stateHex;
        };
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OccupiedPort implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String localAddress;
        private int port;
        private String state;
    }
}
