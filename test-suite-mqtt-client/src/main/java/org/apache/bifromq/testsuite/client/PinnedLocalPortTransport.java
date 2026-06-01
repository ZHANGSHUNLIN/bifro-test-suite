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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.ServerSocketChannel;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.transport.Transport;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PinnedLocalPortTransport implements io.vertx.core.transport.Transport {

    private static final ConcurrentHashMap<Long, Integer> PENDING_PORTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Queue<Integer>> PENDING_PORTS_BY_ADDRESS = new ConcurrentHashMap<>();

    private final io.vertx.core.transport.Transport delegate;

    
    public PinnedLocalPortTransport() {
        this.delegate = io.vertx.core.transport.Transport.NIO;
    }

    
    public PinnedLocalPortTransport(io.vertx.core.transport.Transport delegate) {
        this.delegate = delegate;
    }

    
    
    

    
    public static void pinLocalPort(int port) {
        if (port != 0) {
            PENDING_PORTS.put(Thread.currentThread().getId(), port);
        }
    }

    public static void pinLocalPort(String localAddress, int port) {
        if (localAddress != null && !localAddress.isEmpty() && port != 0) {
            PENDING_PORTS_BY_ADDRESS.computeIfAbsent(localAddress, key -> new ConcurrentLinkedQueue<>()).add(port);
            return;
        }
        pinLocalPort(port);
    }

    
    public static void clearPin() {
        PENDING_PORTS.remove(Thread.currentThread().getId());
    }

    static Integer pollPinnedPort(String localAddress) {
        Queue<Integer> queue = PENDING_PORTS_BY_ADDRESS.get(localAddress);
        if (queue != null) {
            Integer port = queue.poll();
            if (queue.isEmpty()) {
                PENDING_PORTS_BY_ADDRESS.remove(localAddress, queue);
            }
            if (port != null) {
                return port;
            }
        }
        return PENDING_PORTS.remove(Thread.currentThread().getId());
    }

    
    
    

    @Override
    public String name() {
        return "pinned-local-port+" + delegate.name();
    }

    @Override
    public boolean available() {
        return delegate.available();
    }

    @Override
    public Throwable unavailabilityCause() {
        return delegate.unavailabilityCause();
    }

    @Override
    public Transport implementation() {
        
        return new TransportSpiWrapper(delegate.implementation());
    }

    
    
    

    private record TransportSpiWrapper(Transport spi) implements Transport {

        @Override
        public IoHandlerFactory ioHandlerFactory() {
            return spi.ioHandlerFactory();
        }

        @Override
        public DatagramChannel datagramChannel() {
            return spi.datagramChannel();
        }

        @Override
        public DatagramChannel datagramChannel(InternetProtocolFamily family) {
            return spi.datagramChannel(family);
        }

        @Override
        public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
            return spi.channelFactory(domainSocket);
        }

        @Override
        @SuppressWarnings("unchecked")
        public ChannelFactory<? extends ServerSocketChannel> serverChannelFactory(boolean domainSocket) {
            return (ChannelFactory<? extends ServerSocketChannel>) spi.serverChannelFactory(domainSocket);
        }

        
        @Override
        public void configure(ClientOptionsBase options, int connectTimeout, boolean domainSocket,
                              Bootstrap bootstrap) {
            
            spi.configure(options, connectTimeout, domainSocket, bootstrap);

            
            String localAddress = options.getLocalAddress();
            if (!domainSocket && localAddress != null) {
                Integer pinnedPort = PinnedLocalPortTransport.pollPinnedPort(localAddress);
                int port = (pinnedPort != null) ? pinnedPort : 0;
                if (port != 0) {
                    bootstrap.localAddress(new InetSocketAddress(localAddress, port));
                    log.debug("Pinned local address {}:{}", localAddress, port);
                }
            }
        }

        
        @Override
        public boolean supportsDomainSockets() {
            return spi.supportsDomainSockets();
        }

        @Override
        public boolean isAvailable() {
            return spi.isAvailable();
        }

        @Override
        public java.net.SocketAddress convert(SocketAddress address) {
            return spi.convert(address);
        }

        @Override
        public SocketAddress convert(java.net.SocketAddress address) {
            return spi.convert(address);
        }
    }
}
