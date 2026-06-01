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

package org.apache.bifromq.testsuite.chaos;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VertxRawMqttConnection implements RawMqttConnection {

    private final Vertx vertx;
    private final NetClient netClient;
    private final MqttFrameParser parser = new MqttFrameParser();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private NetSocket socket;
    private Consumer<MqttFrameParser.Frame> frameHandler;
    private Consumer<Throwable> closeHandler;

    private CompletableFuture<Integer> connackFuture;

    public VertxRawMqttConnection(Vertx vertx) {
        this.vertx = vertx;
        this.netClient = vertx.createNetClient(
            new NetClientOptions()
                .setConnectTimeout(10_000)
                .setIdleTimeout(0));
    }

    @Override
    public void onFrame(Consumer<MqttFrameParser.Frame> handler) {
        this.frameHandler = handler;
    }

    @Override
    public void onClose(Consumer<Throwable> handler) {
        this.closeHandler = handler;
    }

    @Override
    public CompletableFuture<Integer> connect(String host, int port, String clientId, int keepAlive) {
        connackFuture = new CompletableFuture<>();

        netClient.connect(port, host)
            .onSuccess(sock -> {
                socket = sock;

                socket.handler(buf -> handleIncomingBytes(buf.getBytes()));

                socket.closeHandler(v -> {
                    closed.set(true);

                    if (!connackFuture.isDone()) {
                        connackFuture.completeExceptionally(
                            new IllegalStateException("TCP closed before CONNACK received"));
                    }
                    if (closeHandler != null) {
                        closeHandler.accept(null);
                    }
                });
                socket.exceptionHandler(err -> {
                    log.warn("[RawMqtt] socket exception: {}", err.getMessage());
                    if (!connackFuture.isDone()) {
                        connackFuture.completeExceptionally(err);
                    }
                    if (closeHandler != null) {
                        closeHandler.accept(err);
                    }
                });

                byte[] connectFrame = MqttFrameEncoder.connect(clientId, keepAlive, true);
                socket.write(Buffer.buffer(connectFrame))
                    .onFailure(err -> connackFuture.completeExceptionally(err));

            })
            .onFailure(err -> connackFuture.completeExceptionally(err));

        return connackFuture;
    }

    @Override
    public CompletableFuture<Void> sendRaw(byte[] bytes) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (socket == null || closed.get()) {
            future.completeExceptionally(new IllegalStateException("Not connected"));
            return future;
        }
        socket.write(Buffer.buffer(bytes))
            .onSuccess(v -> future.complete(null))
            .onFailure(future::completeExceptionally);
        return future;
    }

    @Override
    public CompletableFuture<Void> close() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (closed.getAndSet(true)) {
            future.complete(null);
            return future;
        }
        if (socket == null) {
            future.complete(null);
            return future;
        }

        byte[] disconnectFrame = MqttFrameEncoder.disconnect();
        socket.write(Buffer.buffer(disconnectFrame))
            .compose(v -> socket.close())
            .onSuccess(v -> future.complete(null))
            .onFailure(future::completeExceptionally);
        return future;
    }

    @Override
    public void forceClose() {
        closed.set(true);
        if (socket != null) {
            socket.close();
        }
    }

    private void handleIncomingBytes(byte[] bytes) {
        parser.feed(bytes, 0, bytes.length);
        MqttFrameParser.Frame frame;
        while ((frame = parser.poll()) != null) {
            dispatchFrame(frame);
        }
    }

    private void dispatchFrame(MqttFrameParser.Frame frame) {

        if (frame.type == MqttFrameParser.TYPE_CONNACK && connackFuture != null
            && !connackFuture.isDone()) {
            int returnCode = frame.connackReturnCode();
            connackFuture.complete(returnCode);
        }

        if (frameHandler != null) {
            try {
                frameHandler.accept(frame);
            } catch (Exception e) {
                log.warn("[RawMqtt] frameHandler threw: {}", e.getMessage(), e);
            }
        }
    }
}
