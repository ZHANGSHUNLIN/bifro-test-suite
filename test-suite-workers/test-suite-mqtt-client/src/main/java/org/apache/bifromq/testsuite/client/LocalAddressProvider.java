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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalAddressProvider {

    private final List<String> addresses;
    private int index = 0;

    private LocalAddressProvider(List<String> addresses) {
        this.addresses = addresses;
    }


    public static LocalAddressProvider of(List<String> preconfigured) {
        if (preconfigured != null && !preconfigured.isEmpty()) {
            return new LocalAddressProvider(new ArrayList<>(preconfigured));
        }
        return new LocalAddressProvider(discoverLocalAddresses());
    }

    public static LocalAddressProvider primary() {
        List<String> discovered = discoverLocalAddresses();
        if (discovered.isEmpty()) {
            return new LocalAddressProvider(List.of());
        }
        return new LocalAddressProvider(List.of(discovered.get(0)));
    }

    public static LocalAddressProvider disabled() {
        return new LocalAddressProvider(List.of()) {
            @Override
            public String next() {
                return null;
            }
        };
    }

    private static List<String> discoverLocalAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (Enumeration<NetworkInterface> iFaces = NetworkInterface.getNetworkInterfaces();
                 iFaces.hasMoreElements(); ) {
                NetworkInterface iFace = iFaces.nextElement();
                if (!iFace.isUp()) {
                    continue;
                }
                for (Enumeration<InetAddress> addrs = iFace.getInetAddresses();
                     addrs.hasMoreElements(); ) {
                    InetAddress addr = addrs.nextElement();
                    if (addr != null && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        log.info("Discovered local address: {}", addr.getHostAddress());
                        result.add(addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            log.error("Failed to discover local addresses.", e);
        }
        if (result.isEmpty()) {
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                if (localHost != null && !localHost.isLoopbackAddress()) {
                    log.info("Discovered fallback local address: {}", localHost.getHostAddress());
                    result.add(localHost.getHostAddress());
                }
            } catch (Exception e) {
                log.error("Failed to discover fallback local address.", e);
            }
        }
        return result;
    }

    public static List<String> discoverAll() {
        return List.copyOf(discoverLocalAddresses());
    }


    public String next() {
        if (addresses.isEmpty()) {
            return null;
        }
        return addresses.get(index++ % addresses.size());
    }

    public List<String> getAddresses() {
        return List.copyOf(addresses);
    }
}
