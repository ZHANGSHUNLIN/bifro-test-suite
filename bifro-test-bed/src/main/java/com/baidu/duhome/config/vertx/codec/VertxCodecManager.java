package com.baidu.duhome.config.vertx.codec;

import com.hazelcast.config.Config;
import com.hazelcast.config.GlobalSerializerConfig;
import com.hazelcast.config.SerializationConfig;
import io.vertx.core.Vertx;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VertxCodecManager {


    // 注册方法
    public static void registerCodecAll(Vertx vertx) {
        for (CodecType codecType : CodecType.values()) {
            registerCodec(vertx, codecType);
        }
    }

    // 类型安全的单个注册
    public static <T> void registerCodec(Vertx vertx, Class<T> clazz) {
        CodecType codecType = findByClass(clazz);
        if (codecType != null) {
            registerCodec(vertx, codecType);
        }
    }

    // 使用枚举注册
    public static <T> void registerCodec(Vertx vertx, CodecType codecType) {
        try {
            vertx.eventBus().registerDefaultCodec(
                    codecType.getMessageClass(),
                    codecType.getCodec()
            );
            log.info("Registered codec for: {}", codecType.getMessageClass().getSimpleName());
        } catch (Exception e) {
            log.error("Failed to register codec for: {}",
                    codecType.getMessageClass().getSimpleName(), e);
        }
    }

    // 查找枚举
    public static CodecType findByClass(Class<?> clazz) {
        for (CodecType codecType : CodecType.values()) {
            if (codecType.getMessageClass().equals(clazz)) {
                return codecType;
            }
        }
        return null;
    }

    public static void registerStreamSerializerAll(@NonNull Config hazelcastConfig){
        SerializationConfig serializationConfig = hazelcastConfig.getSerializationConfig();

        // 设置全局 JSON 序列化器，禁用 Java 序列化
        GlobalSerializerConfig globalSerializerConfig = new GlobalSerializerConfig()
                .setImplementation(new GlobalJsonStreamSerializer());
        serializationConfig.setGlobalSerializerConfig(globalSerializerConfig);
        log.info("Registered Global JSON StreamSerializer, type ID: {}", GlobalJsonStreamSerializer.TYPE_ID);
    }

}