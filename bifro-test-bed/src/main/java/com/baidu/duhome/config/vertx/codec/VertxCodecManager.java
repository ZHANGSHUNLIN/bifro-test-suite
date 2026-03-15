package com.baidu.duhome.config.vertx.codec;

import com.baidu.duhome.bean.ClusterNodeInfo;
import com.sun.source.util.TaskEvent;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageCodec;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
public class VertxCodecManager {


    // 注册方法
    public static void registerAll(Vertx vertx) {
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
}