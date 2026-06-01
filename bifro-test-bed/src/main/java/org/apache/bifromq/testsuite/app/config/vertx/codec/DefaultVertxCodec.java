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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultVertxCodec<T> implements MessageCodec<T, T> {

    private final Class<T> codecClass;
    private final int typeId;

    public DefaultVertxCodec(Class<T> codecClass, int typeId) {
        this.codecClass = codecClass;
        this.typeId = typeId;
    }

    @Override
    public void encodeToWire(Buffer buffer, T workerTaskEvent) {

        int startPos = buffer.length();
        buffer.appendInt(0);
        byte[] jsonBytes = Json.encodeToBuffer(workerTaskEvent).getBytes();
        buffer.appendBytes(jsonBytes);
        int endPos = buffer.length();
        int msgLength = endPos - startPos - 4;
        buffer.setInt(startPos, msgLength);
    }

    @Override
    public T decodeFromWire(int pos, Buffer buffer) {
        try {

            int msgLength = buffer.getInt(pos);
            pos += 4;
            if (msgLength <= 0 || msgLength > buffer.length() - pos) {
                throw new DecodeException("Invalid message length: " + msgLength);
            }
            Buffer jsonBuffer = buffer.getBuffer(pos, pos + msgLength);
            return Json.decodeValue(jsonBuffer, codecClass);

        } catch (Exception e) {
            log.error("Decode failed. Buffer: {}", buffer, e);
            throw new DecodeException("Failed to decode message", e);
        }
    }

    @Override
    public T transform(T clientTaskEvent) {
        return clientTaskEvent;
    }

    @Override
    public String name() {
        return codecClass.getSimpleName();
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
