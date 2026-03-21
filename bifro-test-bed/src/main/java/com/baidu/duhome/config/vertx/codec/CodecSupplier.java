package com.baidu.duhome.config.vertx.codec;

import com.hazelcast.nio.serialization.StreamSerializer;
import io.vertx.core.eventbus.MessageCodec;

import java.util.function.Supplier;

public interface CodecSupplier<T> extends Supplier<MessageCodec<T, T>> {
    Class<T> messageClass();

    StreamSerializer<T> getSerializer();
}
