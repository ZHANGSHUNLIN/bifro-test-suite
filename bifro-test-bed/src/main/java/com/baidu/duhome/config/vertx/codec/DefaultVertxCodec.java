package com.baidu.duhome.config.vertx.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class DefaultVertxCodec<T> implements MessageCodec<T, T>, StreamSerializer<T> {

    private final Class<T> codecClass;
    private final int typeId;

    private static final ObjectMapper MAPPER = new ObjectMapper();


    public DefaultVertxCodec(Class<T> codecClass, int typeId) {
        this.codecClass = codecClass;
        this.typeId = typeId;
    }


    @Override
    public void encodeToWire(Buffer buffer, T workerTaskEvent) {
        // 1. 先预留4字节给消息长度头
        int startPos = buffer.length();
        buffer.appendInt(0); // 占位

        // 2. 写入实际JSON数据
        byte[] jsonBytes = Json.encodeToBuffer(workerTaskEvent).getBytes();
        buffer.appendBytes(jsonBytes);

        // 3. 回填实际消息长度(不包括长度字段自身)
        int endPos = buffer.length();
        int msgLength = endPos - startPos - 4;
        buffer.setInt(startPos, msgLength);
    }

    @Override
    public T decodeFromWire(int pos, Buffer buffer) {
        try {
            // 1. 读取消息长度头
            int msgLength = buffer.getInt(pos);
            pos += 4;

            // 2. 验证长度有效性
            if (msgLength <= 0 || msgLength > buffer.length() - pos) {
                throw new DecodeException("Invalid message length: " + msgLength);
            }

            // 3. 提取JSON部分
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

    @Override
    public int getTypeId() {
        return typeId;
    }

    @Override
    public void write(ObjectDataOutput out, @NonNull T obj) throws IOException {
        byte[] data = MAPPER.writeValueAsBytes(obj);
        out.writeByteArray(data);
    }

    @Override
    public T read(@NonNull ObjectDataInput in) throws IOException {
        byte[] data = in.readByteArray();
        return MAPPER.readValue(data, codecClass);
    }
}
