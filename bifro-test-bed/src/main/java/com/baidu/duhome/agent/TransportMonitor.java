package com.baidu.duhome.agent;

import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bind.annotation.*;

import io.vertx.core.net.ClientOptionsBase;
import io.netty.bootstrap.Bootstrap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@Slf4j
public class TransportMonitor {

    static Map<String, Queue<Integer>> localPortScanner = new LocalPortScanner().getAvailablePorts();


    @RuntimeType
    public static void intercept(
            @Argument(0) ClientOptionsBase options,
            @Argument(1) boolean domainSocket,
            @Argument(2) Bootstrap bootstrap) throws Exception {
        long start = System.currentTimeMillis();

        if (!domainSocket) {
            bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());
            bootstrap.option(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
            bootstrap.option(ChannelOption.SO_KEEPALIVE, options.isTcpKeepAlive());
        }
        if (options.getLocalAddress() != null) {
            Queue<Integer> queue = localPortScanner.get(options.getLocalAddress());
            Integer port = queue.poll();
            if (port == null) {
                port = 0;
            }
            log.trace("use ip:port,{}:{}", options.getLocalAddress(), port);
            bootstrap.localAddress(options.getLocalAddress(), port);
        }
        if (options.getSendBufferSize() != -1) {
            bootstrap.option(ChannelOption.SO_SNDBUF, options.getSendBufferSize());
        }
        if (options.getReceiveBufferSize() != -1) {
            bootstrap.option(ChannelOption.SO_RCVBUF, options.getReceiveBufferSize());
            bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(options.getReceiveBufferSize()));
        }
        if (options.getSoLinger() != -1) {
            bootstrap.option(ChannelOption.SO_LINGER, options.getSoLinger());
        }
        if (options.getTrafficClass() != -1) {
            bootstrap.option(ChannelOption.IP_TOS, options.getTrafficClass());
        }
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.getConnectTimeout());

        long duration = System.currentTimeMillis() - start;
        log.trace("⏱️ 配置耗时: {}ms", duration);
    }

}