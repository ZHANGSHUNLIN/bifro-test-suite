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

package org.apache.bifromq.testsuite.app.config.vertx.codec;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class EventBusCodecRegistry {

    private EventBusCodecRegistry() {
    }

    public static void registerAll(Vertx vertx) {
        for (CodecType codecType : CodecType.values()) {
            register(vertx, codecType);
        }
    }

    public static <T> void register(Vertx vertx, Class<T> clazz) {
        CodecType codecType = findByClass(clazz);
        if (codecType != null) {
            register(vertx, codecType);
        }
    }

    public static void register(Vertx vertx, CodecType codecType) {
        try {
            vertx.eventBus().registerDefaultCodec(
                codecType.getMessageClass(),
                codecType.getCodec()
            );
            log.info("Registered EventBus codec for: {}", codecType.getMessageClass().getSimpleName());
        } catch (Exception e) {
            log.error("Failed to register EventBus codec for: {}",
                codecType.getMessageClass().getSimpleName(), e);
        }
    }

    public static CodecType findByClass(Class<?> clazz) {
        for (CodecType codecType : CodecType.values()) {
            if (codecType.getMessageClass().equals(clazz)) {
                return codecType;
            }
        }
        return null;
    }
}
