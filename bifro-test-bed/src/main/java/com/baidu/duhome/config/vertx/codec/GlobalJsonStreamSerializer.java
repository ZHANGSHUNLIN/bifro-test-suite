package com.baidu.duhome.config.vertx.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 全局 JSON 序列化器，用于序列化所有未注册的类型
 */
@Slf4j
public class GlobalJsonStreamSerializer implements StreamSerializer<Object> {

    public static final int TYPE_ID = 9999;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void write(ObjectDataOutput out, Object obj) throws IOException {
        if (obj == null) {
            out.writeBoolean(false);
            return;
        }
        out.writeBoolean(true);
        out.writeUTF(obj.getClass().getName());
        byte[] data = MAPPER.writeValueAsBytes(obj);
        out.writeInt(data.length);
        out.write(data);
    }

    @Override
    public Object read(ObjectDataInput in) throws IOException {
        boolean isNotNull = in.readBoolean();
        if (!isNotNull) {
            return null;
        }
        String className = in.readUTF();
        int length = in.readInt();
        byte[] data = new byte[length];
        in.readFully(data);
        try {
            Class<?> clazz = Class.forName(className);
            return MAPPER.readValue(data, clazz);
        } catch (ClassNotFoundException e) {
            log.error("Failed to find class: {}", className, e);
            throw new IOException("Failed to deserialize object", e);
        }
    }

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }
}
