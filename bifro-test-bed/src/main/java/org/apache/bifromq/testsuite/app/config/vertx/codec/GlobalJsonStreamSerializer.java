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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

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
