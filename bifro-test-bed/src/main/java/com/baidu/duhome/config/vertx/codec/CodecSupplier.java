package com.baidu.duhome.config.vertx.codec;

import io.vertx.core.eventbus.MessageCodec;

import java.util.function.Supplier;

public interface CodecSupplier<T> extends Supplier<MessageCodec<T, T>> {
    Class<T> messageClass();
}
