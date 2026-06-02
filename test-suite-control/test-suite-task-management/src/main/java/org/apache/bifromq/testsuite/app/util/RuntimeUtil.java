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

package org.apache.bifromq.testsuite.app.util;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.bean.ClusterNodeInfo;

@Slf4j
public class RuntimeUtil {

    public static double getSystemLoadAverage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(
                OperatingSystemMXBean.class);
            return osBean.getSystemLoadAverage();
        } catch (Exception e) {
            return -1;
        }
    }

    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static List<ClusterNodeInfo.NetworkInterfaceInfo> getNetworkInterfaces() {
        try {
            List<NetworkInterface> networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            networkInterfaces.sort(Comparator.comparing(NetworkInterface::getName));
            return networkInterfaces.stream()
                .map(RuntimeUtil::toNetworkInterfaceInfo)
                .filter(networkInterfaceInfo -> !networkInterfaceInfo.getAddresses().isEmpty())
                .collect(Collectors.toList());
        } catch (SocketException e) {
            log.warn("Failed to collect network interface info", e);
            return List.of();
        }
    }

    private static ClusterNodeInfo.NetworkInterfaceInfo toNetworkInterfaceInfo(NetworkInterface networkInterface) {
        List<String> addresses = Collections.list(networkInterface.getInetAddresses()).stream()
            .map(InetAddress::getHostAddress)
            .sorted()
            .collect(Collectors.toList());
        try {
            return ClusterNodeInfo.NetworkInterfaceInfo.builder()
                .name(networkInterface.getName())
                .displayName(networkInterface.getDisplayName())
                .up(networkInterface.isUp())
                .loopback(networkInterface.isLoopback())
                .virtual(networkInterface.isVirtual())
                .multicastSupported(networkInterface.supportsMulticast())
                .mtu(networkInterface.getMTU())
                .addresses(addresses)
                .build();
        } catch (SocketException e) {
            log.warn("Failed to collect network interface detail: {}", networkInterface.getName(), e);
            return ClusterNodeInfo.NetworkInterfaceInfo.builder()
                .name(networkInterface.getName())
                .displayName(networkInterface.getDisplayName())
                .addresses(addresses)
                .build();
        }
    }

}
