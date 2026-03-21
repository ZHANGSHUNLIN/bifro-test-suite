package com.baidu.duhome.config.vertx.codec;

import com.hazelcast.nio.serialization.StreamSerializer;
import io.vertx.core.eventbus.MessageCodec;

public record GenericCodecSupplier<T>(int typeId,Class<T> messageClass) implements CodecSupplier<T> {

    @Override
    public MessageCodec<T, T> get() {
        return new DefaultVertxCodec<>(messageClass, typeId);
    }

    public StreamSerializer<T> getSerializer() {
        return new DefaultVertxCodec<>(messageClass, typeId);
    }
}