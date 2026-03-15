package com.baidu.duhome.config.vertx.codec;

import io.vertx.core.eventbus.MessageCodec;

public record GenericCodecSupplier<T>(Class<T> messageClass) implements CodecSupplier<T> {

    @Override
    public MessageCodec<T, T> get() {
        return new DefaultVertxCodec<>(messageClass);
    }
}